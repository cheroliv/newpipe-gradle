package com.cheroliv.newpipe

import com.cheroliv.newpipe.NewpipeManager.NEWPIPE_GROUP
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.util.Collections
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Gradle task that downloads every tune and playlist listed in the YAML config
 * and stores each MP3 under downloads/<ArtistName>/.
 *
 * Downloads are concurrent (up to [MAX_CONCURRENT_DOWNLOADS] at a time).
 * Conversions are sequential — FFmpeg is fast enough that parallelising it
 * would only cause CPU contention.
 *
 * For tunes    : the artist folder name comes from the YAML config.
 * For playlists: the artist folder name comes from the YouTube video tag (uploader).
 *
 * In test mode ([MOCK_PROPERTY] = "true") real network calls and FFmpeg are
 * replaced by [FakeVideoInfoProvider] and [FakeAudioConverter].
 *
 * CLI override (single video):
 *   gradle download --url=<youtube-url> [--output=<output-dir>]
 */
open class DownloadMusicTask : DefaultTask() {

    private val logger = LoggerFactory.getLogger(DownloadMusicTask::class.java)

    companion object {
        const val MOCK_PROPERTY = "newpipe.test.mock"
        private val isMockMode get() = System.getProperty(MOCK_PROPERTY) == "true"

        private const val MAX_CONCURRENT_DOWNLOADS = 3
    }

    // ------------------------------------------------------------------
    // Properties injected by NewpipeManager
    // ------------------------------------------------------------------

    @get:Input
    var tuneEntries: List<Pair<String, String>> = emptyList()

    @get:Input
    var playlistUrls: List<String> = emptyList()

    @get:Input
    var ffmpegDockerImage: String = NewpipeExtension.DEFAULT_FFMPEG_IMAGE

    @get:Input
    var forceDocker: Boolean = false

    // ------------------------------------------------------------------
    // Optional CLI overrides
    // ------------------------------------------------------------------

    @get:Input
    @get:Optional
    @set:Option(option = "url", description = "Single YouTube URL to download (overrides the YAML config)")
    var url: String = ""

    @get:Input
    @get:Optional
    @set:Option(option = "output", description = "Root destination folder (default: ./downloads)")
    var outputPath: String = ""

    init {
        group = NEWPIPE_GROUP
        description = "Downloads all tunes and playlists from the YAML config and converts them to MP3"
    }

    // ------------------------------------------------------------------
    // Dependency factories — swapped out in test mode
    // ------------------------------------------------------------------

    private fun newInfoProvider(): VideoInfoProvider =
        if (isMockMode) FakeVideoInfoProvider() else YouTubeDownloader()

    private fun newAudioConverter(): AudioConverter =
        if (isMockMode) FakeAudioConverter()
        else Mp3Converter(dockerImage = ffmpegDockerImage, forceDocker = forceDocker)

    // ------------------------------------------------------------------
    // Data classes for the two-phase pipeline
    // ------------------------------------------------------------------

    /**
     * Holds the raw audio temp file ready for conversion.
     *
     * @param previousMp3Backup  When this is a quality upgrade re-download, the
     *                           original MP3 has been renamed to this `.old` file
     *                           so it stays out of the way during conversion.
     *                           It is deleted after a successful conversion, or
     *                           restored if conversion fails.
     *                           Null for first-time downloads.
     */
    private data class DownloadedTrack(
        val tempFile: File,
        val mp3File: File,
        val metadata: VideoMetadata,
        val artistName: String,
        val label: String,
        val selectedBitrateKbps: Int,
        val previousMp3Backup: File? = null
    )

    // ------------------------------------------------------------------
    // Task action
    // ------------------------------------------------------------------

    @TaskAction
    fun download() {
        if (isMockMode) logger.info("*** Running in mock mode — no network calls will be made ***")

        val baseOutputDir = if (outputPath.isNotBlank()) File(outputPath)
        else File(project.projectDir, "downloads")

        if (url.isNotBlank()) {
            runBlocking { downloadAndConvert(url, artistHint = null, baseOutputDir, label = url) }
            return
        }

        val hasWork = tuneEntries.isNotEmpty() || playlistUrls.isNotEmpty()
        if (!hasWork) throw GradleException("No tunes or playlists to download. Check your YAML config.")

        val allEntries: List<Pair<String?, String>> = buildList {
            tuneEntries.forEach { (artist, tuneUrl) -> add(artist to tuneUrl) }

            if (playlistUrls.isNotEmpty()) {
                val infoProvider = newInfoProvider()
                playlistUrls.forEachIndexed { pIndex, playlistUrl ->
                    logger.info("Fetching playlist [${pIndex + 1}/${playlistUrls.size}]: $playlistUrl")
                    runCatching {
                        runBlocking { infoProvider.getPlaylistVideoUrls(playlistUrl) }
                    }.onSuccess { urls ->
                        logger.info("  → ${urls.size} video(s) found")
                        urls.forEach { videoUrl -> add(null to videoUrl) }
                    }.onFailure { e ->
                        when {
                            e.isNotFound()    -> logger.warn("⏭ Playlist not found, skipping: $playlistUrl")
                            e.isUnavailable() -> logger.warn("⏭ Playlist unavailable, skipping: $playlistUrl — ${e.message}")
                            else              -> logger.error("Failed to fetch playlist $playlistUrl: ${e.message}")
                        }
                    }
                }
            }
        }

        logger.info("\n" + "=".repeat(60))
        logger.info("${allEntries.size} track(s) to download (max $MAX_CONCURRENT_DOWNLOADS concurrent)")
        logger.info("=".repeat(60))

        val errors = Collections.synchronizedList(mutableListOf<String>())

        runBlocking {
            val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
            val converter = newAudioConverter()

            allEntries
                .mapIndexed { index, (artistHint, tuneUrl) ->
                    val label = "[${index + 1}/${allEntries.size}] ${artistHint ?: tuneUrl}"
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            logger.info("\n⬇ Downloading $label")
                            runCatching {
                                val track = fetchAudio(tuneUrl, artistHint, baseOutputDir, label)
                                logger.info("\n🎵 Converting: ${track.label}")
                                convertTrack(converter, track)
                            }.onFailure { e ->
                                when {
                                    e is AlreadyDownloadedException -> { /* already logged in fetchAudio */ }
                                    e.isNotFound()    -> logger.warn("⏭ Not found, skipping: $tuneUrl")
                                    e.isUnavailable() -> logger.warn("⏭ Unavailable, skipping: $tuneUrl — ${e.message}")
                                    else -> {
                                        val msg = "Failed [$label] $tuneUrl: ${e.message}"
                                        logger.error(msg)
                                        errors += msg
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        logger.info("\n" + "=".repeat(60))
        if (errors.isEmpty()) {
            logger.info("✓ All downloads completed successfully.")
        } else {
            logger.warn("Completed with ${errors.size} error(s):")
            errors.forEach { logger.warn("  • $it") }
        }
        logger.info("=".repeat(60))

        if (errors.isNotEmpty()) {
            throw GradleException("${errors.size} track(s) could not be downloaded. See log above.")
        }
    }

    // ------------------------------------------------------------------
    // Phase 1 — fetch audio only (no FFmpeg)
    // ------------------------------------------------------------------

    private suspend fun fetchAudio(
        tuneUrl: String,
        artistHint: String?,
        baseOutputDir: File,
        label: String
    ): DownloadedTrack {
        val infoProvider = newInfoProvider()
        val converter    = newAudioConverter()

        val metadata = infoProvider.getVideoInfo(tuneUrl)
        logger.info("[$label] ✓ Title: ${metadata.title} (${formatDuration(metadata.duration)})")

        val artistName = (artistHint ?: metadata.uploaderName).removeSuffix(" - Topic").trim()

        val sanitizedArtist = infoProvider.sanitizeFileName(artistName)
        val sanitizedTitle  = infoProvider.sanitizeFileName(metadata.title).ifBlank {
            metadata.title.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        }

        val artistDir = File(baseOutputDir, sanitizedArtist).also { it.mkdirs() }
        val mp3File   = File(artistDir, "$sanitizedArtist - $sanitizedTitle.mp3")

        val bestAvailableBitrate = infoProvider.getBestAvailableBitrateKbps(metadata)

        if (converter.alreadyDownloaded(
                mp3File, metadata.title, artistName, metadata.duration,
                bestAvailableBitrateKbps = bestAvailableBitrate
            )
        ) {
            throw AlreadyDownloadedException()
        }

        val tempFile = File(artistDir, "$sanitizedArtist - ${sanitizedTitle}_temp.m4a")

        if (tempFile.exists()) {
            tempFile.delete()
            logger.warn("[$label] Deleted incomplete temp file: ${tempFile.name}")
        }

        // Quality upgrade path — rename the existing MP3 to a .old backup so it
        // stays on disk while the new file downloads and converts.
        // The backup is deleted after successful conversion, or restored on failure.
        // First-time downloads simply delete any corrupt/incomplete leftover.
        val previousMp3Backup: File? = if (mp3File.exists()) {
            val backup = File(mp3File.parentFile, "${mp3File.nameWithoutExtension}.old")
            mp3File.renameTo(backup)
            logger.info("[$label] Backed up lower-quality MP3: ${backup.name}")
            backup
        } else null

        var lastPercent = 0
        infoProvider.downloadBestAudio(metadata, tempFile) { downloaded, total, percent ->
            if (percent >= lastPercent + 10) {
                logger.info("  [$label] $percent%% (%.1f / %.1f MB)".format(
                    downloaded / 1024.0 / 1024.0,
                    total / 1024.0 / 1024.0
                ))
                lastPercent = percent
            }
        }
        logger.info("[$label] ✓ Download complete: ${tempFile.length() / 1024 / 1024} MB")

        return DownloadedTrack(tempFile, mp3File, metadata, artistName, label, bestAvailableBitrate, previousMp3Backup)
    }

    /**
     * Converts a downloaded temp file to MP3 at the bitrate of the selected stream,
     * then writes ID3 tags.
     *
     * On success: deletes the [DownloadedTrack.previousMp3Backup] if present.
     * On failure: restores the backup so the lower-quality file is not lost.
     */
    private suspend fun convertTrack(converter: AudioConverter, track: DownloadedTrack) {
        val ffmpegBitrate = if (track.selectedBitrateKbps > 0) "${track.selectedBitrateKbps}k" else "192k"
        try {
            converter.convertToMp3(track.tempFile, track.mp3File, bitrate = ffmpegBitrate)
            converter.addMetadata(
                mp3File      = track.mp3File,
                title        = track.metadata.title,
                artist       = track.artistName,
                album        = "YouTube",
                thumbnailUrl = track.metadata.url
            )
            // Conversion succeeded — safe to delete the lower-quality backup
            track.previousMp3Backup?.let { backup ->
                if (backup.exists()) {
                    backup.delete()
                    logger.info("🗑 Deleted lower-quality backup: ${backup.name}")
                }
            }
            logger.info("✓ Saved: ${track.mp3File.absolutePath}")
        } catch (e: Exception) {
            // Conversion failed — restore the backup so we don't lose the track entirely
            track.previousMp3Backup?.let { backup ->
                if (backup.exists()) {
                    backup.renameTo(track.mp3File)
                    logger.warn("↩ Restored lower-quality backup after failed conversion: ${track.mp3File.name}")
                }
            }
            throw e
        }
    }

    private class AlreadyDownloadedException : Exception()

    // ------------------------------------------------------------------
    // CLI single-track helper
    // ------------------------------------------------------------------

    private suspend fun downloadAndConvert(
        tuneUrl: String,
        artistHint: String?,
        baseOutputDir: File,
        label: String
    ) {
        val converter = newAudioConverter()
        val track = runCatching {
            fetchAudio(tuneUrl, artistHint, baseOutputDir, label)
        }.getOrElse { e ->
            if (e is AlreadyDownloadedException) return
            if (e.isNotFound() || e.isUnavailable()) {
                logger.warn("⏭ Skipping $tuneUrl — ${e.message}")
                return
            }
            throw e
        }
        convertTrack(converter, track)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val secs    = seconds % 60
        return "%02d:%02d".format(minutes, secs)
    }
}

// ------------------------------------------------------------------
// Extension helpers
// ------------------------------------------------------------------

private fun Throwable.isNotFound(): Boolean {
    val msg = message?.lowercase() ?: return false
    return msg.contains("404")
            || msg.contains("not found")
            || msg.contains("does not exist")
            || msg.contains("video unavailable")
            || msg.contains("private video")
            || msg.contains("this video has been removed")
            || cause?.isNotFound() == true
}

private fun Throwable.isUnavailable(): Boolean {
    val msg = message?.lowercase() ?: return false
    return msg.contains("no audio streams available")
            || msg.contains("no audio stream")
            || msg.contains("audio streams")
            || msg.contains("no video streams")
            || msg.contains("no streams")
            || msg.contains("geo")
            || msg.contains("not available in your country")
            || msg.contains("drm")
            || msg.contains("age")
            || msg.contains("sign in")
            || cause?.isUnavailable() == true
}
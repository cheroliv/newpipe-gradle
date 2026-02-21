package com.cheroliv.newpipe

import com.cheroliv.newpipe.NewpipeManager.NEWPIPE_GROUP
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Gradle task that downloads every tune and playlist listed in the YAML config
 * and stores each MP3 under downloads/<ArtistName>/.
 *
 * For tunes  : the artist folder name comes from the YAML config.
 * For playlists: the artist folder name comes from the YouTube video tag (uploader).
 *
 * CLI override (single video):
 *   gradle download --url=<youtube-url> [--output=<output-dir>]
 */
open class DownloadMusicTask : DefaultTask() {

    private val logger = LoggerFactory.getLogger(DownloadMusicTask::class.java)

    // ------------------------------------------------------------------
    // Properties injected by NewpipeManager
    // ------------------------------------------------------------------

    /** (artistHint, videoUrl) pairs from the tunes section of the YAML config. */
    @get:Input
    var tuneEntries: List<Pair<String, String>> = emptyList()

    /** Playlist URLs from the playlists section of the YAML config. */
    @get:Input
    var playlistUrls: List<String> = emptyList()

    // ------------------------------------------------------------------
    // Optional CLI overrides
    // ------------------------------------------------------------------

    @get:Input
    @get:Optional
    @set:Option(
        option = "url",
        description = "Single YouTube URL to download (overrides the YAML config)"
    )
    var url: String = ""

    @get:Input
    @get:Optional
    @set:Option(
        option = "output",
        description = "Root destination folder (default: ./downloads)"
    )
    var outputPath: String = ""

    init {
        group = NEWPIPE_GROUP
        description = "Downloads all tunes and playlists from the YAML config and converts them to MP3"
    }

    // ------------------------------------------------------------------
    // Task action
    // ------------------------------------------------------------------

    @TaskAction
    fun download() {
        val baseOutputDir = if (outputPath.isNotBlank()) File(outputPath)
        else File(project.projectDir, "downloads")

        // CLI --url flag: download a single track
        if (url.isNotBlank()) {
            downloadSingle(url, artistHint = null, baseOutputDir)
            return
        }

        val hasWork = tuneEntries.isNotEmpty() || playlistUrls.isNotEmpty()
        if (!hasWork) throw GradleException("No tunes or playlists to download. Check your YAML config.")

        val errors = mutableListOf<String>()

        // ── 1. Individual tunes ────────────────────────────────────────
        if (tuneEntries.isNotEmpty()) {
            logger.info("=".repeat(60))
            logger.info("Tunes — ${tuneEntries.size} track(s)")
            logger.info("=".repeat(60))

            tuneEntries.forEachIndexed { index, (artistName, tuneUrl) ->
                logger.info("\n[${index + 1}/${tuneEntries.size}] $artistName — $tuneUrl")
                runCatching { downloadSingle(tuneUrl, artistHint = artistName, baseOutputDir) }
                    .onFailure { e ->
                        val msg = "Failed (tune) [$artistName] $tuneUrl: ${e.message}"
                        logger.error(msg)
                        errors += msg
                    }
            }
        }

        // ── 2. Playlists ───────────────────────────────────────────────
        if (playlistUrls.isNotEmpty()) {
            logger.info("\n" + "=".repeat(60))
            logger.info("Playlists — ${playlistUrls.size} playlist(s)")
            logger.info("=".repeat(60))

            val downloader = YouTubeDownloader()

            playlistUrls.forEachIndexed { pIndex, playlistUrl ->
                logger.info("\nPlaylist [${pIndex + 1}/${playlistUrls.size}]: $playlistUrl")

                val videoUrls: List<String> = runCatching {
                    runBlocking { downloader.getPlaylistVideoUrls(playlistUrl) }
                }.getOrElse { e ->
                    val msg = "Failed to fetch playlist $playlistUrl: ${e.message}"
                    logger.error(msg)
                    errors += msg
                    return@forEachIndexed
                }

                logger.info("  → ${videoUrls.size} video(s) found")

                videoUrls.forEachIndexed { vIndex, videoUrl ->
                    logger.info("\n  [${vIndex + 1}/${videoUrls.size}] $videoUrl")
                    // No artistHint: folder name comes from the YouTube uploader tag
                    runCatching { downloadSingle(videoUrl, artistHint = null, baseOutputDir) }
                        .onFailure { e ->
                            val msg = "Failed (playlist video) $videoUrl: ${e.message}"
                            logger.error(msg)
                            errors += msg
                        }
                }
            }
        }

        // ── Summary ────────────────────────────────────────────────────
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
    // Single-track download
    // ------------------------------------------------------------------

    /**
     * Downloads one YouTube video URL and saves it as an MP3.
     *
     * @param tuneUrl     YouTube video URL.
     * @param artistHint  Artist name from the YAML config (tunes only).
     *                    When null the YouTube uploader name is used (playlist videos).
     * @param baseOutputDir Root downloads directory.
     */
    private fun downloadSingle(tuneUrl: String, artistHint: String?, baseOutputDir: File) {
        val downloader = YouTubeDownloader()
        val converter = Mp3Converter()

        runBlocking {
            // Step 1: Extract video information
            logger.info("[1/4] Extracting video information...")
            val videoInfo = downloader.getVideoInfo(tuneUrl)

            val title        = videoInfo.name
            val uploaderName = videoInfo.uploaderName
            val duration     = videoInfo.duration

            logger.info("✓ Title: $title")
            logger.info("✓ Uploader: $uploaderName")
            logger.info("✓ Duration: ${formatDuration(duration)}")

            // Resolve artist name:
            //   - tunes         → YAML name (artistHint), strip " - Topic" just in case
            //   - playlist videos → YouTube uploader tag, strip " - Topic"
            val artistName = (artistHint ?: uploaderName).removeSuffix(" - Topic").trim()
            logger.info("✓ Artist folder: $artistName")

            val artistDir = File(baseOutputDir, downloader.sanitizeFileName(artistName))
                .also { it.mkdirs() }

            val sanitizedArtist = downloader.sanitizeFileName(artistName)
            val sanitizedTitle  = downloader.sanitizeFileName(title)
            val mp3File = File(artistDir, "$sanitizedArtist - $sanitizedTitle.mp3")

            // ── Duplicate check ────────────────────────────────────────
            // Compare existing file tags (title, artist) and audio duration
            // against YouTube metadata before touching the network any further.
            if (converter.alreadyDownloaded(mp3File, title, artistName, duration)) {
                return@runBlocking
            }

            // Step 2: Select best audio stream
            logger.info("[2/4] Selecting best audio stream...")
            val audioStream = downloader.getBestAudioStream(videoInfo)
            logger.info("✓ Format: ${audioStream.format?.name ?: "unknown"}, ${audioStream.averageBitrate} kbps")

            // Step 3: Download raw audio
            logger.info("[3/4] Downloading audio...")
            val tempFile = File(artistDir, "$sanitizedArtist - ${sanitizedTitle}_temp.${audioStream.format?.suffix ?: "m4a"}")

            var lastPercent = 0
            downloader.downloadAudio(audioStream, tempFile) { downloaded, total, percent ->
                if (percent >= lastPercent + 5) {
                    logger.info("  Progress: $percent%% (%.2f / %.2f MB)".format(
                        downloaded / 1024.0 / 1024.0,
                        total / 1024.0 / 1024.0
                    ))
                    lastPercent = percent
                }
            }
            logger.info("✓ Download complete: ${tempFile.length() / 1024 / 1024} MB")

            // Step 4: Convert to MP3 and embed ID3 tags
            logger.info("[4/4] Converting to MP3 and writing metadata...")
            converter.convertToMp3(tempFile, mp3File, bitrate = "192k")
            converter.addMetadata(
                mp3File = mp3File,
                title = title,
                artist = artistName,
                album = "YouTube",
                thumbnailUrl = videoInfo.url
            )

            logger.info("✓ Saved: ${mp3File.absolutePath}")
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(minutes, secs)
    }
}
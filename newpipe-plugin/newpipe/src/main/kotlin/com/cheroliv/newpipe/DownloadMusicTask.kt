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
 * Typed Gradle task to download music from YouTube.
 * Usage: gradle download --url=<youtube-url> [--output=<output-dir>]
 */
open class DownloadMusicTask : DefaultTask() {

    private val logger = LoggerFactory.getLogger(DownloadMusicTask::class.java)

    @get:Input
    @set:Option(
        option = "url",
        description = "URL of the YouTube video to download"
    )
    var url: String = ""

    @get:Input
    @get:Optional
    @set:Option(
        option = "output",
        description = "Destination folder for the MP3 file (default: ./downloads)"
    )
    var outputPath: String = ""

    init {
        group = NEWPIPE_GROUP
        description = "Downloads audio from a YouTube video and converts it to MP3"
    }

    @TaskAction
    fun download() {
        // Validate URL
        if (url.isBlank()) {
            throw GradleException("YouTube URL is required. Use: --url=<youtube-url>")
        }

        // Base output directory — the artist sub-folder will be created after fetching video info
        val baseOutputDir = if (outputPath.isNotBlank()) File(outputPath)
        else File(project.projectDir, "downloads")

        printHeader(url, baseOutputDir)

        val downloader = YouTubeDownloader()
        val converter = Mp3Converter()

        runBlocking {
            try {
                // Step 1: Extract video information
                logger.info("\n[1/4] Extracting video information...")
                val videoInfo = downloader.getVideoInfo(url)

                val title = videoInfo.name
                val artist = videoInfo.uploaderName
                val duration = videoInfo.duration

                logger.info("✓ Title: $title")
                logger.info("✓ Artist: $artist")
                logger.info("✓ Duration: ${formatDuration(duration)}")

                // Create artist sub-folder (downloads/<ArtistName>/)
                // YouTube automatically appends " - Topic" to auto-generated music channels
                val cleanArtist = artist.removeSuffix(" - Topic").trim()
                val sanitizedArtist = downloader.sanitizeFileName(cleanArtist)
                val artistDir = File(baseOutputDir, sanitizedArtist)
                if (!artistDir.exists()) {
                    artistDir.mkdirs()
                    logger.info("Artist directory created: ${artistDir.absolutePath}")
                }

                // Step 2: Select best audio stream
                logger.info("\n[2/4] Selecting best audio stream...")
                val audioStream = downloader.getBestAudioStream(videoInfo)
                logger.info("✓ Format: ${audioStream.format?.name ?: "unknown"}")
                logger.info("✓ Bitrate: ${audioStream.averageBitrate} kbps")

                // Step 3: Download audio
                logger.info("\n[3/4] Downloading audio...")

                val sanitizedTitle = downloader.sanitizeFileName(title)
                val tempFile = File(artistDir, "${sanitizedTitle}_temp.${audioStream.format?.suffix ?: "m4a"}")

                var lastPrintedPercent = 0
                downloader.downloadAudio(audioStream, tempFile) { downloaded, total, percent ->
                    if (percent >= lastPrintedPercent + 5) {
                        val downloadedMB = downloaded / 1024.0 / 1024.0
                        val totalMB = total / 1024.0 / 1024.0
                        logger.info("  Progress: $percent%% (%.2f MB / %.2f MB)".format(downloadedMB, totalMB))
                        lastPrintedPercent = percent
                    }
                }
                logger.info("✓ Download complete: ${tempFile.length() / 1024 / 1024} MB")

                // Step 4: Convert to MP3 and add metadata
                logger.info("\n[4/4] Converting to MP3 and adding metadata...")

                val mp3File = File(artistDir, "${sanitizedTitle}.mp3")
                converter.convertToMp3(tempFile, mp3File, bitrate = "192k")

                // Add ID3 metadata tags
                val thumbnailUrl = videoInfo.url
                converter.addMetadata(
                    mp3File = mp3File,
                    title = title,
                    artist = cleanArtist,
                    album = "YouTube",
                    thumbnailUrl = thumbnailUrl
                )

                logger.info("✓ Conversion complete")
                printSuccess(mp3File)

            } catch (e: Exception) {
                printError(e)
                throw GradleException("Download failed: ${e.message}", e)
            }
        }
    }

    private fun printHeader(url: String, outputDir: File) {
        logger.info("=".repeat(60))
        logger.info("YouTube MP3 Downloader")
        logger.info("=".repeat(60))
        logger.info("URL: $url")
        logger.info("Destination: ${outputDir.absolutePath}")
        logger.info("=".repeat(60))
    }

    private fun printSuccess(mp3File: File) {
        logger.info("\n" + "=".repeat(60))
        logger.info("✓ SUCCESS!")
        logger.info("File: ${mp3File.absolutePath}")
        logger.info("Size: ${mp3File.length() / 1024 / 1024} MB")
        logger.info("=".repeat(60))
    }

    private fun printError(e: Exception) {
        logger.error("\n" + "=".repeat(60))
        logger.error("✗ ERROR: ${e.message}")
        logger.error("=".repeat(60))
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(minutes, secs)
    }
}
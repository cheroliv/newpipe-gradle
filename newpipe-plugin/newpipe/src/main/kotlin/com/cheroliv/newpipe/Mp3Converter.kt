package com.cheroliv.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.time.Year

/**
 * Real implementation of [AudioConverter].
 *
 * FFmpeg strategy (resolved once at construction, in priority order):
 *
 *  1. [FfmpegStrategy.LOCAL]  — FFmpeg found on the system PATH
 *  2. [FfmpegStrategy.DOCKER] — Docker available and [dockerImage] is set
 *  3. [FfmpegStrategy.NONE]   — neither available → [ConversionException] at runtime
 *
 * @param dockerImage Docker image to pull when FFmpeg is not installed locally.
 *                    Defaults to [NewpipeExtension.DEFAULT_FFMPEG_IMAGE].
 *                    Ignored when FFmpeg is available locally.
 */
class Mp3Converter(
    private val dockerImage: String = NewpipeExtension.DEFAULT_FFMPEG_IMAGE
) : AudioConverter {

    private val logger = LoggerFactory.getLogger(Mp3Converter::class.java)

    // Resolved once — avoids repeated process checks on every track
    private val strategy: FfmpegStrategy = resolveStrategy()

    private enum class FfmpegStrategy { LOCAL, DOCKER, NONE }

    private fun resolveStrategy(): FfmpegStrategy = when {
        isCommandAvailable("ffmpeg", "-version") -> {
            logger.info("FFmpeg strategy: LOCAL")
            FfmpegStrategy.LOCAL
        }
        isCommandAvailable("docker", "info") -> {
            logger.info("FFmpeg strategy: DOCKER (image=$dockerImage)")
            FfmpegStrategy.DOCKER
        }
        else -> {
            logger.warn("FFmpeg strategy: NONE — install FFmpeg or Docker")
            FfmpegStrategy.NONE
        }
    }

    // ------------------------------------------------------------------
    // AudioConverter implementation
    // ------------------------------------------------------------------

    override fun alreadyDownloaded(
        mp3File: File,
        title: String,
        artist: String,
        youtubeDurationSeconds: Long,
        toleranceSeconds: Long
    ): Boolean {
        if (!mp3File.exists()) return false
        return try {
            val audioFile = AudioFileIO.read(mp3File)
            val tag = audioFile.tag ?: return false
            val existingTitle  = tag.getFirst(FieldKey.TITLE)
            val existingArtist = tag.getFirst(FieldKey.ARTIST)

            // Duration check via audio header
            val existingDuration = audioFile.audioHeader.trackLength.toLong()
            val durationMatch = Math.abs(existingDuration - youtubeDurationSeconds) <= toleranceSeconds

            val match = existingTitle == title && existingArtist == artist && durationMatch
            if (match) logger.info("⏭ Skipping already downloaded track: ${mp3File.name}")
            match
        } catch (e: Exception) {
            logger.warn("Could not read tags from ${mp3File.name}: ${e.message}")
            false
        }
    }

    override suspend fun convertToMp3(
        inputFile: File,
        outputFile: File,
        bitrate: String
    ): File = withContext(Dispatchers.IO) {
        logger.info("Converting to MP3: ${inputFile.name} → ${outputFile.name}")
        when (strategy) {
            FfmpegStrategy.LOCAL  -> convertLocal(inputFile, outputFile, bitrate)
            FfmpegStrategy.DOCKER -> convertDocker(inputFile, outputFile, bitrate)
            FfmpegStrategy.NONE   -> throw ConversionException(
                "FFmpeg is not installed and Docker is not available. " +
                        "Install FFmpeg (apt install ffmpeg / brew install ffmpeg) " +
                        "or install Docker to enable MP3 conversion."
            )
        }
        cleanupTemp(inputFile, outputFile)
        logger.info("Conversion complete: ${outputFile.length() / 1024 / 1024} MB")
        outputFile
    }

    override suspend fun addMetadata(
        mp3File: File,
        title: String?,
        artist: String?,
        album: String?,
        thumbnailUrl: String?
    ): File = withContext(Dispatchers.IO) {
        try {
            val audioFile = AudioFileIO.read(mp3File)
            val tag = audioFile.tagOrCreateAndSetDefault

            title?.let  { tag.setField(FieldKey.TITLE,  it) }
            artist?.let { tag.setField(FieldKey.ARTIST, it) }
            album?.let  { tag.setField(FieldKey.ALBUM,  it) }
            tag.setField(FieldKey.YEAR, Year.now().toString())

            thumbnailUrl?.let { url ->
                try {
                    val imageData = URI(url).toURL().openStream().readBytes()
                    val tmpFile = File.createTempFile("thumbnail", ".jpg")
                        .apply { writeBytes(imageData); deleteOnExit() }
                    tag.setField(ArtworkFactory.createArtworkFromFile(tmpFile))
                    logger.info("Cover art added")
                } catch (e: Exception) {
                    logger.warn("Could not add cover art: ${e.message}")
                }
            }

            audioFile.commit()
            logger.info("Metadata written: $title — $artist")
        } catch (e: Exception) {
            logger.error("Failed to write metadata: ${e.message}", e)
            // Non-fatal — return the file as-is
        }
        mp3File
    }

    // ------------------------------------------------------------------
    // Conversion strategies
    // ------------------------------------------------------------------

    /**
     * Converts using the locally installed `ffmpeg` binary.
     */
    private fun convertLocal(inputFile: File, outputFile: File, bitrate: String) {
        val command = buildFfmpegArgs(
            inputPath  = inputFile.absolutePath,
            outputPath = outputFile.absolutePath,
            bitrate    = bitrate
        )
        runProcess(listOf("ffmpeg") + command, "FFmpeg (local)")
    }

    /**
     * Converts by running FFmpeg inside a Docker container.
     *
     * The parent directory of the input file is mounted as `/data` inside
     * the container so both the temp file and the output file are reachable
     * without any copy.
     *
     * Command shape:
     * ```
     * docker run --rm
     *   -v <workDir>:/data
     *   <dockerImage>
     *   -i /data/<inputFileName>
     *   -vn -ar 44100 -ac 2 -b:a <bitrate> -y
     *   /data/<outputFileName>
     * ```
     */
    private fun convertDocker(inputFile: File, outputFile: File, bitrate: String) {
        // Both files must be in the same directory so one -v mount covers both
        require(inputFile.parentFile.canonicalPath == outputFile.parentFile.canonicalPath) {
            "Docker conversion requires input and output to share the same directory"
        }

        val workDir      = inputFile.parentFile.canonicalPath
        val inputInContainer  = "/data/${inputFile.name}"
        val outputInContainer = "/data/${outputFile.name}"

        val command = listOf(
            "docker", "run", "--rm",
            "-v", "$workDir:/data"
        ) + listOf(dockerImage) + buildFfmpegArgs(
            inputPath  = inputInContainer,
            outputPath = outputInContainer,
            bitrate    = bitrate
        )

        runProcess(command, "FFmpeg (Docker image=$dockerImage)")
    }

    /**
     * Builds the FFmpeg argument list (without the `ffmpeg` binary prefix).
     */
    private fun buildFfmpegArgs(inputPath: String, outputPath: String, bitrate: String) =
        listOf(
            "-i",    inputPath,
            "-vn",               // strip video stream
            "-ar",   "44100",    // sample rate
            "-ac",   "2",        // stereo
            "-b:a",  bitrate,    // audio bitrate
            "-y",                // overwrite output without asking
            outputPath
        )

    /**
     * Runs an external process and throws [ConversionException] if it fails.
     */
    private fun runProcess(command: List<String>, label: String) {
        logger.debug("Running: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.error("$label failed (exit=$exitCode):\n$output")
            throw ConversionException("$label conversion failed (exit code $exitCode)")
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Checks whether a command is available and exits cleanly.
     * Used to probe `ffmpeg` and `docker` without side effects.
     */
    private fun isCommandAvailable(vararg command: String): Boolean = try {
        ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    } catch (_: Exception) {
        false
    }

    private fun cleanupTemp(inputFile: File, outputFile: File) {
        if (inputFile.exists() && inputFile.canonicalPath != outputFile.canonicalPath) {
            inputFile.delete()
            logger.debug("Temp file deleted: ${inputFile.name}")
        }
    }
}

class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)
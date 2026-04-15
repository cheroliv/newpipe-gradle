package com.cheroliv.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.net.URI
import java.time.Year
import kotlin.math.abs

/**
 * Real implementation of [AudioConverter].
 *
 * FFmpeg strategy (resolved once at construction, in priority order):
 *
 *  1. [FfmpegStrategy.LOCAL]  — FFmpeg found on the system PATH, AND [forceDocker] is false
 *  2. [FfmpegStrategy.DOCKER] — Docker available and [dockerImage] is set
 *  3. [FfmpegStrategy.NONE]   — neither available → [ConversionException] at runtime
 *
 * @param dockerImage  Docker image to use when FFmpeg is not installed locally.
 *                     Defaults to [NewpipeExtension.DEFAULT_FFMPEG_IMAGE].
 * @param forceDocker  When true, skip the local FFmpeg probe and go straight to Docker,
 *                     even if `ffmpeg` is available on the system PATH.
 *                     Defaults to false.
 */
class Mp3Converter(
    private val dockerImage: String = NewpipeExtension.DEFAULT_FFMPEG_IMAGE,
    private val forceDocker: Boolean = false
) : AudioConverter {

    private val logger = getLogger(Mp3Converter::class.java)

    private val strategy: FfmpegStrategy = resolveStrategy()

    private enum class FfmpegStrategy { LOCAL, DOCKER, NONE }

    private fun resolveStrategy(): FfmpegStrategy = when {
        !forceDocker && isCommandAvailable("ffmpeg", "-version") -> {
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

    /**
     * Returns true only if the file exists, tags match, duration matches,
     * AND the encoded bitrate is not meaningfully below the best stream available.
     *
     * A "meaningful" gap is defined as the existing file being more than
     * [BITRATE_UPGRADE_THRESHOLD_KBPS] kbps below the best available stream.
     * This avoids re-downloading when YouTube reports e.g. 321 kbps and the
     * encoded file measures 318 kbps after FFmpeg re-encoding.
     */
    override fun alreadyDownloaded(
        mp3File: File,
        title: String,
        artist: String,
        youtubeDurationSeconds: Long,
        toleranceSeconds: Long,
        bestAvailableBitrateKbps: Int
    ): Boolean {
        if (!mp3File.exists()) return false
        return try {
            val audioFile = AudioFileIO.read(mp3File)
            val tag = audioFile.tag ?: return false

            val existingTitle    = tag.getFirst(FieldKey.TITLE)
            val existingArtist   = tag.getFirst(FieldKey.ARTIST)
            val existingDuration = audioFile.audioHeader.trackLength.toLong()
            val existingBitrate = try { audioFile.audioHeader.bitRateAsNumber.toString().trim().toIntOrNull() ?: 0 } catch (_: Exception) { 0 }

            val durationMatch = abs(existingDuration - youtubeDurationSeconds) <= toleranceSeconds
            val tagsMatch     = existingTitle == title && existingArtist == artist

            if (!tagsMatch || !durationMatch) return false

            // Quality upgrade check — only when the caller provides a meaningful bitrate
            if (bestAvailableBitrateKbps > 0 && existingBitrate > 0) {
                val gap = bestAvailableBitrateKbps - existingBitrate
                if (gap > BITRATE_UPGRADE_THRESHOLD_KBPS) {
                    logger.info(
                        "↑ Quality upgrade available for ${mp3File.name}: " +
                                "existing=${existingBitrate}k, available=${bestAvailableBitrateKbps}k — will re-download"
                    )
                    return false
                }
            }

            logger.info("⏭ Skipping already downloaded track: ${mp3File.name}")
            true
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
            FfmpegStrategy.DOCKER -> {
                convertDocker(inputFile, outputFile, bitrate)
                // Guarantee the file is writable by the current process
                // even if the container ignored --user
                if (outputFile.exists()) outputFile.setWritable(true, false)
            }
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

    /**
     * Writes ID3 tags to [mp3File].
     *
     * Strategy:
     * 1. Full pass — title, artist, album, year, cover art.
     * 2. If the full pass throws, attempt a minimal fallback with only [title]
     *    and [artist] — always available from [VideoMetadata] / YAML.
     * 3. If the fallback also throws, log warn and return the file as-is.
     */
    override suspend fun addMetadata(
        mp3File: File,
        title: String?,
        artist: String?,
        album: String?,
        thumbnailUrl: String?
    ): File = withContext(Dispatchers.IO) {
        try {
            writeFullMetadata(mp3File, title, artist, album, thumbnailUrl)
            logger.info("Metadata written: $title — $artist")
        } catch (fullPassEx: Exception) {
            logger.warn(
                "Full metadata pass failed for ${mp3File.name}: ${fullPassEx.message} — " +
                        "attempting minimal fallback (title + artist only)"
            )
            try {
                writeMinimalMetadata(mp3File, title, artist)
                logger.info("Minimal metadata written (fallback): $title — $artist")
            } catch (fallbackEx: Exception) {
                logger.warn(
                    "Minimal metadata fallback also failed for ${mp3File.name}: " +
                            "${fallbackEx.message} — file will have no tags"
                )
            }
        }
        mp3File
    }

    // ------------------------------------------------------------------
    // Metadata helpers
    // ------------------------------------------------------------------

    /**
     * Full metadata pass: title, artist, album, year, and optional cover art.
     * Throws on any error — the caller handles fallback.
     */
    private fun writeFullMetadata(
        mp3File: File,
        title: String?,
        artist: String?,
        album: String?,
        thumbnailUrl: String?
    ) {
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
                // Cover art is non-fatal — commit text tags anyway
                logger.warn("Could not add cover art: ${e.message}")
            }
        }

        audioFile.commit()
    }

    /**
     * Minimal fallback pass: only [title] and [artist].
     * Throws on any error so the caller can warn and continue.
     */
    private fun writeMinimalMetadata(mp3File: File, title: String?, artist: String?) {
        val audioFile = AudioFileIO.read(mp3File)
        val tag = audioFile.tagOrCreateAndSetDefault
        title?.let  { tag.setField(FieldKey.TITLE,  it) }
        artist?.let { tag.setField(FieldKey.ARTIST, it) }
        audioFile.commit()
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
     *   --user <uid>:<gid>
     *   -v <workDir>:/data
     *   <dockerImage>
     *   -i /data/<inputFileName>
     *   -vn -ar 44100 -ac 2 -b:a <bitrate> -y
     *   /data/<outputFileName>
     * ```
     *
     * Note: [jrottenberg/ffmpeg] uses `ffmpeg` as its Docker entrypoint,
     * so the arguments are passed directly without repeating the binary name.
     */
    private fun convertDocker(inputFile: File, outputFile: File, bitrate: String) {
        require(inputFile.parentFile.canonicalPath == outputFile.parentFile.canonicalPath) {
            "Docker conversion requires input and output to share the same directory"
        }

        val workDir           = inputFile.parentFile.canonicalPath
        val inputInContainer  = "/data/${inputFile.name}"
        val outputInContainer = "/data/${outputFile.name}"

        val uid = ProcessBuilder("id", "-u")
            .redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText().trim()
        val gid = ProcessBuilder("id", "-g")
            .redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText().trim()

        val command = listOf(
            "docker", "run", "--rm",
            "--user", "$uid:$gid",
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
            "-i",   inputPath,
            "-vn",            // strip video stream
            "-ar",  "44100",  // sample rate
            "-ac",  "2",      // stereo
            "-b:a", bitrate,  // audio bitrate
            "-y",             // overwrite output without asking
            outputPath
        )

    /**
     * Runs an external process and throws [ConversionException] if it fails.
     * FFmpeg writes progress to stderr — [redirectErrorStream] merges it into
     * stdout so it is captured in [output] and surfaced in the log on failure.
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

    companion object {
        /**
         * Minimum gap (kbps) between the available stream bitrate and the
         * existing file's bitrate before a quality upgrade re-download is triggered.
         *
         * Avoids re-downloading when FFmpeg re-encoding produces e.g. 317 kbps
         * from a 320 kbps stream — that's normal lossy encoder variance, not
         * a meaningful quality difference.
         */
        const val BITRATE_UPGRADE_THRESHOLD_KBPS = 32
    }
}

class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)
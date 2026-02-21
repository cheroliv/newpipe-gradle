package com.cheroliv.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.time.Year

/**
 * Handles audio conversion to MP3 and ID3 metadata tagging.
 */
class Mp3Converter {

    private val logger = LoggerFactory.getLogger(Mp3Converter::class.java)

    /**
     * Converts an audio file to MP3 using FFmpeg.
     * Falls back to a plain copy if FFmpeg is not available on the system.
     */
    suspend fun convertToMp3(
        inputFile: File,
        outputFile: File,
        bitrate: String = "192k",
        onProgress: (percent: Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        logger.info("Converting to MP3: ${inputFile.name} -> ${outputFile.name}")

        try {
            if (!isFFmpegAvailable()) {
                logger.warn("FFmpeg not found — copying audio file without conversion")
                inputFile.copyTo(outputFile, overwrite = true)
                return@withContext outputFile
            }

            // FFmpeg command for MP3 conversion
            val command = listOf(
                "ffmpeg",
                "-i", inputFile.absolutePath,
                "-vn",          // strip video
                "-ar", "44100", // sample rate
                "-ac", "2",     // stereo
                "-b:a", bitrate,
                "-y",           // overwrite output
                outputFile.absolutePath
            )

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            // Read FFmpeg output to track progress
            val output = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    output.append(line).append("\n")
                    if (line.contains("time=")) {
                        logger.debug(line)
                    }
                }
            }

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.error("FFmpeg exited with code: $exitCode")
                logger.error("FFmpeg output: $output")
                throw ConversionException("FFmpeg conversion failed")
            }

            logger.info("MP3 conversion successful: ${outputFile.length() / 1024 / 1024} MB")

            // Remove the source temp file
            if (inputFile.exists() && inputFile != outputFile) {
                inputFile.delete()
                logger.debug("Temp file deleted: ${inputFile.name}")
            }

            outputFile
        } catch (e: Exception) {
            logger.error("Conversion error: ${e.message}", e)
            throw ConversionException("Failed to convert to MP3", e)
        }
    }

    /**
     * Writes ID3 tags to an MP3 file (title, artist, album, year, cover art).
     * Errors during tagging are non-fatal — the file is returned as-is.
     */
    suspend fun addMetadata(
        mp3File: File,
        title: String?,
        artist: String?,
        album: String? = null,
        thumbnailUrl: String? = null
    ): File = withContext(Dispatchers.IO) {
        logger.info("Writing ID3 metadata to: ${mp3File.name}")

        try {
            val audioFile = AudioFileIO.read(mp3File)
            val tag = audioFile.tagOrCreateAndSetDefault

            title?.let { tag.setField(FieldKey.TITLE, it) }
            artist?.let { tag.setField(FieldKey.ARTIST, it) }
            album?.let { tag.setField(FieldKey.ALBUM, it) }
            tag.setField(FieldKey.YEAR, Year.now().toString())

            // Download and embed cover art if available
            thumbnailUrl?.let { url ->
                try {
                    val artwork = downloadThumbnail(url)
                    artwork?.let {
                        tag.setField(artwork)
                        logger.info("Cover art embedded successfully")
                    }
                } catch (e: Exception) {
                    logger.warn("Could not embed cover art: ${e.message}")
                }
            }

            audioFile.commit()
            logger.info("Metadata written: $title — $artist")
            mp3File
        } catch (e: Exception) {
            logger.error("Failed to write metadata: ${e.message}", e)
            // Non-fatal: return the file even without tags
            mp3File
        }
    }

    /**
     * Downloads a thumbnail image and wraps it as an [Artwork] object.
     */
    private fun downloadThumbnail(url: String): Artwork? {
        return try {
            val connection = URL(url).openConnection()
            connection.connect()
            val imageData = connection.getInputStream().readBytes()
            ArtworkFactory.createArtworkFromFile(
                File.createTempFile("thumbnail", ".jpg").apply {
                    writeBytes(imageData)
                    deleteOnExit()
                }
            )
        } catch (e: Exception) {
            logger.warn("Thumbnail download failed: ${e.message}")
            null
        }
    }

    /**
     * Returns true if FFmpeg is installed and accessible on the system PATH.
     */
    private fun isFFmpegAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("ffmpeg", "-version")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Thrown when an audio conversion operation fails.
 */
class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)
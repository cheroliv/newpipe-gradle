package com.cheroliv.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.time.Year
import kotlin.math.abs

/**
 * Handles audio conversion to MP3 and ID3 metadata tagging.
 */
class Mp3Converter: AudioConverter {

    private val logger = LoggerFactory.getLogger(Mp3Converter::class.java)

    // ------------------------------------------------------------------
    // Duplicate detection
    // ------------------------------------------------------------------

    /**
     * Returns true if [mp3File] already exists and its ID3 tags + audio duration
     * match the given [title], [artist] and [youtubeDurationSeconds].
     *
     * Duration comparison uses a ±[toleranceSeconds] window to absorb minor
     * discrepancies between YouTube metadata and the actual encoded audio length.
     */
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
            val header = audioFile.audioHeader

            val tagTitle  = tag.getFirst(FieldKey.TITLE).orEmpty().trim()
            val tagArtist = tag.getFirst(FieldKey.ARTIST).orEmpty().trim()
            val tagDuration = header.trackLength.toLong() // seconds

            val titlesMatch  = tagTitle.equals(title.trim(), ignoreCase = true)
            val artistsMatch = tagArtist.equals(artist.trim(), ignoreCase = true)
            val durationsMatch = abs(tagDuration - youtubeDurationSeconds) <= toleranceSeconds

            if (titlesMatch && artistsMatch && durationsMatch) {
                logger.info(
                    "⏭ Skipping already downloaded track: " +
                            "\"$tagTitle\" by $tagArtist (${tagDuration}s)"
                )
                true
            } else {
                // Log which fields differ to help diagnose unexpected re-downloads
                if (!titlesMatch)  logger.debug("Title mismatch: tag=\"$tagTitle\" vs youtube=\"$title\"")
                if (!artistsMatch) logger.debug("Artist mismatch: tag=\"$tagArtist\" vs youtube=\"$artist\"")
                if (!durationsMatch) logger.debug(
                    "Duration mismatch: tag=${tagDuration}s vs youtube=${youtubeDurationSeconds}s " +
                            "(tolerance ±${toleranceSeconds}s)"
                )
                false
            }
        } catch (e: Exception) {
            // If we cannot read the file, treat it as not downloaded so it gets replaced
            logger.warn("Could not read existing MP3 tags for ${mp3File.name}: ${e.message}")
            false
        }
    }

    // ------------------------------------------------------------------
    // Conversion
    // ------------------------------------------------------------------

    /**
     * Converts an audio file to MP3 using FFmpeg.
     * Falls back to a plain copy if FFmpeg is not available on the system.
     */
    override suspend fun convertToMp3(
        inputFile: File,
        outputFile: File,
        bitrate: String
    ): File = withContext(Dispatchers.IO) {
        logger.info("Converting to MP3: ${inputFile.name} -> ${outputFile.name}")

        try {
            if (!isFFmpegAvailable()) {
                logger.warn("FFmpeg not found — copying audio file without conversion")
                inputFile.copyTo(outputFile, overwrite = true)
                return@withContext outputFile
            }

            val command = listOf(
                "ffmpeg",
                "-i", inputFile.absolutePath,
                "-vn",
                "-ar", "44100",
                "-ac", "2",
                "-b:a", bitrate,
                "-y",
                outputFile.absolutePath
            )

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    output.append(line).append("\n")
                    if (line.contains("time=")) logger.debug(line)
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.error("FFmpeg exited with code: $exitCode")
                logger.error("FFmpeg output: $output")
                throw ConversionException("FFmpeg conversion failed")
            }

            logger.info("MP3 conversion successful: ${outputFile.length() / 1024 / 1024} MB")

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

    // ------------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------------

    /**
     * Writes ID3 tags to an MP3 file (title, artist, album, year, cover art).
     * Errors during tagging are non-fatal — the file is returned as-is.
     */
    override suspend fun addMetadata(
        mp3File: File,
        title: String?,
        artist: String?,
        album: String?,
        thumbnailUrl: String?
    ): File = withContext(Dispatchers.IO) {
        logger.info("Writing ID3 metadata to: ${mp3File.name}")

        try {
            val audioFile = AudioFileIO.read(mp3File)
            val tag = audioFile.tagOrCreateAndSetDefault

            title?.let  { tag.setField(FieldKey.TITLE, it) }
            artist?.let { tag.setField(FieldKey.ARTIST, it) }
            album?.let  { tag.setField(FieldKey.ALBUM, it) }
            tag.setField(FieldKey.YEAR, Year.now().toString())

            thumbnailUrl?.let { url ->
                try {
                    downloadThumbnail(url)?.let { tag.setField(it) }
                    logger.info("Cover art embedded successfully")
                } catch (e: Exception) {
                    logger.warn("Could not embed cover art: ${e.message}")
                }
            }

            audioFile.commit()
            logger.info("Metadata written: $title — $artist")
            mp3File
        } catch (e: Exception) {
            logger.error("Failed to write metadata: ${e.message}", e)
            mp3File
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private fun downloadThumbnail(url: String): Artwork? {
        return try {
            val connection = url.run(::URI).toURL().openConnection()
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

    private fun isFFmpegAvailable(): Boolean {
        return try {
            ProcessBuilder("ffmpeg", "-version")
                .redirectErrorStream(true)
                .start()
                .waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Thrown when an audio conversion operation fails.
 */
class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)
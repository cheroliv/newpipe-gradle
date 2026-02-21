package com.cheroliv.newpipe

import com.cheroliv.newpipe.NewpipeManager.REGEX_CLEAN_TUNE_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream


/**
 * Downloads audio from YouTube videos using NewPipe Extractor
 * to retrieve stream metadata and OkHttp for the actual HTTP download.
 */
class YouTubeDownloader {

    private val logger = LoggerFactory.getLogger(YouTubeDownloader::class.java)
    private val httpClient = OkHttpClient()

    init {
        // Initialize NewPipe with the YouTube service
        NewPipe.init(DownloaderImpl.getInstance())
    }

    /**
     * Fetches metadata for a YouTube video.
     * @param url YouTube video URL
     * @return StreamInfo containing all video metadata
     */
    suspend fun getVideoInfo(url: String): StreamInfo = withContext(Dispatchers.IO) {
        logger.info("Extracting video information for: $url")

        try {
            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()

            val streamInfo = StreamInfo.getInfo(extractor)

            logger.info("Title: ${streamInfo.name}")
            logger.info("Uploader: ${streamInfo.uploaderName}")
            logger.info("Duration: ${streamInfo.duration} seconds")

            streamInfo
        } catch (e: Exception) {
            logger.error("Failed to extract video information: ${e.message}", e)
            throw DownloadException("Unable to extract video information", e)
        }
    }

    /**
     * Selects the best available audio stream.
     * Prefers high-quality formats (M4A, WebM) sorted by bitrate.
     */
    fun getBestAudioStream(streamInfo: StreamInfo): AudioStream {
        val audioStreams = streamInfo.audioStreams

        if (audioStreams.isEmpty()) {
            throw DownloadException("No audio streams available for this video")
        }

        // Sort by descending bitrate and pick the best one
        val bestStream = audioStreams
            .filter { it.averageBitrate > 0 }
            .maxByOrNull { it.averageBitrate }
            ?: audioStreams.first()

        logger.info(
            "Selected audio stream: ${bestStream.format?.name ?: "unknown"}, " +
                    "bitrate: ${bestStream.averageBitrate} kbps"
        )

        return bestStream
    }

    /**
     * Downloads the audio stream to a local file.
     * Reports download progress via the [onProgress] callback.
     */
    suspend fun downloadAudio(
        audioStream: AudioStream,
        outputFile: File,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        logger.info("Downloading to: ${outputFile.absolutePath}")

        try {
            val request = Request.Builder()
                .url(audioStream.content)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadException("Download request failed: ${response.code}")
                }

                val totalBytes = response.body.contentLength()
                var downloadedBytes = 0L
                var lastPercent = 0

                response.body.byteStream().use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(downloadedBytes, totalBytes, percent)
                                }
                            }
                        }
                    }
                }

                logger.info("Download finished: ${downloadedBytes / 1024 / 1024} MB")
            }

            outputFile
        } catch (e: Exception) {
            logger.error("Download error: ${e.message}", e)
            if (outputFile.exists()) outputFile.delete()
            throw DownloadException("Audio file download failed", e)
        }
    }

    /**
     * Sanitizes a string for use as a file or directory name
     * by removing invalid characters and trimming whitespace.
     */
    fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex(REGEX_CLEAN_TUNE_NAME), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
    }
}

/**
 * Thrown when a download operation fails.
 */
class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
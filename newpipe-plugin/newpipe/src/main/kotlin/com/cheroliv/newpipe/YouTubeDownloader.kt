package com.cheroliv.newpipe

import com.cheroliv.newpipe.NewpipeManager.REGEX_CLEAN_TUNE_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Real implementation of [VideoInfoProvider] using NewPipe Extractor + OkHttp.
 *
 * Uses the shared [DownloaderImpl.httpClient] singleton — no separate OkHttpClient
 * is created here, so connection pools and thread pools are shared with the
 * metadata extraction layer.
 */
class YouTubeDownloader : VideoInfoProvider {

    private val logger = LoggerFactory.getLogger(YouTubeDownloader::class.java)

    private val httpClient = DownloaderImpl.httpClient

    companion object {
        private const val DOWNLOAD_BUFFER_SIZE = 128 * 1024

        /**
         * Preferred bitrate targets in descending priority order (kbps).
         * Format is deliberately ignored — FFmpeg handles conversion.
         *
         * Selection logic: for each target, pick the stream with the lowest
         * bitrate still >= the target (avoids unnecessary overkill).
         * If no stream reaches [MINIMUM_ACCEPTABLE_BITRATE], take the best
         * available and log a warning.
         */
        private val PREFERRED_BITRATES = listOf(320, 256, 192, 160)
        private const val MINIMUM_ACCEPTABLE_BITRATE = 160
    }

    init {
        NewPipe.init(DownloaderImpl.getInstance())
    }

    override suspend fun getVideoInfo(url: String): VideoMetadata = withContext(Dispatchers.IO) {
        logger.info("Extracting video information for: $url")
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()
            val streamInfo = StreamInfo.getInfo(extractor)
            logger.info("Title: ${streamInfo.name}")
            logger.info("Uploader: ${streamInfo.uploaderName}")
            logger.info("Duration: ${streamInfo.duration} seconds")
            VideoMetadata(
                title        = streamInfo.name,
                uploaderName = streamInfo.uploaderName,
                duration     = streamInfo.duration,
                url          = streamInfo.url,
                streamInfo   = streamInfo
            )
        } catch (e: Exception) {
            logger.error("Failed to extract video information: ${e.message}", e)
            throw DownloadException("Unable to extract video information", e)
        }
    }

    override suspend fun getPlaylistVideoUrls(playlistUrl: String): List<String> =
        withContext(Dispatchers.IO) {
            val isMix = isMixRadioUrl(playlistUrl)
            logger.info("Fetching ${if (isMix) "Mix Radio" else "playlist"}: $playlistUrl")
            try {
                val service   = ServiceList.YouTube
                val extractor = service.getPlaylistExtractor(playlistUrl)
                extractor.fetchPage()

                val items     = mutableListOf<StreamInfoItem>()
                val firstPage = PlaylistInfo.getInfo(extractor)
                items += firstPage.relatedItems.filterIsInstance<StreamInfoItem>()

                if (isMix) {
                    logger.info("Mix Radio: using first page only (${items.size} track(s))")
                } else {
                    var nextPage  = firstPage.nextPage
                    var pageCount = 0
                    val maxPages  = 50
                    while (nextPage != null && pageCount < maxPages) {
                        pageCount++
                        logger.info("Fetching playlist page $pageCount...")
                        val moreItems = PlaylistInfo.getMoreItems(service, playlistUrl, nextPage)
                        items    += moreItems.items.filterIsInstance<StreamInfoItem>()
                        nextPage  = moreItems.nextPage
                    }
                    if (pageCount >= maxPages) logger.warn("Reached page cap ($maxPages) — playlist truncated")
                }

                items.map { it.url }.also { logger.info("Found ${it.size} video(s)") }
            } catch (e: Exception) {
                logger.error("Failed to fetch playlist: ${e.message}", e)
                throw DownloadException("Unable to fetch playlist videos", e)
            }
        }

    private fun isMixRadioUrl(url: String): Boolean = try {
        val params = url.substringAfter("?", "")
            .takeIf { it.isNotBlank() }
            ?.split("&")
            ?.mapNotNull { pair ->
                val k = pair.substringBefore("=")
                val v = pair.substringAfter("=", "")
                if (k.isNotBlank()) k to v else null
            }?.toMap() ?: return false
        val list = params["list"] ?: return false
        logger.debug("Mix detection — list=$list")
        list.startsWith("RD")
    } catch (e: Exception) {
        logger.debug("Mix detection failed for $url: ${e.message}")
        false
    }

    override suspend fun downloadBestAudio(
        metadata: VideoMetadata,
        outputFile: File,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val streamInfo  = metadata.streamInfo
            ?: throw DownloadException("No StreamInfo available — cannot download audio")
        val audioStreams = streamInfo.audioStreams
        if (audioStreams.isEmpty()) throw DownloadException("No audio streams available for this video")

        val bestStream = selectBestAudioStream(audioStreams)
        logger.info("Downloading to: ${outputFile.absolutePath}")

        try {
            val request = Request.Builder().url(bestStream.content).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful)
                    throw DownloadException("Download request failed: ${response.code}")

                val totalBytes      = response.body.contentLength()
                var downloadedBytes = 0L
                var lastPercent     = 0

                response.body.byteStream().use { input ->
                    BufferedOutputStream(FileOutputStream(outputFile), DOWNLOAD_BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
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
     * Returns the bitrate (kbps) of the stream that [selectBestAudioStream]
     * would pick for [metadata], without downloading anything.
     * Returns 0 if no stream with bitrate info is available.
     */
    override fun getBestAvailableBitrateKbps(metadata: VideoMetadata): Int {
        val streams = metadata.streamInfo?.audioStreams ?: return 0
        val candidates = streams.filter { it.averageBitrate > 0 }
        if (candidates.isEmpty()) return 0
        for (target in PREFERRED_BITRATES) {
            val match = candidates.filter { it.averageBitrate >= target }.minByOrNull { it.averageBitrate }
            if (match != null) return match.averageBitrate
        }
        return candidates.maxOf { it.averageBitrate }
    }

    /**
     * Selects the best audio stream using the [PREFERRED_BITRATES] ladder:
     * 320 → 256 → 192 → 160 kbps.
     *
     * For each target, picks the stream with the lowest bitrate still >= the target
     * (closest match, avoids overkill). Format is ignored — FFmpeg handles conversion.
     *
     * Falls back to the highest available stream with a warning if none reaches
     * [MINIMUM_ACCEPTABLE_BITRATE].
     */
    private fun selectBestAudioStream(audioStreams: List<AudioStream>): AudioStream {
        val candidates = audioStreams.filter { it.averageBitrate > 0 }

        if (candidates.isNotEmpty()) {
            for (target in PREFERRED_BITRATES) {
                val match = candidates
                    .filter { it.averageBitrate >= target }
                    .minByOrNull { it.averageBitrate }
                if (match != null) {
                    val fmt = match.format?.name?.lowercase() ?: "unknown"
                    logger.info("Selected ${target}k stream: $fmt ${match.averageBitrate} kbps")
                    return match
                }
            }

            // No stream reached MINIMUM_ACCEPTABLE_BITRATE — take the best available
            val fallback = candidates.maxByOrNull { it.averageBitrate }!!
            val fmt      = fallback.format?.name?.lowercase() ?: "unknown"
            logger.warn(
                "No stream >= ${MINIMUM_ACCEPTABLE_BITRATE}k found — " +
                        "falling back to best available: $fmt ${fallback.averageBitrate} kbps"
            )
            return fallback
        }

        // No bitrate info at all — last resort
        logger.warn("No streams with bitrate info — using first available stream")
        return audioStreams.first()
    }

    override fun sanitizeFileName(name: String): String =
        name.replace(Regex(REGEX_CLEAN_TUNE_NAME), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
}

class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
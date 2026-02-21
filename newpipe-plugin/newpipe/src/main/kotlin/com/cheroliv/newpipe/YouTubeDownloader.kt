package com.cheroliv.newpipe

import com.cheroliv.newpipe.NewpipeManager.REGEX_CLEAN_TUNE_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
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

    // Reuse the singleton — no new connection pool, no new thread pool
    private val httpClient = DownloaderImpl.httpClient

    companion object {
        // 128 KB — significantly reduces syscall overhead vs the default 8 KB
        private const val DOWNLOAD_BUFFER_SIZE = 128 * 1024
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
                title = streamInfo.name,
                uploaderName = streamInfo.uploaderName,
                duration = streamInfo.duration,
                url = streamInfo.url,
                streamInfo = streamInfo
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
                val service = ServiceList.YouTube
                val extractor = service.getPlaylistExtractor(playlistUrl)
                extractor.fetchPage()

                val items = mutableListOf<StreamInfoItem>()

                val firstPage = PlaylistInfo.getInfo(extractor)
                items += firstPage.relatedItems.filterIsInstance<StreamInfoItem>()

                if (isMix) {
                    // Mix Radio playlists are dynamically generated and infinite —
                    // the first page already contains ~15 tracks, which is enough.
                    logger.info("Mix Radio: using first page only (${items.size} track(s))")
                } else {
                    var nextPage = firstPage.nextPage
                    var pageCount = 0
                    val maxPages = 50

                    while (nextPage != null && pageCount < maxPages) {
                        pageCount++
                        logger.info("Fetching playlist page $pageCount...")
                        val moreItems = PlaylistInfo.getMoreItems(service, playlistUrl, nextPage)
                        items += moreItems.items.filterIsInstance<StreamInfoItem>()
                        nextPage = moreItems.nextPage
                    }

                    if (pageCount >= maxPages) {
                        logger.warn("Reached page cap ($maxPages) — playlist truncated")
                    }
                }

                val urls = items.map { it.url }
                logger.info("Found ${urls.size} video(s)")
                urls
            } catch (e: Exception) {
                logger.error("Failed to fetch playlist: ${e.message}", e)
                throw DownloadException("Unable to fetch playlist videos", e)
            }
        }

    /**
     * Returns true if the URL is a YouTube Mix Radio (list=RD...).
     * Mix Radio playlists are dynamically generated — pagination is infinite
     * so we only consume the first page.
     */
    private fun isMixRadioUrl(url: String): Boolean {
        return try {
            val query = url.substringAfter("?", missingDelimiterValue = "")
            if (query.isBlank()) return false
            val params = query.split("&")
                .mapNotNull { pair ->
                    val key = pair.substringBefore("=")
                    val value = pair.substringAfter("=", missingDelimiterValue = "")
                    if (key.isNotBlank()) key to value else null
                }.toMap()
            val listParam = params["list"] ?: return false
            logger.debug("Mix detection — list=$listParam")
            listParam.startsWith("RD")
        } catch (e: Exception) {
            logger.debug("Mix detection failed for $url: ${e.message}")
            false
        }
    }

    override suspend fun downloadBestAudio(
        metadata: VideoMetadata,
        outputFile: File,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val streamInfo = metadata.streamInfo
            ?: throw DownloadException("No StreamInfo available — cannot download audio")

        val audioStreams = streamInfo.audioStreams
        if (audioStreams.isEmpty()) throw DownloadException("No audio streams available for this video")

        val bestStream = audioStreams
            .filter { it.averageBitrate > 0 }
            .maxByOrNull { it.averageBitrate }
            ?: audioStreams.first()

        logger.info("Selected audio stream: ${bestStream.format?.name ?: "unknown"}, ${bestStream.averageBitrate} kbps")
        logger.info("Downloading to: ${outputFile.absolutePath}")

        try {
            val request = Request.Builder().url(bestStream.content).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw DownloadException("Download request failed: ${response.code}")

                val totalBytes = response.body.contentLength()
                var downloadedBytes = 0L
                var lastPercent = 0

                response.body.byteStream().use { input ->
                    // BufferedOutputStream avoids per-chunk syscalls to the kernel
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

    override fun sanitizeFileName(name: String): String =
        name.replace(Regex(REGEX_CLEAN_TUNE_NAME), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
}

class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
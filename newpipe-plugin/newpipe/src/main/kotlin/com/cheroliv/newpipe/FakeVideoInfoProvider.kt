package com.cheroliv.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Test fake for [VideoInfoProvider].
 * Returns predictable fixed data instantly — no network calls are made.
 */
class FakeVideoInfoProvider : VideoInfoProvider {

    companion object {
        const val FAKE_TITLE    = "Fake Song Title"
        const val FAKE_ARTIST   = "Fake Artist"
        const val FAKE_DURATION = 180L
        val FAKE_PLAYLIST_URLS  = listOf(
            "https://www.youtube.com/watch?v=fake001",
            "https://www.youtube.com/watch?v=fake002"
        )
    }

    override suspend fun getVideoInfo(url: String): VideoMetadata = VideoMetadata(
        title        = FAKE_TITLE,
        uploaderName = FAKE_ARTIST,
        duration     = FAKE_DURATION,
        url          = url,
        streamInfo   = null
    )

    override suspend fun getPlaylistVideoUrls(playlistUrl: String): List<String> =
        FAKE_PLAYLIST_URLS

    override suspend fun downloadBestAudio(
        metadata: VideoMetadata,
        outputFile: File,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        outputFile.createNewFile()
        outputFile
    }

    /**
     * Always returns 0 — no stream info available in fake/test mode.
     * [alreadyDownloaded] ignores the bitrate check when this returns 0.
     */
    override fun getBestAvailableBitrateKbps(metadata: VideoMetadata): Int = 0

    override fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9 \\-_]"), "").trim()
}
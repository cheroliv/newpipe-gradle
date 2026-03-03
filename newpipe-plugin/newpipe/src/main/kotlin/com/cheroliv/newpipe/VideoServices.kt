package com.cheroliv.newpipe

import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File

/**
 * Lightweight representation of the video metadata the task needs.
 * Decouples the task and fakes from NewPipe's [StreamInfo] constructor complexity.
 */
data class VideoMetadata(
    val title: String,
    val uploaderName: String,
    val duration: Long,
    val url: String,
    val streamInfo: StreamInfo? = null  // null in fake/test mode
)

/**
 * Abstracts YouTube data fetching so the task can work
 * with either the real implementation or a test fake.
 */
interface VideoInfoProvider {
    suspend fun getVideoInfo(url: String): VideoMetadata
    suspend fun getPlaylistVideoUrls(playlistUrl: String): List<String>
    suspend fun downloadBestAudio(
        metadata: VideoMetadata,
        outputFile: File,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit
    ): File
    fun sanitizeFileName(name: String): String

    /**
     * Returns the bitrate (kbps) of the best audio stream available for [metadata],
     * using the same selection ladder as [downloadBestAudio].
     * Returns 0 if no stream with bitrate info is available (e.g. in fake/test mode).
     */
    fun getBestAvailableBitrateKbps(metadata: VideoMetadata): Int
}

/**
 * Abstracts MP3 conversion and tagging so the task can work
 * with either the real implementation or a test fake.
 */
interface AudioConverter {
    /**
     * Returns true if [mp3File] is already a fully downloaded, correctly tagged file
     * that is at least as good as the best available stream quality.
     *
     * @param bestAvailableBitrateKbps  Bitrate of the best YouTube stream currently available.
     *                                  Pass 0 to skip the quality upgrade check (test/fake mode).
     */
    fun alreadyDownloaded(
        mp3File: File,
        title: String,
        artist: String,
        youtubeDurationSeconds: Long,
        toleranceSeconds: Long = 2L,
        bestAvailableBitrateKbps: Int = 0
    ): Boolean
    suspend fun convertToMp3(inputFile: File, outputFile: File, bitrate: String = "192k"): File
    suspend fun addMetadata(
        mp3File: File,
        title: String?,
        artist: String?,
        album: String?,
        thumbnailUrl: String?
    ): File
}
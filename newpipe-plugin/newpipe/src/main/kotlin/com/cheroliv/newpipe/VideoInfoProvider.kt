package com.cheroliv.newpipe

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File

/**
 * Abstracts YouTube data fetching so the task can work
 * with either the real implementation or a test fake.
 */
interface VideoInfoProvider {
    suspend fun getVideoInfo(url: String): StreamInfo
    suspend fun getPlaylistVideoUrls(playlistUrl: String): List<String>
    fun getBestAudioStream(streamInfo: StreamInfo): AudioStream
    suspend fun downloadAudio(
        audioStream: AudioStream,
        outputFile: File,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit
    ): File
    fun sanitizeFileName(name: String): String
}

/**
 * Abstracts MP3 conversion and tagging so the task can work
 * with either the real implementation or a test fake.
 */
interface AudioConverter {
    fun alreadyDownloaded(
        mp3File: File,
        title: String,
        artist: String,
        youtubeDurationSeconds: Long,
        toleranceSeconds: Long = 2L
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
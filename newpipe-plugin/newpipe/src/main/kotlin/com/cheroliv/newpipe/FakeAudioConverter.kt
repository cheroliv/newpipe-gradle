package com.cheroliv.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Test fake for [AudioConverter].
 * Skips FFmpeg and jaudiotagger entirely — operations complete instantly.
 */
class FakeAudioConverter : AudioConverter {

    /**
     * Always returns false so every track goes through the full (fake) download flow.
     * Override in specific scenarios to test the duplicate-skip behaviour.
     */
    override fun alreadyDownloaded(
        mp3File: File,
        title: String,
        artist: String,
        youtubeDurationSeconds: Long,
        toleranceSeconds: Long
    ): Boolean = false

    /**
     * Renames / copies the temp file to the output path without invoking FFmpeg.
     */
    override suspend fun convertToMp3(inputFile: File, outputFile: File, bitrate: String): File {
        inputFile.renameTo(outputFile)
        if (!outputFile.exists()) withContext(Dispatchers.IO) {
            outputFile.createNewFile()
        }
        return outputFile
    }

    /**
     * No-op — jaudiotagger is not called in tests.
     */
    override suspend fun addMetadata(
        mp3File: File,
        title: String?,
        artist: String?,
        album: String?,
        thumbnailUrl: String?
    ): File = mp3File
}
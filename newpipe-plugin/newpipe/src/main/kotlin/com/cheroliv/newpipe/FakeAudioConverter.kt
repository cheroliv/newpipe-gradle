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
     * [bestAvailableBitrateKbps] is ignored — quality upgrade logic is not tested here.
     */
    override fun alreadyDownloaded(
        mp3File: File,
        title: String,
        artist: String,
        youtubeDurationSeconds: Long,
        toleranceSeconds: Long,
        bestAvailableBitrateKbps: Int
    ): Boolean = false

    override suspend fun convertToMp3(inputFile: File, outputFile: File, bitrate: String): File {
        inputFile.renameTo(outputFile)
        if (!outputFile.exists()) withContext(Dispatchers.IO) {
            outputFile.createNewFile()
        }
        return outputFile
    }

    override suspend fun addMetadata(
        mp3File: File,
        title: String?,
        artist: String?,
        album: String?,
        thumbnailUrl: String?
    ): File = mp3File
}
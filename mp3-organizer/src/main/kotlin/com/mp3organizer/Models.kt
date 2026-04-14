package com.mp3organizer

data class Artist(
    val id: Int? = null,
    val name: String,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val totalSizeBytes: Long = 0,
    val totalDurationSeconds: Int = 0
)

data class Album(
    val id: Int? = null,
    val title: String,
    val artistId: Int? = null,
    val artistName: String? = null,
    val year: Int? = null,
    val trackCount: Int = 0
)

data class Track(
    val id: Int? = null,
    val title: String,
    val artistId: Int? = null,
    val artistName: String? = null,
    val albumId: Int? = null,
    val albumTitle: String? = null,
    val albumYear: Int? = null,
    val filePath: String,
    val fileSizeBytes: Long,
    val durationSeconds: Int? = null,
    val bitrateKbps: Int? = null,
    val sampleRate: Int? = null,
    val hasVideo: Boolean = false,
    val tags: Map<String, String> = emptyMap(),
    val genres: List<String> = emptyList()
)

data class Genre(
    val id: Int? = null,
    val name: String,
    val trackCount: Int = 0
)

data class ArtistStats(
    val id: Int,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val totalSizeBytes: Long,
    val totalDurationSeconds: Int
) {
    val totalSizePretty: String
        get() {
            val gb = totalSizeBytes / (1024 * 1024 * 1024)
            val mb = (totalSizeBytes % (1024 * 1024 * 1024)) / (1024 * 1024)
            return if (gb > 0) "${gb}GB ${mb}MB" else "${mb}MB"
        }
    
    val totalDurationPretty: String
        get() {
            val hours = totalDurationSeconds / 3600
            val minutes = (totalDurationSeconds % 3600) / 60
            val seconds = totalDurationSeconds % 60
            return if (hours > 0) "${hours}h ${minutes}m ${seconds}s" else "${minutes}m ${seconds}s"
        }
}

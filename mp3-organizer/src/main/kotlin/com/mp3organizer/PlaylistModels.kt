package com.mp3organizer

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistTrack(
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int? = null
)

@Serializable
data class Playlist(
    val tracks: List<PlaylistTrack>,
    val description: String,
    val generatedAt: Long = System.currentTimeMillis()
)

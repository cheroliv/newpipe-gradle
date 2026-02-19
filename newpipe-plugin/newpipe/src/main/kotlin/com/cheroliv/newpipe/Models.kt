package com.cheroliv.newpipe

data class Selection(
    val artistes: List<Artist> = emptyList()
) {
    data class Artist(
        val name: String,
        val tunes: List<String> = emptyList(),
        val playlists: List<String> = emptyList()
    )
}
package com.crowstar.deeztrackermobile.data.model

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val tracks: List<Track> = emptyList()
)

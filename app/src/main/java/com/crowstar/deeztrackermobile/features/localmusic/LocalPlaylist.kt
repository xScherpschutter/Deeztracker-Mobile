package com.crowstar.deeztrackermobile.features.localmusic

data class LocalPlaylist(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val trackIds: List<Long> = emptyList()
)

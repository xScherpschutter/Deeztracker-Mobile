package com.crowstar.deeztrackermobile.features.player

import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack

data class PlayerState(
    val currentTrack: LocalTrack? = null,
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val volume: Float = 1.0f,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playingSource: String = "Local Library",
    val isCurrentTrackFavorite: Boolean = false,
    val lyrics: List<com.crowstar.deeztrackermobile.features.lyrics.LrcLine> = emptyList(),
    val currentLyricIndex: Int = -1,
    val isLoadingLyrics: Boolean = false
)

enum class RepeatMode {
    OFF, ONE, ALL
}

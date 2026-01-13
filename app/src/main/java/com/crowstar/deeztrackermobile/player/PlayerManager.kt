package com.crowstar.deeztrackermobile.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.crowstar.deeztrackermobile.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerManager(context: Context) {

    private val _player = ExoPlayer.Builder(context).build()
    val player: Player get() = _player

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        _player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Logic to update current track based on media item can go here
                // For now, we manually update it when playing
            }
        })
    }

    fun playTrack(track: Track) {
        _currentTrack.value = track
        _player.setMediaItem(track.toMediaItem())
        _player.prepare()
        _player.play()
    }

    fun pause() {
        _player.pause()
    }

    fun resume() {
        _player.play()
    }

    fun release() {
        _player.release()
    }
}

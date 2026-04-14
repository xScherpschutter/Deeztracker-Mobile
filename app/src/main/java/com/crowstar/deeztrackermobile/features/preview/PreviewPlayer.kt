package com.crowstar.deeztrackermobile.features.preview

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreviewPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pollJob: Job? = null

    private val _playingUrl = MutableStateFlow<String?>(null)
    val playingUrl: StateFlow<String?> = _playingUrl.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private fun ensurePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(context).build()
        }
    }

    fun toggle(url: String) {
        ensurePlayer()
        val p = player ?: return

        if (_playingUrl.value == url && p.isPlaying) {
            stop()
            return
        }

        stopPolling()
        p.stop()
        p.clearMediaItems()
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.play()
        _playingUrl.value = url
        _positionMs.value = 0L

        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startPolling()
                } else {
                    stopPolling()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    _positionMs.value = 0L
                    _playingUrl.value = null
                    stopPolling()
                }
            }
        })
    }

    fun stop() {
        stopPolling()
        player?.stop()
        _positionMs.value = 0L
        _playingUrl.value = null
    }

    fun release() {
        stop()
        player?.release()
        player = null
    }

    private fun startPolling() {
        stopPolling()
        pollJob = scope.launch {
            while (true) {
                _positionMs.value = player?.currentPosition ?: 0L
                delay(100)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }
}

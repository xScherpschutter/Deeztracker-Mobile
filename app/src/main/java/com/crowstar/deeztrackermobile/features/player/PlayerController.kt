package com.crowstar.deeztrackermobile.features.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylist
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutionException

class PlayerController(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: PlayerController? = null

        fun getInstance(context: Context): PlayerController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayerController(context).also { INSTANCE = it }
            }
        }
    }

    val playlistRepository = LocalPlaylistRepository(context)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var currentPlaylist: List<LocalTrack> = emptyList()

    init {
        initializeController()
        // Initialize Playlists and Observe Favorites
        kotlinx.coroutines.GlobalScope.launch {
            playlistRepository.loadPlaylists()
            playlistRepository.playlists.collect { playlists ->
                 checkFavoriteStatus()
            }
        }
    }


    private fun initializeController() {
        Log.d("DeezTracker", "Initializing MediaController")
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                Log.d("DeezTracker", "MediaController connected")
                mediaController?.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateState()
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateState()
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateState()
                        // Sync current track from playlist if possible
                        syncCurrentTrack()
                    }
                    override fun onRepeatModeChanged(repeatMode: Int) {
                        updateState()
                    }
                    override fun onEvents(player: Player, events: Player.Events) {
                        updateState()
                    }
                })
                updateState()
            } catch (e: ExecutionException) {
                Log.e("DeezTracker", "Failed to connect to MediaController", e)
            } catch (e: InterruptedException) {
                Log.e("DeezTracker", "Failed to connect to MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun syncCurrentTrack() {
        val player = mediaController ?: return
        val index = player.currentMediaItemIndex
        if (index in currentPlaylist.indices) {
            val track = currentPlaylist[index]
            _playerState.update { it.copy(currentTrack = track) }
            checkFavoriteStatus()
        }
    }

    private fun checkFavoriteStatus() {
         val track = _playerState.value.currentTrack ?: return
         kotlinx.coroutines.GlobalScope.launch {
             val isFav = playlistRepository.isFavorite(track.id)
             _playerState.update { it.copy(isCurrentTrackFavorite = isFav) }
         }
    }

    fun playTrack(track: LocalTrack, playlist: List<LocalTrack>, source: String = "Local Library") {
        Log.d("DeezTracker", "PlayerController.playTrack called for: ${track.title} from $source")
        val player = mediaController
        if (player == null) {
            Log.e("DeezTracker", "MediaController is null, attempting re-init")
            initializeController()
            return // Or queue command
        }
        
        currentPlaylist = playlist
        
        val startIndex = playlist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        Log.d("DeezTracker", "Starting playback at index: $startIndex")
        
        val mediaItems = playlist.map { localTrack ->
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(localTrack.filePath)))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(localTrack.title)
                        .setArtist(localTrack.artist)
                        .setAlbumTitle(localTrack.album)
                        .setArtworkUri(localTrack.albumArtUri?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
        
        _playerState.update { it.copy(currentTrack = track, isPlaying = true, playingSource = source) }
    }

    fun togglePlayPause() {
        val player = mediaController ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun next() {
        mediaController?.seekToNext()
    }

    fun previous() {
        mediaController?.seekToPrevious()
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun setShuffle(enabled: Boolean) {
        mediaController?.shuffleModeEnabled = enabled
        updateState()
    }

    fun toggleRepeatMode() {
        val player = mediaController ?: return
        val newMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = newMode
        updateState()
        player.repeatMode = newMode
        updateState()
    }

    fun toggleFavorite() {
        val track = _playerState.value.currentTrack ?: return
        kotlinx.coroutines.GlobalScope.launch {
            playlistRepository.toggleFavorite(track.id)
            // checkFavoriteStatus called via flow collection, but we can update optimistically
            val isFav = playlistRepository.isFavorite(track.id)
            _playerState.update { it.copy(isCurrentTrackFavorite = isFav) }
        }
    }


    private fun updateState() {
        val player = mediaController ?: return
        
        val appRepeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }

        _playerState.update { 
            it.copy(
                isPlaying = player.isPlaying,
                duration = player.duration.coerceAtLeast(0L),
                currentPosition = player.currentPosition,
                isShuffleEnabled = player.shuffleModeEnabled,
                repeatMode = appRepeatMode
            )
        }
    }
}

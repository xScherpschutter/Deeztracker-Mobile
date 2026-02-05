package com.crowstar.deeztrackermobile.features.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylist
import androidx.media3.session.SessionCommand
import android.os.Bundle
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
                INSTANCE ?: PlayerController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val playlistRepository = LocalPlaylistRepository(context)
    private val lyricsRepository = com.crowstar.deeztrackermobile.features.lyrics.LyricsRepository(context)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var currentPlaylist: List<LocalTrack> = emptyList()
    private var positionUpdateJob: kotlinx.coroutines.Job? = null
    private var lyricsJob: kotlinx.coroutines.Job? = null

    init {
        initializeController()
        // Initialize Playlists and Observe Favorites
        kotlinx.coroutines.GlobalScope.launch {
            playlistRepository.loadPlaylists()
            playlistRepository.playlists.collect { _ ->
                 checkFavoriteStatus()
            }
        }
    }


    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateState()
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateState()
                        if (isPlaying) {
                            startPositionUpdates()
                        } else {
                            stopPositionUpdates()
                        }
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateState()
                        // Sync current track from playlist if possible
                        syncCurrentTrack(mediaItem)
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
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun syncCurrentTrack(targetMediaItem: MediaItem? = null) {
        val player = mediaController ?: return
        val itemToSync = targetMediaItem ?: player.currentMediaItem ?: return
        val mediaId = itemToSync.mediaId.toLongOrNull() ?: return
        
        val track = currentPlaylist.find { it.id == mediaId }
        
        if (track != null) {
            // Only update and fetch if the track has actually changed or lyrics are missing
            if (_playerState.value.currentTrack?.id != track.id || _playerState.value.lyrics.isEmpty()) {
                 _playerState.update { it.copy(currentTrack = track) }
                 checkFavoriteStatus()
                 fetchLyrics(track)
            }
        }
    }

    private var fetchingForTrackId: Long? = null

    private fun fetchLyrics(track: LocalTrack) {
        // If already fetching for this track, do nothing
        if (fetchingForTrackId == track.id && lyricsJob?.isActive == true) {
            return
        }

        lyricsJob?.cancel()
        fetchingForTrackId = track.id
        
        _playerState.update { it.copy(lyrics = emptyList(), currentLyricIndex = -1, isLoadingLyrics = true) }
        
        lyricsJob = kotlinx.coroutines.GlobalScope.launch {
            try {
                val lrcContent = lyricsRepository.getLyrics(track)
                if (lrcContent != null) {
                    android.util.Log.d("PlayerController", "Lyrics content length: ${lrcContent.length}")
                    val parsedLyrics = com.crowstar.deeztrackermobile.features.lyrics.LrcParser.parse(lrcContent)
                    android.util.Log.d("PlayerController", "Parsed lyrics size: ${parsedLyrics.size}")
                    
                    _playerState.update { it.copy(lyrics = parsedLyrics, isLoadingLyrics = false) }
                    // Force state update to calculate initial index immediately
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        updateState()
                    }
                } else {
                    // No lyrics found, stop loading
                    _playerState.update { it.copy(isLoadingLyrics = false) }
                }
            } catch (e: Exception) {
                // If cancelled or failed
                if (e !is kotlinx.coroutines.CancellationException) {
                     e.printStackTrace()
                     _playerState.update { it.copy(isLoadingLyrics = false) }
                }
            } finally {
                if (fetchingForTrackId == track.id) {
                    fetchingForTrackId = null
                }
            }
        }
    }

    private fun checkFavoriteStatus() {
         val track = _playerState.value.currentTrack ?: return
         kotlinx.coroutines.GlobalScope.launch {
             val isFav = playlistRepository.isFavorite(track.id)
             _playerState.update { it.copy(isCurrentTrackFavorite = isFav) }
         }
    }

    fun playTrack(track: LocalTrack, playlist: List<LocalTrack>, source: String? = null) {
        val resolvedSource = source ?: context.getString(com.crowstar.deeztrackermobile.R.string.local_music_title)
        val player = mediaController
        if (player == null) {
            initializeController()
            return // Or queue command
        }
        
        currentPlaylist = playlist
        
        val startIndex = playlist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        
        val mediaItems = playlist.map { localTrack ->
        
            val albumArt = localTrack.albumArtUri?.trim()
            val artworkUri = if (!albumArt.isNullOrEmpty()) {
                Uri.parse(albumArt)
            } else {
                Uri.parse("android.resource://${context.packageName}/${com.crowstar.deeztrackermobile.R.drawable.ic_app_icon}")
            }
            
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(localTrack.filePath)))
                .setMediaId(localTrack.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(localTrack.title)
                        .setArtist(localTrack.artist)
                        .setAlbumTitle(localTrack.album)
                        .setArtworkUri(artworkUri)
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
        
        // Initial fetch for the starting track
        fetchLyrics(track)
        _playerState.update { it.copy(currentTrack = track, isPlaying = true, playingSource = resolvedSource) }
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


    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                while (true) {
                    updateState()
                    kotlinx.coroutines.delay(100)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job was cancelled, exit gracefully
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updateState() {
        val player = mediaController ?: return
        
        val appRepeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }

            
        val currentPos = player.currentPosition
        val lyrics = _playerState.value.lyrics
        val lyricIndex = com.crowstar.deeztrackermobile.features.lyrics.LrcParser.getActiveLineIndex(lyrics, currentPos)

        _playerState.update { 
            it.copy(
                isPlaying = player.isPlaying,
                duration = player.duration.coerceAtLeast(0L),
                currentPosition = currentPos,
                isShuffleEnabled = player.shuffleModeEnabled,
                repeatMode = appRepeatMode,
                volume = player.volume, // Sync volume from player if changed elsewhere
                currentLyricIndex = lyricIndex
            )
        }
    }

    fun setVolume(volume: Float) {
        val player = mediaController ?: return
        val command = SessionCommand(MusicService.CMD_SET_VOLUME, Bundle.EMPTY) // Command definition, extras usually empty
        val args = Bundle().apply {
             putFloat(MusicService.KEY_VOLUME, volume)
        }
        player.sendCustomCommand(command, args)
        _playerState.update { it.copy(volume = volume) }
    }

    /**
     * Stop playback, clear queue, and release controller.
     * Use this when logging out to silence the app.
     */
    fun stop() {
        val player = mediaController ?: return
        player.stop()
        player.clearMediaItems()
        player.release() // Unbinds and should eventually kill service if no other clients
        mediaController = null
        controllerFuture = null
        stopPositionUpdates()
        _playerState.update { PlayerState() } // Reset state
    }
}

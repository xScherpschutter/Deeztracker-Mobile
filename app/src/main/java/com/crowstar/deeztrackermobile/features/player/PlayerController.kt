package com.crowstar.deeztrackermobile.features.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import com.crowstar.deeztrackermobile.features.lyrics.LyricsRepository
import com.crowstar.deeztrackermobile.features.lyrics.LrcParser
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    val playlistRepository: LocalPlaylistRepository,
    private val lyricsRepository: LyricsRepository
) {

    private val TAG = "PlayerController"
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<LocalTrack>>(emptyList())
    val currentQueue: StateFlow<List<LocalTrack>> = _currentQueue.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var positionUpdateJob: kotlinx.coroutines.Job? = null
    private var lyricsJob: kotlinx.coroutines.Job? = null

    init {
        initializeController()
        controllerScope.launch {
            playlistRepository.loadPlaylists()
            playlistRepository.playlists.collect {
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun syncCurrentTrack(targetMediaItem: MediaItem? = null) {
        val player = mediaController ?: return
        val itemToSync = targetMediaItem ?: player.currentMediaItem ?: return
        val mediaId = itemToSync.mediaId.toLongOrNull() ?: return
        
        val track = _currentQueue.value.find { it.id == mediaId }
        
        if (track != null) {
            if (_playerState.value.currentTrack?.id != track.id || _playerState.value.lyrics.isEmpty()) {
                 _playerState.update { it.copy(currentTrack = track) }
                 checkFavoriteStatus()
                 fetchLyrics(track)
            }
        }
    }

    private var fetchingForTrackId: Long? = null

    private fun fetchLyrics(track: LocalTrack) {
        if (fetchingForTrackId == track.id && lyricsJob?.isActive == true) {
            return
        }

        lyricsJob?.cancel()
        fetchingForTrackId = track.id
        
        _playerState.update { it.copy(lyrics = emptyList(), currentLyricIndex = -1, isLoadingLyrics = true) }
        
        lyricsJob = controllerScope.launch {
            try {
                val lrcContent = withContext(Dispatchers.IO) { lyricsRepository.getLyrics(track) }
                if (lrcContent != null) {
                    val parsedLyrics = LrcParser.parse(lrcContent)
                    _playerState.update { it.copy(lyrics = parsedLyrics, isLoadingLyrics = false) }
                    updateState()
                } else {
                    _playerState.update { it.copy(isLoadingLyrics = false) }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
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
         controllerScope.launch {
             val isFav = withContext(Dispatchers.IO) { playlistRepository.isFavorite(track.id) }
             _playerState.update { it.copy(isCurrentTrackFavorite = isFav) }
         }
    }

    fun playTrack(track: LocalTrack, playlist: List<LocalTrack>, source: String? = null) {
        val resolvedSource = source ?: context.getString(com.crowstar.deeztrackermobile.R.string.local_music_title)
        val player = mediaController
        if (player == null) {
            initializeController()
            return
        }
        
        _currentQueue.value = playlist
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
    }

    fun toggleFavorite() {
        val track = _playerState.value.currentTrack ?: return
        controllerScope.launch {
            withContext(Dispatchers.IO) { playlistRepository.toggleFavorite(track.id) }
            val isFav = withContext(Dispatchers.IO) { playlistRepository.isFavorite(track.id) }
            _playerState.update { it.copy(isCurrentTrackFavorite = isFav) }
        }
    }

    /**
     * Move a track within the queue
     */
    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val player = mediaController ?: return
        if (fromIndex !in 0 until _currentQueue.value.size || toIndex !in 0 until _currentQueue.value.size) return

        player.moveMediaItem(fromIndex, toIndex)
        
        // Update internal queue flow
        val updatedQueue = _currentQueue.value.toMutableList()
        val item = updatedQueue.removeAt(fromIndex)
        updatedQueue.add(toIndex, item)
        _currentQueue.value = updatedQueue
    }

    /**
     * Remove a track from the queue
     */
    fun removeTrack(index: Int) {
        val player = mediaController ?: return
        if (index !in 0 until _currentQueue.value.size) return

        player.removeMediaItem(index)
        
        // Update internal queue flow
        val updatedQueue = _currentQueue.value.toMutableList()
        updatedQueue.removeAt(index)
        _currentQueue.value = updatedQueue
    }

    /**
     * Jump to a specific track in the queue by its index
     */
    fun seekToQueueIndex(index: Int) {
        val player = mediaController ?: return
        if (index !in 0 until _currentQueue.value.size) return
        
        player.seekToDefaultPosition(index)
        player.play()
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = controllerScope.launch {
            try {
                while (true) {
                    updateState()
                    kotlinx.coroutines.delay(100)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
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
        val lyricIndex = LrcParser.getActiveLineIndex(lyrics, currentPos)

        _playerState.update { 
            it.copy(
                isPlaying = player.isPlaying,
                duration = player.duration.coerceAtLeast(0L),
                currentPosition = currentPos,
                isShuffleEnabled = player.shuffleModeEnabled,
                repeatMode = appRepeatMode,
                volume = player.volume,
                currentLyricIndex = lyricIndex
            )
        }
    }

    fun setVolume(volume: Float) {
        val player = mediaController ?: return
        val command = SessionCommand(MusicService.CMD_SET_VOLUME, Bundle.EMPTY)
        val args = Bundle().apply {
             putFloat(MusicService.KEY_VOLUME, volume)
        }
        player.sendCustomCommand(command, args)
        _playerState.update { it.copy(volume = volume) }
    }

    fun stop() {
        val player = mediaController ?: return
        player.stop()
        player.clearMediaItems()
        player.release()
        mediaController = null
        controllerFuture = null
        stopPositionUpdates()
        _playerState.update { PlayerState() }
        _currentQueue.value = emptyList()
    }
}

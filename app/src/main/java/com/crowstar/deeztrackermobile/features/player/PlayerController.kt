package com.crowstar.deeztrackermobile.features.player

import com.crowstar.deeztrackermobile.features.localmusic.toPlaylistTrack
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton

import com.crowstar.deeztrackermobile.features.deezer.DeezerRepository
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    val playlistRepository: LocalPlaylistRepository,
    private val lyricsRepository: LyricsRepository,
    private val deezerRepository: DeezerRepository,
    private val downloadManager: DownloadManager,
    private val localMusicRepository: LocalMusicRepository
) {

    private val TAG = "PlayerController"
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<LocalTrack>>(emptyList())
    val currentQueue: StateFlow<List<LocalTrack>> = _currentQueue.asStateFlow()

    private var originalQueue: List<LocalTrack>? = null

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

    private suspend fun createMediaItems(tracks: List<LocalTrack>): List<MediaItem> {
        val allLocalTracks = localMusicRepository.getAllTracks()
        val localTracksMap = allLocalTracks.associateBy { 
            generateTrackKey(it.title, it.artist)
        }
        
        return tracks.map { track ->
            val artworkUri = if (!track.albumArtUri.isNullOrEmpty()) {
                Uri.parse(track.albumArtUri)
            } else {
                Uri.parse("android.resource://${context.packageName}/${com.crowstar.deeztrackermobile.R.drawable.ic_app_icon}")
            }
            
            // Local-First Logic: Check if track is already downloaded
            val trackKey = generateTrackKey(track.title, track.artist)
            val localVersion = if (track.isStreaming) localTracksMap[trackKey] else null

            val finalUri = when {
                localVersion != null -> Uri.fromFile(File(localVersion.filePath))
                track.isStreaming -> Uri.parse("rusteer://${track.id}")
                else -> Uri.fromFile(File(track.filePath))
            }
            
            MediaItem.Builder()
                .setUri(finalUri)
                .setMediaId(track.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(artworkUri)
                        .build()
                )
                .build()
        }
    }

    private fun generateTrackKey(title: String, artist: String): String {
        val t = title.lowercase().replace(Regex("[^a-z0-9]"), "")
        val a = artist.lowercase().replace(Regex("[^a-z0-9]"), "")
        return "$t|$a"
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
                        syncCurrentTrack(mediaItem, reason)
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

    private fun syncCurrentTrack(targetMediaItem: MediaItem? = null, reason: Int = -1) {
        val player = mediaController ?: return
        val itemToSync = targetMediaItem ?: player.currentMediaItem ?: return
        val mediaId = itemToSync.mediaId.toLongOrNull() ?: return
        
        val currentTrack = _playerState.value.currentTrack
        
        // Skip sync if the transition was caused by playlist metadata changes BUT the mediaId is the same.
        // This prevents flickering/re-loading when shuffling or moving items.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaId == currentTrack?.id) {
            Log.d(TAG, "syncCurrentTrack: Redundant sync ignored for $mediaId")
            return
        }

        val queue = _currentQueue.value
        val track = queue.find { it.id == mediaId }
        
        Log.d(TAG, "syncCurrentTrack: mediaId=$mediaId found=${track != null} queueSize=${queue.size} reason=$reason")

        if (track != null) {
            if (currentTrack?.id != track.id || _playerState.value.lyrics.isEmpty()) {
                 _playerState.update { it.copy(currentTrack = track) }
                 checkFavoriteStatus()
                 fetchLyrics(track)
            }
        } else {
             Log.w(TAG, "syncCurrentTrack: Track NOT found in queue for mediaId $mediaId")
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

    fun playStream(track: uniffi.rusteer.Track, source: String? = null) {
        val localTrack = LocalTrack(
            id = track.id.toLongOrNull() ?: 0L,
            title = track.title,
            artist = track.artist,
            album = track.album,
            albumId = 0,
            duration = 0,
            filePath = "",
            size = 0,
            mimeType = "audio/mpeg",
            dateAdded = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
            albumArtUri = track.coverUrl,
            isStreaming = true
        )
        playTrack(localTrack, listOf(localTrack), source ?: "Deezer Streaming")
    }

    fun playDeezerTrack(track: com.crowstar.deeztrackermobile.features.deezer.Track, source: String? = null) {
        val localTrack = track.toLocalTrack()
        playTrack(localTrack, listOf(localTrack), source ?: "Deezer Search")
    }

    fun playDeezerTrackWithRadio(track: com.crowstar.deeztrackermobile.features.deezer.Track) {
        controllerScope.launch {
            try {
                val currentTrack = track.toLocalTrack()
                val radioResponse = deezerRepository.getArtistRadio(track.artist?.id ?: 0L)
                val recommendations = radioResponse.data
                    .filter { it.id != track.id }
                    .map { it.toLocalTrack() }
                
                playTrack(currentTrack, listOf(currentTrack) + recommendations, "Deezer Radio")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load radio", e)
                playDeezerTrack(track)
            }
        }
    }

    fun playDeezerAlbum(albumId: Long, albumTitle: String, albumArt: String? = null, startIndex: Int = 0) {
        controllerScope.launch {
            try {
                val tracks = deezerRepository.getAlbumTracks(albumId).data.map { 
                    it.toLocalTrack(backupAlbumTitle = albumTitle, backupAlbumId = albumId, backupAlbumArt = albumArt) 
                }
                if (tracks.isNotEmpty()) {
                    val startTrack = if (startIndex in tracks.indices) tracks[startIndex] else tracks[0]
                    playTrack(startTrack, tracks, albumTitle)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load album tracks", e)
            }
        }
    }

    fun playDeezerPlaylist(playlistId: Long, playlistTitle: String, playlistArt: String? = null, startIndex: Int = 0) {
        controllerScope.launch {
            try {
                val tracks = deezerRepository.getPlaylistTracks(playlistId).data
                    .filter { it.id > 0 } // Filter corrupt/invalid IDs
                    .map { it.toLocalTrack(backupAlbumArt = it.album?.coverBig ?: it.album?.coverMedium ?: playlistArt) }
                if (tracks.isNotEmpty()) {
                    val startTrack = if (startIndex in tracks.indices) tracks[startIndex] else tracks[0]
                    playTrack(startTrack, tracks, playlistTitle)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load playlist tracks", e)
            }
        }
    }

    fun playDeezerArtist(artistId: Long, artistName: String) {
        controllerScope.launch {
            try {
                val tracks = deezerRepository.getArtistTopTracks(artistId, 50).data.map { it.toLocalTrack() }
                if (tracks.isNotEmpty()) {
                    playTrack(tracks[0], tracks, "$artistName Top Tracks")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load artist tracks", e)
            }
        }
    }

    private fun com.crowstar.deeztrackermobile.features.deezer.Track.toLocalTrack(
        backupAlbumTitle: String? = null,
        backupAlbumId: Long? = null,
        backupAlbumArt: String? = null
    ): LocalTrack {
        return LocalTrack(
            id = this.id,
            title = this.title,
            artist = this.artist?.name ?: "Unknown Artist",
            album = this.album?.title ?: backupAlbumTitle ?: "Unknown Album",
            albumId = this.album?.id ?: backupAlbumId ?: 0L,
            duration = (this.duration ?: 0).toLong() * 1000,
            filePath = "",
            size = 0,
            mimeType = "audio/mpeg",
            dateAdded = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
            albumArtUri = this.album?.coverBig ?: this.album?.coverMedium ?: backupAlbumArt,
            isStreaming = true
        )
    }

    fun playTrack(track: LocalTrack, playlist: List<LocalTrack>, source: String? = null) {
        controllerScope.launch {
            val resolvedSource = source ?: context.getString(com.crowstar.deeztrackermobile.R.string.local_music_title)
            val player = mediaController
            if (player == null) {
                initializeController()
                return@launch
            }
            
            var finalPlaylist = playlist
            var startIndex = playlist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

            if (_playerState.value.isShuffleEnabled) {
                originalQueue = playlist.toList()
                val toShuffle = playlist.toMutableList()
                if (startIndex in toShuffle.indices) {
                    toShuffle.removeAt(startIndex)
                }
                toShuffle.shuffle()
                finalPlaylist = listOf(track) + toShuffle
                startIndex = 0
            } else {
                originalQueue = null
            }

            _currentQueue.value = finalPlaylist
            val mediaItems = createMediaItems(finalPlaylist)

            player.setMediaItems(mediaItems, startIndex, 0L)
            player.prepare()
            player.play()
            
            fetchLyrics(track)
            _playerState.update { it.copy(currentTrack = track, isPlaying = true, playingSource = resolvedSource) }
        }
    }

    fun addToQueue(track: LocalTrack, source: String? = null) {
        val player = mediaController ?: run {
            Log.w(TAG, "Cannot add to queue: MediaController not initialized")
            initializeController()
            return
        }
        
        // Prevent duplicates to avoid crashes in UI lists with unique keys
        if (_currentQueue.value.any { it.id == track.id }) {
            Log.d(TAG, "Track already in queue: ${track.title}")
            return
        }

        controllerScope.launch {
            val mediaItems = createMediaItems(listOf(track))
            val mediaItem = mediaItems.firstOrNull() ?: return@launch
            
            // Check if our local queue is empty, regardless of what's in the ExoPlayer
            // (MusicService might have restored a last-played track, but we want to override it 
            // if the user manually adds something to the queue for the first time).
            val wasEmpty = _currentQueue.value.isEmpty()
            
            // Update internal state flows
            val updatedQueue = _currentQueue.value.toMutableList()
            updatedQueue.add(track)
            _currentQueue.value = updatedQueue
            
            // Update original queue for shuffle logic 
            originalQueue?.let { original ->
                val newOriginal = original.toMutableList()
                newOriginal.add(track)
                originalQueue = newOriginal
            }

            if (wasEmpty) {
                val resolvedSource = source ?: context.getString(com.crowstar.deeztrackermobile.R.string.local_music_title)
                
                // If it was the first manual add, we use setMediaItem to CLEAR any restored track
                // and start fresh with the user's choice.
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
                player.play()
                
                _playerState.update { 
                    it.copy(
                        currentTrack = track, 
                        isPlaying = true,
                        playingSource = resolvedSource
                    ) 
                }
                checkFavoriteStatus()
                fetchLyrics(track)
            } else {
                // Already has a manual queue, just append
                player.addMediaItem(mediaItem)
            }
        }
    }

    fun addToQueue(
        track: com.crowstar.deeztrackermobile.features.deezer.Track,
        source: String? = null,
        backupAlbumArt: String? = null
    ) {
        addToQueue(
            track.toLocalTrack(backupAlbumTitle = source, backupAlbumArt = backupAlbumArt),
            source
        )
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

    private val shuffleMutex = Mutex()

    fun setShuffle(enabled: Boolean) {
        val player = mediaController ?: return
        if (_currentQueue.value.isEmpty()) return

        controllerScope.launch {
            shuffleMutex.withLock {
                val currentTrackId = player.currentMediaItem?.mediaId ?: return@withLock
                val currentIndex = player.currentMediaItemIndex
                val currentQueueList = _currentQueue.value
                
                Log.d(TAG, "setShuffle: enabled=$enabled currentIndex=$currentIndex currentTrackId=$currentTrackId")

                if (enabled) {
                    // Enable shuffle
                    if (originalQueue == null) {
                        originalQueue = currentQueueList.toList()
                    }
                    
                    val targetQueue = currentQueueList.toMutableList()
                    val playingTrack = targetQueue.find { it.id.toString() == currentTrackId }
                    
                    if (playingTrack != null) {
                        // 1. Move current playing track to top gaplessly
                        if (currentIndex != 0) {
                            player.moveMediaItem(currentIndex, 0)
                        }
                        
                        // 2. Shuffle others
                        targetQueue.remove(playingTrack)
                        targetQueue.shuffle()
                        val finalQueue = listOf(playingTrack) + targetQueue
                        
                        // 3. Update internal queue FIRST so syncCurrentTrack finds it
                        _currentQueue.value = finalQueue
                        
                        // 4. Create and replace items after index 0
                        val shuffledItems = createMediaItems(targetQueue)
                        if (shuffledItems.isNotEmpty()) {
                             player.replaceMediaItems(1, player.mediaItemCount, shuffledItems)
                        }
                    }
                } else {
                    // Disable shuffle
                    originalQueue?.let { original ->
                        val targetIndexInOriginal = original.indexOfFirst { it.id.toString() == currentTrackId }.coerceAtLeast(0)
                        Log.d(TAG, "setShuffle(disabled): restoring original order. targetIndex=$targetIndexInOriginal")

                        // 1. Move back to original position
                        if (currentIndex != targetIndexInOriginal) {
                            player.moveMediaItem(currentIndex, targetIndexInOriginal)
                        }
                        
                        // 2. Update internal queue
                        _currentQueue.value = original
                        
                        // 3. Restore ranges before and after
                        val itemsBefore = createMediaItems(original.subList(0, targetIndexInOriginal))
                        val itemsAfter = createMediaItems(original.subList(targetIndexInOriginal + 1, original.size))
                        
                        if (targetIndexInOriginal > 0) {
                             player.replaceMediaItems(0, targetIndexInOriginal, itemsBefore)
                        }
                        if (targetIndexInOriginal < original.size - 1) {
                             player.replaceMediaItems(targetIndexInOriginal + 1, player.mediaItemCount, itemsAfter)
                        }
                    }
                    originalQueue = null
                }
                
                _playerState.update { it.copy(isShuffleEnabled = enabled) }
                updateState()
            }
        }
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
            withContext(Dispatchers.IO) { playlistRepository.toggleFavorite(track.toPlaylistTrack()) }
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

        val removedTrack = _currentQueue.value[index]
        player.removeMediaItem(index)
        
        // Update internal queue flow
        val updatedQueue = _currentQueue.value.toMutableList()
        updatedQueue.removeAt(index)
        _currentQueue.value = updatedQueue

        // Update original queue if it exists
        originalQueue = originalQueue?.filter { it.id != removedTrack.id }
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
        // Prevent duration flicker during transitions by keeping old duration if new one is invalid
        val playerDuration = player.duration
        val duration = if (playerDuration > 0) playerDuration else _playerState.value.duration
        
        val lyrics = _playerState.value.lyrics
        val lyricIndex = LrcParser.getActiveLineIndex(lyrics, currentPos)

        _playerState.update { 
            it.copy(
                isPlaying = player.isPlaying,
                duration = duration,
                currentPosition = currentPos,
                // isShuffleEnabled remains as managed manually by setShuffle and playTrack
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

package com.crowstar.deeztrackermobile.ui.screens

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.os.Environment

class DownloadsViewModel(
    private val application: Application,
    private val repository: LocalMusicRepository,
    private val playlistRepository: com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
) : AndroidViewModel(application) {

    private val _tracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val tracks: StateFlow<List<LocalTrack>> = _tracks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _deleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val deleteIntentSender: StateFlow<IntentSender?> = _deleteIntentSender

    private var allDownloadedTracks: List<LocalTrack> = emptyList()
    
    // Track being deleted (to clean from playlists after user confirms deletion on Android 11+)
    private var trackPendingDeletion: Long? = null

    val playlists = playlistRepository.playlists

    init {
        loadDownloadedMusic()
        viewModelScope.launch {
             playlistRepository.loadPlaylists()
        }
    }

    fun loadDownloadedMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Ensure directory exists or get path
                val appDownloadDir = DownloadManager.getInstance(application).downloadDirectory
                val nativeDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                
                // Get absolute path string to match. Query both app specific dir and native downloads
                val pathsToQuery = listOf(appDownloadDir, nativeDownloadsDir)
                
                allDownloadedTracks = repository.getDownloadedTracks(pathsToQuery)
                _tracks.value = allDownloadedTracks
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun addTrackToPlaylist(playlist: com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylist, track: LocalTrack) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlist.id, track.id)
        }
    }

    fun filter(query: String) {
        if (query.isBlank()) {
            _tracks.value = allDownloadedTracks
        } else {
            _tracks.value = allDownloadedTracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
    }

    fun deleteTrack(track: LocalTrack) {
        viewModelScope.launch {
            trackPendingDeletion = track.id
            val intentSender = repository.requestDeleteTrack(track.id)
            if (intentSender != null) {
                _deleteIntentSender.value = intentSender
            } else {
                // Deletion happened immediately (Android 10-), playlists already cleaned by repository
                trackPendingDeletion = null
                onDeleteSuccess()
            }
        }
    }

    fun onDeleteSuccess() {
        viewModelScope.launch {
            // Android 11+ confirmation - clean playlists via repository
            trackPendingDeletion?.let { repository.onTrackDeleted(it) }
            trackPendingDeletion = null
            loadDownloadedMusic() // Reload list
        }
    }

    fun resetDeleteIntentSender() {
        _deleteIntentSender.value = null
    }
}

class DownloadsViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DownloadsViewModel::class.java)) {
            val playlistRepository = com.crowstar.deeztrackermobile.features.player.PlayerController.getInstance(context).playlistRepository
            @Suppress("UNCHECKED_CAST")
            return DownloadsViewModel(
                context.applicationContext as Application,
                LocalMusicRepository(context.contentResolver, playlistRepository),
                playlistRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

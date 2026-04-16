package com.crowstar.deeztrackermobile.ui.downloads

import com.crowstar.deeztrackermobile.features.localmusic.toPlaylistTrack
import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import android.os.Environment
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val application: Application,
    private val repository: LocalMusicRepository,
    private val playlistRepository: LocalPlaylistRepository,
    private val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    private val _tracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val tracks: StateFlow<List<LocalTrack>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _deleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val deleteIntentSender: StateFlow<IntentSender?> = _deleteIntentSender.asStateFlow()

    private var allDownloadedTracks: List<LocalTrack> = emptyList()
    
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
                val appDownloadDir = downloadManager.downloadDirectory
                val nativeDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                
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
            playlistRepository.addTrackToPlaylist(playlist.id, track.toPlaylistTrack())
        }
    }

    fun filter(query: String) {
        // Optimized filtering: runs instantly without isLoading
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
                trackPendingDeletion = null
                onDeleteSuccess()
            }
        }
    }

    fun onDeleteSuccess() {
        viewModelScope.launch {
            trackPendingDeletion = null
            loadDownloadedMusic()
        }
    }

    fun resetDeleteIntentSender() {
        _deleteIntentSender.value = null
    }
}

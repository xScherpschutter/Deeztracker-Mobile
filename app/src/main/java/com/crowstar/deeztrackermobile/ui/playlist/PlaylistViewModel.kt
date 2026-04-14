package com.crowstar.deeztrackermobile.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.deezer.DeezerRepository
import com.crowstar.deeztrackermobile.features.deezer.Playlist
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.preview.PreviewPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: DeezerRepository,
    val downloadManager: DownloadManager,
    private val previewPlayer: PreviewPlayer
) : ViewModel() {
    
    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val downloadState = downloadManager.downloadState
    val downloadRefreshTrigger = downloadManager.downloadRefreshTrigger

    val playingUrl = previewPlayer.playingUrl
    val previewPosition = previewPlayer.positionMs

    fun loadPlaylist(playlistId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load playlist data
                val playlistData = repository.getPlaylist(playlistId)
                _playlist.value = playlistData

                // Load tracks
                val tracksResponse = repository.getPlaylistTracks(playlistId)
                _tracks.value = tracksResponse.data
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startPlaylistDownload(playlistId: Long, title: String) {
        downloadManager.startPlaylistDownload(playlistId, title)
    }

    fun startTrackDownload(trackId: Long, title: String) {
        downloadManager.startTrackDownload(trackId, title)
    }

    fun isTrackDownloaded(title: String, artist: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = downloadManager.isTrackDownloaded(title, artist)
            callback(result)
        }
    }

    fun resetDownloadState() {
        downloadManager.resetState()
    }

    fun togglePreview(url: String) {
        previewPlayer.toggle(url)
    }

    fun stopPreview() {
        previewPlayer.stop()
    }
}

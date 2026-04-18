package com.crowstar.deeztrackermobile.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.deezer.DeezerRepository
import com.crowstar.deeztrackermobile.features.deezer.Album
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.player.PlayerController
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val repository: DeezerRepository,
    val downloadManager: DownloadManager,
    val playerController: PlayerController,
    val playlistRepository: LocalPlaylistRepository
) : ViewModel() {
    
    private val _album = MutableStateFlow<Album?>(null)
    val album: StateFlow<Album?> = _album.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val downloadState = downloadManager.downloadState
    val downloadRefreshTrigger = downloadManager.downloadRefreshTrigger
    val playlists = playlistRepository.playlists

    fun loadAlbum(albumId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load album data
                val albumData = repository.getAlbum(albumId)
                _album.value = albumData

                // Load tracks
                val tracksResponse = repository.getAlbumTracks(albumId)
                _tracks.value = tracksResponse.data
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playAlbum(startIndex: Int = 0) {
        val albumData = _album.value ?: return
        playerController.playDeezerAlbum(albumData.id, albumData.title, albumData.coverXl ?: albumData.coverBig, startIndex)
    }

    fun startAlbumDownload(albumId: Long, title: String) {
        downloadManager.startAlbumDownload(albumId, title)
    }

    fun startTrackDownload(trackId: Long, title: String) {
        downloadManager.startTrackDownload(trackId, title)
    }

    fun isTrackDownloaded(title: String, artist: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = downloadManager.checkIfTrackDownloaded(title, artist)
            callback(result)
        }
    }

    fun resetDownloadState() {
        downloadManager.resetState()
    }
}

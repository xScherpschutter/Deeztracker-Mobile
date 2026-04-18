package com.crowstar.deeztrackermobile.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.deezer.DeezerRepository
import com.crowstar.deeztrackermobile.features.deezer.Album
import com.crowstar.deeztrackermobile.features.deezer.Artist
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
class ArtistViewModel @Inject constructor(
    private val repository: DeezerRepository,
    val downloadManager: DownloadManager,
    val playerController: PlayerController,
    val playlistRepository: LocalPlaylistRepository
) : ViewModel() {
    
    private val _artist = MutableStateFlow<Artist?>(null)
    val artist: StateFlow<Artist?> = _artist.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _topTracks = MutableStateFlow<List<Track>>(emptyList())
    val topTracks: StateFlow<List<Track>> = _topTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val downloadState = downloadManager.downloadState
    val downloadRefreshTrigger = downloadManager.downloadRefreshTrigger
    val playlists = playlistRepository.playlists

    fun loadArtist(artistId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load artist data
                val artistData = repository.getArtist(artistId)
                _artist.value = artistData

                // Load albums
                val albumsResponse = repository.getArtistAlbums(artistId)
                _albums.value = albumsResponse.data

                // Load top tracks
                val tracksResponse = repository.getArtistTopTracks(artistId, 10)
                _topTracks.value = tracksResponse.data
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playArtistTopTracks() {
        val artistData = _artist.value ?: return
        playerController.playDeezerArtist(artistData.id, artistData.name)
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

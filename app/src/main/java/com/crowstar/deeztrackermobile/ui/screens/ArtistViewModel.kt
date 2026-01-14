package com.crowstar.deeztrackermobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.deezer.DeezerRepository
import com.crowstar.deeztrackermobile.features.deezer.Album
import com.crowstar.deeztrackermobile.features.deezer.Artist
import com.crowstar.deeztrackermobile.features.deezer.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ArtistViewModel(
    private val repository: DeezerRepository = DeezerRepository()
) : ViewModel() {
    
    private val _artist = MutableStateFlow<Artist?>(null)
    val artist: StateFlow<Artist?> = _artist

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _topTracks = MutableStateFlow<List<Track>>(emptyList())
    val topTracks: StateFlow<List<Track>> = _topTracks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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
}

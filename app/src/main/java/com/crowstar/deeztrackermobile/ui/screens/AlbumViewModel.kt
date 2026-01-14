package com.crowstar.deeztrackermobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.deezer.DeezerRepository
import com.crowstar.deeztrackermobile.features.deezer.Album
import com.crowstar.deeztrackermobile.features.deezer.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlbumViewModel(
    private val repository: DeezerRepository = DeezerRepository()
) : ViewModel() {
    
    private val _album = MutableStateFlow<Album?>(null)
    val album: StateFlow<Album?> = _album

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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
}

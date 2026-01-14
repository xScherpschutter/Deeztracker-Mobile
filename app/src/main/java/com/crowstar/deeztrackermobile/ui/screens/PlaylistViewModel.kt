package com.crowstar.deeztrackermobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.deezer.DeezerRepository
import com.crowstar.deeztrackermobile.features.deezer.Playlist
import com.crowstar.deeztrackermobile.features.deezer.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val repository: DeezerRepository = DeezerRepository()
) : ViewModel() {
    
    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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
}

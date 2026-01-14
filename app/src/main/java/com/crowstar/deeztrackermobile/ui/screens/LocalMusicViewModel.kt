package com.crowstar.deeztrackermobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.localmusic.LocalAlbum
import com.crowstar.deeztrackermobile.features.localmusic.LocalArtist
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LocalMusicViewModel(
    private val repository: LocalMusicRepository
) : ViewModel() {
    
    private val _tracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val tracks: StateFlow<List<LocalTrack>> = _tracks

    private val _albums = MutableStateFlow<List<LocalAlbum>>(emptyList())
    val albums: StateFlow<List<LocalAlbum>> = _albums

    private val _artists = MutableStateFlow<List<LocalArtist>>(emptyList())
    val artists: StateFlow<List<LocalArtist>> = _artists

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _selectedView = MutableStateFlow(0) // 0 = Tracks, 1 = Albums, 2 = Artists
    val selectedView: StateFlow<Int> = _selectedView

    private val _loadedAlbumTracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val loadedAlbumTracks: StateFlow<List<LocalTrack>> = _loadedAlbumTracks

    private val _loadedArtistTracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val loadedArtistTracks: StateFlow<List<LocalTrack>> = _loadedArtistTracks

    init {
        loadMusic()
    }

    fun loadMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _tracks.value = repository.getAllTracks()
                _albums.value = repository.getAllAlbums()
                _artists.value = repository.getAllArtists()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadTracksForAlbum(albumId: Long) {
         viewModelScope.launch {
             _loadedAlbumTracks.value = repository.getTracksForAlbum(albumId)
         }
    }

    fun loadTracksForArtist(artistName: String) {
         viewModelScope.launch {
             _loadedArtistTracks.value = repository.getTracksForArtist(artistName)
         }
    }

    fun setSelectedView(index: Int) {
        _selectedView.value = index
    }

    fun searchTracks(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _tracks.value = if (query.isBlank()) {
                    repository.getAllTracks()
                } else {
                    repository.searchTracks(query)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

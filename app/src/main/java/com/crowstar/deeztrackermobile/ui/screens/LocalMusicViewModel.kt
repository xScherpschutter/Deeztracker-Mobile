package com.crowstar.deeztrackermobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.localmusic.LocalAlbum
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _selectedView = MutableStateFlow(0) // 0 = Tracks, 1 = Albums
    val selectedView: StateFlow<Int> = _selectedView

    init {
        loadMusic()
    }

    fun loadMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _tracks.value = repository.getAllTracks()
                _albums.value = repository.getAllAlbums()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
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

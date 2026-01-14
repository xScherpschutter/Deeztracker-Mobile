package com.crowstar.deeztrackermobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.localmusic.LocalAlbum
import com.crowstar.deeztrackermobile.features.localmusic.LocalArtist
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylist
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.content.IntentSender

class LocalMusicViewModel(
    private val repository: LocalMusicRepository,
    private val playlistRepository: LocalPlaylistRepository // Inject Playlist Repo
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

    val playlists = playlistRepository.playlists // Expose playlists
    
    private val _deleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val deleteIntentSender: StateFlow<IntentSender?> = _deleteIntentSender


    // Master lists for filtering
    private var allTracks: List<LocalTrack> = emptyList()
    private var allAlbums: List<LocalAlbum> = emptyList()
    private var allArtists: List<LocalArtist> = emptyList()

    init {
        loadMusic()
    }

    fun loadMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Initialize Playlists
                playlistRepository.loadPlaylists()

                // Load all data
                allTracks = repository.getAllTracks()
                allAlbums = repository.getAllAlbums()
                allArtists = repository.getAllArtists()

                // Initial state = all data
                _tracks.value = allTracks
                _albums.value = allAlbums
                _artists.value = allArtists
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
                if (query.isBlank()) {
                    // Reset to full lists
                    _tracks.value = allTracks
                    _albums.value = allAlbums
                    _artists.value = allArtists
                } else {
                    // Filter Tracks
                    _tracks.value = allTracks.filter { 
                        it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
                    }

                    // Filter Albums
                    _albums.value = allAlbums.filter { 
                        it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true)
                    }

                    // Filter Artists
                    _artists.value = allArtists.filter { 
                        it.name.contains(query, ignoreCase = true)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Playlist Operations
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: LocalPlaylist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist.id)
        }
    }

    fun addTrackToPlaylist(playlist: LocalPlaylist, track: LocalTrack) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlist.id, track.id)
        }
    }

    fun toggleFavorite(track: LocalTrack) {
        viewModelScope.launch {
            playlistRepository.toggleFavorite(track.id)
        }
    }


    fun removeTrackFromPlaylist(playlist: LocalPlaylist, track: LocalTrack) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlist.id, track.id)
        }
    }

    fun requestDeleteTrack(track: LocalTrack) {
        viewModelScope.launch {
            val intentSender = repository.requestDeleteTrack(track.id)
            if (intentSender != null) {
                _deleteIntentSender.value = intentSender
            } else {
                // Deletion handled directly or failed, refresh list
                loadMusic()
            }
        }
    }

    fun resetDeleteIntentSender() {
        _deleteIntentSender.value = null
    }

    fun onDeleteSuccess() {
        loadMusic()
    }
}

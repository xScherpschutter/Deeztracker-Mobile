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
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.crowstar.deeztrackermobile.features.player.PlayerController

class LocalMusicViewModel(
    private val repository: LocalMusicRepository,
    private val playlistRepository: LocalPlaylistRepository // Inject Playlist Repo
) : ViewModel() {
    
    private val _tracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val tracks: StateFlow<List<LocalTrack>> = _tracks
    
    // Unfiltered tracks for playlist operations
    private val _unfilteredTracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val unfilteredTracks: StateFlow<List<LocalTrack>> = _unfilteredTracks

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

    private val _totalStorage = MutableStateFlow<Long>(0L)
    val totalStorage: StateFlow<Long> = _totalStorage

    val playlists = playlistRepository.playlists // Expose playlists
    
    private val _deleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val deleteIntentSender: StateFlow<IntentSender?> = _deleteIntentSender
    
    // Track being deleted (to clean from playlists after user confirms deletion on Android 11+)
    private var trackPendingDeletion: Long? = null


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
                _unfilteredTracks.value = allTracks
                _albums.value = allAlbums
                _albums.value = allAlbums
                _artists.value = allArtists
                
                // Load storage info
                val storage = repository.getTotalStorageSpace()
                _totalStorage.value = storage
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

    suspend fun createPlaylistSync(name: String): String {
        return playlistRepository.createPlaylist(name)
    }

    fun addTrackToPlaylistId(playlistId: String, track: LocalTrack) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, track.id)
        }
    }

    fun deletePlaylist(playlist: LocalPlaylist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist.id)
        }
    }

    fun editPlaylist(playlist: LocalPlaylist, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlist.id, newName)
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
            trackPendingDeletion = track.id
            val intentSender = repository.requestDeleteTrack(track.id)
            if (intentSender != null) {
                _deleteIntentSender.value = intentSender
            } else {
                // Deletion handled directly (Android 10-), playlists already cleaned by repository
                trackPendingDeletion = null
                loadMusic()
            }
        }
    }

    fun resetDeleteIntentSender() {
        _deleteIntentSender.value = null
    }

    fun onDeleteSuccess() {
        viewModelScope.launch {
            // Android 11+ confirmation - clean playlists via repository
            trackPendingDeletion?.let { repository.onTrackDeleted(it) }
            trackPendingDeletion = null
            loadMusic()
        }
    }
    class LocalMusicViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LocalMusicViewModel::class.java)) {
                // Use singleton repository from PlayerController to ensure sync
                val playlistRepository = PlayerController.getInstance(context).playlistRepository
                val repository = LocalMusicRepository(context.contentResolver, playlistRepository)
                return LocalMusicViewModel(repository, playlistRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

package com.crowstar.deeztrackermobile.ui.library

import com.crowstar.deeztrackermobile.features.localmusic.toPlaylistTrack
import com.crowstar.deeztrackermobile.features.localmusic.toLocalTrack
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.localmusic.LocalAlbum
import com.crowstar.deeztrackermobile.features.localmusic.LocalArtist
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylist
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import android.content.IntentSender
import javax.inject.Inject

data class PlaylistTrackUiState(
    val track: LocalTrack,
    val isDownloaded: Boolean
)

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    private val repository: LocalMusicRepository,
    private val playlistRepository: LocalPlaylistRepository,
    val downloadManager: com.crowstar.deeztrackermobile.features.download.DownloadManager
) : ViewModel() {
    
    private val _tracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val tracks: StateFlow<List<LocalTrack>> = _tracks.asStateFlow()
    
    private val _unfilteredTracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val unfilteredTracks: StateFlow<List<LocalTrack>> = _unfilteredTracks.asStateFlow()

    private val _albums = MutableStateFlow<List<LocalAlbum>>(emptyList())
    val albums: StateFlow<List<LocalAlbum>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<LocalArtist>>(emptyList())
    val artists: StateFlow<List<LocalArtist>> = _artists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedView = MutableStateFlow(0)
    val selectedView: StateFlow<Int> = _selectedView.asStateFlow()

    private val _loadedAlbumTracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val loadedAlbumTracks: StateFlow<List<LocalTrack>> = _loadedAlbumTracks.asStateFlow()

    private val _loadedArtistTracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val loadedArtistTracks: StateFlow<List<LocalTrack>> = _loadedArtistTracks.asStateFlow()

    private val _totalStorage = MutableStateFlow<Long>(0L)
    val totalStorage: StateFlow<Long> = _totalStorage.asStateFlow()

    val playlists = playlistRepository.playlists
    
    private val _deleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val deleteIntentSender: StateFlow<IntentSender?> = _deleteIntentSender.asStateFlow()
    
    private var trackPendingDeletion: Long? = null

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
                // Parallel loading
                val playlistsJob = launch { playlistRepository.loadPlaylists() }
                val tracksDeferred = async { repository.getAllTracks() }
                val albumsDeferred = async { repository.getAllAlbums() }
                val artistsDeferred = async { repository.getAllArtists() }
                val storageDeferred = async { repository.getTotalStorageSpace() }

                playlistsJob.join()
                allTracks = tracksDeferred.await()
                allAlbums = albumsDeferred.await()
                allArtists = artistsDeferred.await()

                _tracks.value = allTracks
                _unfilteredTracks.value = allTracks
                _albums.value = allAlbums
                _artists.value = allArtists
                _totalStorage.value = storageDeferred.await()
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
        // Optimized search: No isLoading, runs instantly
        if (query.isBlank()) {
            _tracks.value = allTracks
            _albums.value = allAlbums
            _artists.value = allArtists
        } else {
            _tracks.value = allTracks.filter { 
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }

            _albums.value = allAlbums.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.artist.contains(query, ignoreCase = true)
            }

            _artists.value = allArtists.filter { 
                it.name.contains(query, ignoreCase = true)
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    suspend fun createPlaylistSync(name: String): String {
        return playlistRepository.createPlaylist(name)
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
            playlistRepository.addTrackToPlaylist(playlist.id, track.toPlaylistTrack())
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
            trackPendingDeletion = null
            loadMusic()
        }
    }

    fun getPlaylistTracksUiState(playlist: LocalPlaylist, allTracks: List<LocalTrack>): List<PlaylistTrackUiState> {
        return playlist.tracks.map { it.toLocalTrack() }.map { track -> 
            allTracks.find { it.title.lowercase() == track.title.lowercase() && it.artist.lowercase() == track.artist.lowercase() } ?: track 
        }.map { finalTrack ->
            PlaylistTrackUiState(
                track = finalTrack,
                isDownloaded = downloadManager.isTrackDownloadedFast(finalTrack.title, finalTrack.artist ?: "")
            )
        }
    }
}

package com.crowstar.deeztrackermobile.ui.common.selection

import androidx.lifecycle.ViewModel
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class SelectionContext {
    LOCAL,          // Library / Downloads
    LOCAL_PLAYLIST, // Within a local playlist
    REMOTE          // Search / Artist / Album / Remote Playlist
}

sealed class SelectedTrack {
    abstract val id: Long
    abstract val title: String
    abstract val artist: String

    data class Local(
        val track: LocalTrack,
        val originalId: Long? = null
    ) : SelectedTrack() {
        override val id = track.id
        override val title = track.title
        override val artist = track.artist
    }

    data class Remote(
        val track: Track,
        val source: String? = null,
        val backupAlbumArt: String? = null
    ) : SelectedTrack() {
        override val id = track.id
        override val title = track.title ?: ""
        override val artist = track.artist?.name ?: ""
    }
}

@HiltViewModel
class SelectionViewModel @Inject constructor() : ViewModel() {

    private val _selectedTracks = MutableStateFlow<Set<SelectedTrack>>(emptySet())
    val selectedTracks: StateFlow<Set<SelectedTrack>> = _selectedTracks.asStateFlow()

    private val _selectionContext = MutableStateFlow(SelectionContext.LOCAL)
    val selectionContext: StateFlow<SelectionContext> = _selectionContext.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _currentPlaylistId = MutableStateFlow<String?>(null)
    val currentPlaylistId: StateFlow<String?> = _currentPlaylistId.asStateFlow()

    fun enterSelectionMode(context: SelectionContext, initialTrack: SelectedTrack, playlistId: String? = null) {
        _selectionContext.value = context
        _currentPlaylistId.value = playlistId
        _selectedTracks.value = setOf(initialTrack)
        _isSelectionMode.value = true
    }

    fun toggleSelection(track: SelectedTrack) {
        _selectedTracks.update { current ->
            val isAlreadySelected = if (track is SelectedTrack.Local && track.originalId != null) {
                current.any { it is SelectedTrack.Local && it.track.id == track.track.id && it.originalId == track.originalId }
            } else {
                current.any { it.id == track.id }
            }

            if (isAlreadySelected) {
                val next = if (track is SelectedTrack.Local && track.originalId != null) {
                    current.filterNot { it is SelectedTrack.Local && it.track.id == track.track.id && it.originalId == track.originalId }.toSet()
                } else {
                    current.filterNot { it.id == track.id }.toSet()
                }
                
                if (next.isEmpty()) {
                    _isSelectionMode.value = false
                }
                next
            } else {
                current + track
            }
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedTracks.value = emptySet()
    }

    fun selectAll(tracks: List<SelectedTrack>) {
        _selectedTracks.value = tracks.toSet()
    }

    fun isSelected(trackId: Long): Boolean {
        return _selectedTracks.value.any { it.id == trackId }
    }
}

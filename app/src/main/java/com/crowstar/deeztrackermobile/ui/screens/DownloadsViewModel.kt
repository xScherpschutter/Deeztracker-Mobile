package com.crowstar.deeztrackermobile.ui.screens

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.os.Environment

class DownloadsViewModel(
    private val application: Application,
    private val repository: LocalMusicRepository
) : AndroidViewModel(application) {

    private val _tracks = MutableStateFlow<List<LocalTrack>>(emptyList())
    val tracks: StateFlow<List<LocalTrack>> = _tracks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _deleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val deleteIntentSender: StateFlow<IntentSender?> = _deleteIntentSender

    private var allDownloadedTracks: List<LocalTrack> = emptyList()

    init {
        loadDownloadedMusic()
    }

    fun loadDownloadedMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Ensure directory exists or get path
                val appDownloadDir = DownloadManager.getInstance(application).downloadDirectory
                val nativeDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                
                // Get absolute path string to match. Query both app specific dir and native downloads
                val pathsToQuery = listOf(appDownloadDir, nativeDownloadsDir)
                
                allDownloadedTracks = repository.getDownloadedTracks(pathsToQuery)
                _tracks.value = allDownloadedTracks
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filter(query: String) {
        if (query.isBlank()) {
            _tracks.value = allDownloadedTracks
        } else {
            _tracks.value = allDownloadedTracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
    }

    fun deleteTrack(track: LocalTrack) {
        viewModelScope.launch {
            val intentSender = repository.requestDeleteTrack(track.id)
            if (intentSender != null) {
                _deleteIntentSender.value = intentSender
            } else {
                // Deletion happened immediately or failed silently (used in API < 30 or if permission already granted)
                onDeleteSuccess()
            }
        }
    }

    fun onDeleteSuccess() {
        loadDownloadedMusic() // Reload list
    }

    fun resetDeleteIntentSender() {
        _deleteIntentSender.value = null
    }
}

class DownloadsViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DownloadsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DownloadsViewModel(
                context.applicationContext as Application,
                LocalMusicRepository(context.contentResolver)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

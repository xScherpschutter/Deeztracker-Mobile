package com.crowstar.deeztrackermobile.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository

class LocalMusicViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocalMusicViewModel::class.java)) {
            val repository = LocalMusicRepository(context.contentResolver)
            // Use singleton repository from PlayerController to ensure sync
            val playlistRepository = com.crowstar.deeztrackermobile.features.player.PlayerController.getInstance(context).playlistRepository
            return LocalMusicViewModel(repository, playlistRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

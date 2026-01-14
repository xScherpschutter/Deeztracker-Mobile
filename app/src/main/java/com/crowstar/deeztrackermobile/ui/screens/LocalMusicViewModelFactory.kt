package com.crowstar.deeztrackermobile.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository

class LocalMusicViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocalMusicViewModel::class.java)) {
            val repository = LocalMusicRepository(context.contentResolver)
            return LocalMusicViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

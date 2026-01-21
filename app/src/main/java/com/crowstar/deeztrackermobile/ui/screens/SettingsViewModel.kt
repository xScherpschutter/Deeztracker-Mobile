package com.crowstar.deeztrackermobile.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.rusteer.DownloadQuality

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val rustDeezerService = RustDeezerService(context)
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _audioQuality = MutableStateFlow(DownloadQuality.MP3_128)
    val audioQuality: StateFlow<DownloadQuality> = _audioQuality.asStateFlow()

    private val _language = MutableStateFlow("English")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _downloadLocation = MutableStateFlow("MUSIC")
    val downloadLocation: StateFlow<String> = _downloadLocation.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        // Load Audio Quality
        val savedQuality = prefs.getString("audio_quality", "MP3_128")
        _audioQuality.value = when (savedQuality) {
            "MP3_320" -> DownloadQuality.MP3_320
            "FLAC" -> DownloadQuality.FLAC
            else -> DownloadQuality.MP3_128
        }

        // Load Language
        val savedLanguage = prefs.getString("language", "English")
        _language.value = savedLanguage ?: "English"

        // Load Download Location
        val savedLocation = prefs.getString("download_location", "MUSIC")
        _downloadLocation.value = savedLocation ?: "MUSIC"
    }

    fun setAudioQuality(quality: DownloadQuality) {
        _audioQuality.value = quality
        prefs.edit().putString("audio_quality", quality.name).apply()
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        prefs.edit().putString("language", lang).apply()
    }

    fun setDownloadLocation(location: String) {
        _downloadLocation.value = location
        prefs.edit().putString("download_location", location).apply()
    }

    fun logout() {
        rustDeezerService.logout()
        // Navigation should be handled by the UI observing a logout event or callback
    }
}

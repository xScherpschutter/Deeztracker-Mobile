package com.crowstar.deeztrackermobile.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.player.LyricMode
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import com.crowstar.deeztrackermobile.ui.utils.LanguageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.rusteer.DownloadQuality
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val rustDeezerService: RustDeezerService
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _audioQuality = MutableStateFlow(DownloadQuality.MP3_128)
    val audioQuality: StateFlow<DownloadQuality> = _audioQuality.asStateFlow()

    private val _language = MutableStateFlow("English")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _downloadLocation = MutableStateFlow("MUSIC")
    val downloadLocation: StateFlow<String> = _downloadLocation.asStateFlow()

    private val _lyricMode = MutableStateFlow(LyricMode.CLASSIC)
    val lyricMode: StateFlow<LyricMode> = _lyricMode.asStateFlow()

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

        // Load Language - convert code to display name
        val savedLanguageCode = prefs.getString("language", "en") ?: "en"
        _language.value = LanguageHelper.getDisplayName(savedLanguageCode)

        // Load Download Location
        val savedLocation = prefs.getString("download_location", "MUSIC")
        _downloadLocation.value = savedLocation ?: "MUSIC"

        // Load Lyric Mode
        val savedLyricMode = prefs.getString("lyric_mode", LyricMode.CLASSIC.name)
        _lyricMode.value = try {
            LyricMode.valueOf(savedLyricMode ?: LyricMode.CLASSIC.name)
        } catch (e: Exception) {
            LyricMode.CLASSIC
        }
    }

    fun setAudioQuality(quality: DownloadQuality) {
        _audioQuality.value = quality
        prefs.edit().putString("audio_quality", quality.name).apply()
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        // Convert display name to code before saving
        val languageCode = LanguageHelper.getCode(lang)
        prefs.edit().putString("language", languageCode).apply()
    }

    fun setDownloadLocation(location: String) {
        _downloadLocation.value = location
        prefs.edit().putString("download_location", location).apply()
    }

    fun setLyricMode(mode: LyricMode) {
        _lyricMode.value = mode
        prefs.edit().putString("lyric_mode", mode.name).apply()
    }

    fun logout() {
        rustDeezerService.logout()
    }
}

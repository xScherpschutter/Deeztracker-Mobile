package com.crowstar.deeztrackermobile.ui.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_LANGUAGE = "language"

    fun onAttach(context: Context): Context {
        val lang = getPersistedData(context, "en")
        return setLocale(context, lang)
    }

    fun getLanguage(context: Context): String {
        return getPersistedData(context, "en")
    }

    fun setLocale(context: Context, language: String): Context {
        persist(context, language)
        return updateResources(context, language)
    }

    private fun getPersistedData(context: Context, defaultLanguage: String): String {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Map full names back to codes if stored that way, or assume codes are stored
        // SettingsViewModel currently stores "English", "Spanish", "Español"
        // We need to normalize this.
        val saved = preferences.getString(KEY_LANGUAGE, defaultLanguage) ?: defaultLanguage
        return when (saved) {
            "Español", "Spanish", "es" -> "es"
            "English", "en" -> "en"
            else -> "en"
        }
    }

    private fun persist(context: Context, language: String) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences.edit().putString(KEY_LANGUAGE, language).apply()
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }
}

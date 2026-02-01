package com.crowstar.deeztrackermobile.ui.utils

/**
 * Helper object to manage language code to display name mapping
 */
object LanguageHelper {
    
    // Available languages with their codes and display names
    data class Language(val code: String, val displayName: String)
    
    private val languages = listOf(
        Language("en", "English"),
        Language("es", "Spanish")
    )
    
    /**
     * Get display name from language code
     * @param code Language code (e.g., "en", "es")
     * @return Display name (e.g., "English", "Spanish")
     */
    fun getDisplayName(code: String): String {
        return languages.find { it.code == code }?.displayName 
            ?: languages.first().displayName // Default to first language
    }
    
    /**
     * Get language code from display name
     * @param displayName Display name (e.g., "English", "Spanish")
     * @return Language code (e.g., "en", "es")
     */
    fun getCode(displayName: String): String {
        return languages.find { it.displayName == displayName }?.code 
            ?: languages.first().code // Default to first language
    }
    
    /**
     * Get all available display names
     * @return List of display names
     */
    fun getAllDisplayNames(): List<String> {
        return languages.map { it.displayName }
    }
    
    /**
     * Get all available languages
     * @return List of Language objects
     */
    fun getAllLanguages(): List<Language> {
        return languages
    }
}

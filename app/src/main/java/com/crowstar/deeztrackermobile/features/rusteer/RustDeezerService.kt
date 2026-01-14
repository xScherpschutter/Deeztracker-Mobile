package com.crowstar.deeztrackermobile.features.rusteer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rusteer.RusteerService

class RustDeezerService(context: Context) {
    private val service = RusteerService()
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "rusteer_prefs",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_ARL = "arl_token"
    }

    /**
     * Get the saved ARL token from SharedPreferences
     */
    fun getSavedArl(): String? {
        return prefs.getString(KEY_ARL, null)
    }

    /**
     * Clear the saved ARL token
     */
    fun clearArl() {
        prefs.edit().remove(KEY_ARL).apply()
    }

    /**
     * Login with ARL token and save it if successful
     */
    suspend fun login(arl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("RustDeezerService", "Verifying ARL: ${arl.take(10)}...")
                val isValid = service.verifyArl(arl)
                if (isValid) {
                    // Save ARL to SharedPreferences
                    prefs.edit().putString(KEY_ARL, arl).apply()
                    Log.d("RustDeezerService", "ARL is valid and saved, login successful")
                } else {
                    Log.w("RustDeezerService", "ARL is invalid")
                }
                isValid
            } catch (e: Exception) {
                Log.e("RustDeezerService", "Login verification failed", e)
                false
            }
        }
    }

    /**
     * Check if user is logged in (has saved ARL)
     */
    fun isLoggedIn(): Boolean {
        return getSavedArl() != null
    }

    /**
     * Logout - clears the saved ARL
     */
    fun logout() {
        clearArl()
        Log.d("RustDeezerService", "User logged out, ARL cleared")
    }
}

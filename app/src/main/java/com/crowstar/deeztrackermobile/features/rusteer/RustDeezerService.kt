package com.crowstar.deeztrackermobile.features.rusteer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rusteer.BatchDownloadResult
import uniffi.rusteer.DownloadQuality
import uniffi.rusteer.DownloadResult
import uniffi.rusteer.RusteerService
import uniffi.rusteer.Track
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RustDeezerService @Inject constructor(
    @ApplicationContext context: Context
) {
    private val service = RusteerService()
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "rusteer_prefs",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val TAG = "RustDeezerService"
        private const val KEY_ARL = "arl_token"
    }

    fun getSavedArl(): String? {
        return prefs.getString(KEY_ARL, null)
    }

    fun clearArl() {
        prefs.edit().remove(KEY_ARL).apply()
    }

    suspend fun login(arl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Verifying ARL: ${arl.take(10)}...")
                val isValid = service.verifyArl(arl)
                if (isValid) {
                    prefs.edit().putString(KEY_ARL, arl).apply()
                    Log.d(TAG, "ARL is valid and saved, login successful")
                } else {
                    Log.w(TAG, "ARL is invalid")
                }
                isValid
            } catch (e: Exception) {
                Log.e(TAG, "Login verification failed", e)
                false
            }
        }
    }

    fun isLoggedIn(): Boolean {
        return getSavedArl() != null
    }

    fun logout() {
        clearArl()
        Log.d(TAG, "User logged out, ARL cleared")
    }

    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        val arl = getSavedArl() ?: throw IllegalStateException("Not logged in - ARL not set")
        try {
            service.searchTracks(arl, query)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for query: $query", e)
            emptyList()
        }
    }
    
    suspend fun downloadTrack(
        trackId: String,
        outputDir: String,
        quality: DownloadQuality
    ): DownloadResult = withContext(Dispatchers.IO) {
        val arl = getSavedArl() ?: throw IllegalStateException("Not logged in - ARL not set")
        try {
            val result = service.downloadTrack(arl, trackId, outputDir, quality)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download track $trackId", e)
            throw e
        }
    }
    
    suspend fun downloadAlbum(
        albumId: String,
        outputDir: String,
        quality: DownloadQuality
    ): BatchDownloadResult = withContext(Dispatchers.IO) {
        val arl = getSavedArl() ?: throw IllegalStateException("Not logged in - ARL not set")
        try {
            val result = service.downloadAlbum(arl, albumId, outputDir, quality)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download album $albumId", e)
            throw e
        }
    }
    
    suspend fun downloadPlaylist(
        playlistId: String,
        outputDir: String,
        quality: DownloadQuality
    ): BatchDownloadResult = withContext(Dispatchers.IO) {
        val arl = getSavedArl() ?: throw IllegalStateException("Not logged in - ARL not set")
        try {
            val result = service.downloadPlaylist(arl, playlistId, outputDir, quality)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download playlist $playlistId", e)
            throw e
        }
    }

    // =====================================
    // STREAMING
    // =====================================

    suspend fun preloadTrack(
        trackId: String,
        quality: DownloadQuality
    ): Long = withContext(Dispatchers.IO) {
        val arl = getSavedArl() ?: throw IllegalStateException("Not logged in - ARL not set")
        try {
            service.preloadTrack(arl, trackId, quality).toLong()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to preload track $trackId", e)
            throw e
        }
    }

    suspend fun readAudioChunk(
        trackId: String,
        offset: ULong,
        size: UInt
    ): ByteArray = withContext(Dispatchers.IO) {
        try {
            service.readAudioChunk(trackId, offset, size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read audio chunk for track $trackId", e)
            ByteArray(0)
        }
    }

    fun cancelPreload(trackId: String) {
        try {
            service.cancelPreload(trackId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel preload for track $trackId", e)
        }
    }
}

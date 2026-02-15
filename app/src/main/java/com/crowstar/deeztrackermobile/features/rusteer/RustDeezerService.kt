package com.crowstar.deeztrackermobile.features.rusteer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rusteer.BatchDownloadResult
import uniffi.rusteer.DownloadQuality
import uniffi.rusteer.DownloadResult
import uniffi.rusteer.RusteerService
import uniffi.rusteer.Track

class RustDeezerService(context: Context) {
    private val service = RusteerService()
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "rusteer_prefs",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val TAG = "RustDeezerService"
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
                Log.d(TAG, "Verifying ARL: ${arl.take(10)}...")
                val isValid = service.verifyArl(arl)
                if (isValid) {
                    // Save ARL to SharedPreferences
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
        Log.d(TAG, "User logged out, ARL cleared")
    }

    /**
     * Search for tracks on Deezer.
     *
     * @param query The search query (e.g. "Artist - Title")
     * @return List of matching tracks
     */
    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        val arl = getSavedArl() ?: throw IllegalStateException("Not logged in - ARL not set")
        try {
            service.searchTracks(arl, query)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for query: $query", e)
            emptyList()
        }
    }
    
    // ==================== DOWNLOAD METHODS ====================
    
    // TODO: Uncomment after recompiling Rust bindings (run: cd rusteer && cargo build)
    /*
    /**
     * Get streaming URL for a track (for online playback without downloading to storage).
     * 
     * @param trackId The Deezer track ID
     * @param quality Streaming quality (FLAC, MP3_320, MP3_128)
     * @return StreamingUrl containing URL, track ID, quality, and format
     * @throws Exception if streaming URL retrieval fails or ARL is not set
     */
    suspend fun getStreamingUrl(
        trackId: String,
        quality: DownloadQuality
    ): uniffi.rusteer.StreamingUrl = withContext(Dispatchers.IO) {
        val arl = getSavedArl() ?: throw IllegalStateException("Not logged in - ARL not set")
        
        Log.d(TAG, "Getting streaming URL for track $trackId with quality $quality")
        
        try {
            val result = service.getStreamingUrl(arl, trackId, quality)
            Log.d(TAG, "Streaming URL obtained for track: $trackId")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get streaming URL for track $trackId", e)
            throw e
        }
    }
    */
    
    /**
     * Download a single track.
     * 
     * @param trackId The Deezer track ID
     * @param outputDir Directory where the track will be saved
     * @param quality Download quality (FLAC, MP3_320, MP3_128)
     * @return DownloadResult containing path, quality, size, title, and artist
     * @throws Exception if download fails or ARL is not set
     */
    suspend fun downloadTrack(
        trackId: String,
        outputDir: String,
        quality: DownloadQuality
    ): DownloadResult = withContext(Dispatchers.IO) {
        val arl = getSavedArl() ?: throw IllegalStateException("Not logged in - ARL not set")
        
        Log.d(TAG, "Downloading track $trackId to $outputDir with quality $quality")
        
        try {
            val result = service.downloadTrack(arl, trackId, outputDir, quality)
            Log.d(TAG, "Track downloaded successfully: ${result.title} - ${result.artist}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download track $trackId", e)
            throw e
        }
    }
    
    /**
     * Download an entire album.
     * 
     * @param albumId The Deezer album ID
     * @param outputDir Directory where the album will be saved
     * @param quality Download quality (FLAC, MP3_320, MP3_128)
     * @return BatchDownloadResult containing successful downloads and failures
     * @throws Exception if download fails or ARL is not set
     */
    suspend fun downloadAlbum(
        albumId: String,
        outputDir: String,
        quality: DownloadQuality
    ): BatchDownloadResult = withContext(Dispatchers.IO) {
        val arl = getSavedArl() ?: throw IllegalStateException("Not logged in - ARL not set")
        
        Log.d(TAG, "Downloading album $albumId to $outputDir with quality $quality")
        
        try {
            val result = service.downloadAlbum(arl, albumId, outputDir, quality)
            Log.d(TAG, "Album download complete: ${result.successful.size} succeeded, ${result.failed.size} failed")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download album $albumId", e)
            throw e
        }
    }
    
    /**
     * Download an entire playlist.
     * 
     * @param playlistId The Deezer playlist ID
     * @param outputDir Directory where the playlist will be saved
     * @param quality Download quality (FLAC, MP3_320, MP3_128)
     * @return BatchDownloadResult containing successful downloads and failures
     * @throws Exception if download fails or ARL is not set
     */
    suspend fun downloadPlaylist(
        playlistId: String,
        outputDir: String,
        quality: DownloadQuality
    ): BatchDownloadResult = withContext(Dispatchers.IO) {
        val arl = getSavedArl() ?: throw IllegalStateException("Not logged in - ARL not set")
        
        Log.d(TAG, "Downloading playlist $playlistId to $outputDir with quality $quality")
        
        try {
            val result = service.downloadPlaylist(arl, playlistId, outputDir, quality)
            Log.d(TAG, "Playlist download complete: ${result.successful.size} succeeded, ${result.failed.size} failed")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download playlist $playlistId", e)
            throw e
        }
    }
}

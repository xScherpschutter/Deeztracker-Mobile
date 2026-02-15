package com.crowstar.deeztrackermobile.features.player

import android.content.Context
import android.util.Log
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rusteer.DownloadQuality
import java.io.File

/**
 * Manages online streaming by downloading to temporary cache directory.
 * Files are automatically cleaned up by Android when space is needed.
 */
class StreamingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "StreamingManager"
        private const val CACHE_DIR_NAME = "streaming_cache"
        private const val MAX_CACHE_SIZE_MB = 500L // Maximum cache size
        private const val CACHE_MAP_PREFS = "streaming_cache_map"
        
        @Volatile
        private var INSTANCE: StreamingManager? = null
        
        fun getInstance(context: Context): StreamingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StreamingManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val rustService = RustDeezerService(context)
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val cacheMapPrefs = context.getSharedPreferences(CACHE_MAP_PREFS, Context.MODE_PRIVATE)
    
    /**
     * Get cache directory for streaming files.
     * Uses app's cache directory which Android can clear automatically.
     */
    private val cacheDir: File
        get() {
            val dir = File(context.cacheDir, CACHE_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    
    /**
     * Get current quality setting from preferences.
     */
    private val currentQuality: DownloadQuality
        get() {
            val saved = prefs.getString("audio_quality", "MP3_128")
            return when (saved) {
                "MP3_320" -> DownloadQuality.MP3_320
                "FLAC" -> DownloadQuality.FLAC
                else -> DownloadQuality.MP3_128
            }
        }
    
    /**
     * Stream a track by downloading to temporary cache.
     * Returns the local file path for playback.
     * 
     * @param trackId Deezer track ID
     * @return Local file path for playback, or null if failed
     */
    suspend fun streamTrack(trackId: String): String? = withContext(Dispatchers.IO) {
        try {
            // Check if already cached
            val cachedFile = findCachedFile(trackId)
            if (cachedFile?.exists() == true) {
                Log.d(TAG, "Using cached file for track $trackId: ${cachedFile.name}")
                // Update last access time
                cachedFile.setLastModified(System.currentTimeMillis())
                return@withContext cachedFile.absolutePath
            }
            
            Log.d(TAG, "Streaming track $trackId to cache")
            
            // Download to cache directory
            val result = rustService.downloadTrack(
                trackId = trackId,
                outputDir = cacheDir.absolutePath,
                quality = currentQuality
            )
            
            Log.d(TAG, "Track streamed successfully: ${result.path}")
            
            // Save mapping between trackId and filename
            saveCacheMapping(trackId, result.path)
            
            // Clean up old cache if needed
            cleanupCache()
            
            result.path
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream track $trackId", e)
            null
        }
    }
    
    /**
     * Find cached file for a track if it exists.
     */
    private fun findCachedFile(trackId: String): File? {
        // Get filename from cache map
        val filename = cacheMapPrefs.getString(trackId, null) ?: return null
        val file = File(cacheDir, filename)
        
        // Verify file still exists
        if (!file.exists()) {
            // Remove stale mapping
            cacheMapPrefs.edit().remove(trackId).apply()
            return null
        }
        
        return file
    }
    
    /**
     * Save mapping between trackId and filename.
     */
    private fun saveCacheMapping(trackId: String, filePath: String) {
        val filename = File(filePath).name
        cacheMapPrefs.edit().putString(trackId, filename).apply()
        Log.d(TAG, "Saved cache mapping: $trackId -> $filename")
    }
    
    /**
     * Clean up old cache files if cache size exceeds limit.
     * Removes least recently used files first.
     */
    private fun cleanupCache() {
        val files = cacheDir.listFiles() ?: return
        
        // Calculate total cache size
        val totalSize = files.sumOf { it.length() }
        val maxSizeBytes = MAX_CACHE_SIZE_MB * 1024 * 1024
        
        if (totalSize > maxSizeBytes) {
            Log.d(TAG, "Cache size ${totalSize / 1024 / 1024}MB exceeds limit, cleaning up...")
            
            // Sort by last modified (oldest first)
            val sortedFiles = files.sortedBy { it.lastModified() }
            
            var currentSize = totalSize
            for (file in sortedFiles) {
                if (currentSize <= maxSizeBytes) break
                
                val fileSize = file.length()
                if (file.delete()) {
                    currentSize -= fileSize
                    // Remove mapping for deleted file
                    removeCacheMappingByFilename(file.name)
                    Log.d(TAG, "Deleted old cache file: ${file.name}")
                }
            }
            
            Log.d(TAG, "Cache cleanup complete. New size: ${currentSize / 1024 / 1024}MB")
        }
    }
    
    /**
     * Remove cache mapping by filename.
     */
    private fun removeCacheMappingByFilename(filename: String) {
        val editor = cacheMapPrefs.edit()
        val allMappings = cacheMapPrefs.all
        for ((trackId, storedFilename) in allMappings) {
            if (storedFilename == filename) {
                editor.remove(trackId)
                Log.d(TAG, "Removed cache mapping: $trackId -> $filename")
            }
        }
        editor.apply()
    }
    
    /**
     * Clear all streaming cache.
     */
    fun clearCache() {
        val files = cacheDir.listFiles() ?: return
        var deletedCount = 0
        var freedSpace = 0L
        
        for (file in files) {
            val size = file.length()
            if (file.delete()) {
                deletedCount++
                freedSpace += size
            }
        }
        
        // Clear all cache mappings
        cacheMapPrefs.edit().clear().apply()
        
        Log.d(TAG, "Cleared cache: $deletedCount files, ${freedSpace / 1024 / 1024}MB freed")
    }
    
    /**
     * Get current cache size in bytes.
     */
    fun getCacheSize(): Long {
        val files = cacheDir.listFiles() ?: return 0L
        return files.sumOf { it.length() }
    }
    
    /**
     * Check if streaming is available (user is logged in).
     */
    fun isStreamingAvailable(): Boolean {
        return rustService.isLoggedIn()
    }
}

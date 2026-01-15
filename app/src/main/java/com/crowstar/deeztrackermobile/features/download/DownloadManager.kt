package com.crowstar.deeztrackermobile.features.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import uniffi.rusteer.DownloadQuality
import android.media.MediaScannerConnection 
import java.io.File

/**
 * Singleton manager for handling music downloads.
 * Ensures only one download happens at a time.
 * Uses its own coroutine scope so downloads survive navigation.
 */
class DownloadManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "DownloadManager"
        
        @Volatile
        private var INSTANCE: DownloadManager? = null
        
        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Internal scope that survives navigation - uses SupervisorJob so failures don't cancel other jobs
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val rustService = RustDeezerService(context)
    private val downloadMutex = Mutex()
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // Dynamic quality from Settings
    val currentQuality: DownloadQuality
        get() {
            val saved = prefs.getString("audio_quality", "MP3_128")
            return when (saved) {
                "MP3_320" -> DownloadQuality.MP3_320
                "FLAC" -> DownloadQuality.FLAC
                else -> DownloadQuality.MP3_128
            }
        }
    
    /**
     * Get the base download directory.
     * Creates it if it doesn't exist.
     */
    val downloadDirectory: String
        get() {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val deezDir = File(musicDir, "Deeztracker")
            if (!deezDir.exists()) {
                deezDir.mkdirs()
            }
            return deezDir.absolutePath
        }
    
    /**
     * Check if a download is currently in progress.
     */
    fun isDownloading(): Boolean = _downloadState.value is DownloadState.Downloading
    
    /**
     * Reset state to Idle (call after showing notification to user).
     */
    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
    
    /**
     * Start downloading a single track.
     * This method launches the download in the manager's own scope,
     * so it continues even if the caller's scope is cancelled.
     * 
     * @param trackId The Deezer track ID
     * @param title The track title (for display purposes)
     * @return true if download started, false if another download is in progress
     */
    fun startTrackDownload(trackId: Long, title: String): Boolean {
        // Prevent concurrent downloads
        if (!downloadMutex.tryLock()) {
            Log.w(TAG, "Cannot start download - another download is in progress")
            return false
        }
        
        _downloadState.value = DownloadState.Downloading(
            type = DownloadType.TRACK,
            title = title,
            itemId = trackId.toString()
        )
        
        managerScope.launch {
            try {
                Log.d(TAG, "Starting track download: $title (ID: $trackId)")
                
                val result = rustService.downloadTrack(
                    trackId = trackId.toString(),
                    outputDir = downloadDirectory,
                    quality = currentQuality
                )
                
                _downloadState.value = DownloadState.Completed(
                    type = DownloadType.TRACK,
                    title = title,
                    successCount = 1
                )
                
                Log.d(TAG, "Track download completed: ${result.path}")
                scanFile(result.path) // Scan the file
                
            } catch (e: Exception) {
                Log.e(TAG, "Track download failed: $title", e)
                _downloadState.value = DownloadState.Error(
                    title = title,
                    message = e.message ?: "Unknown error"
                )
            } finally {
                downloadMutex.unlock()
            }
        }
        
        return true
    }
    
    /**
     * Start downloading an entire album.
     * This method launches the download in the manager's own scope,
     * so it continues even if the caller's scope is cancelled.
     * 
     * @param albumId The Deezer album ID
     * @param title The album title (for display purposes)
     * @return true if download started, false if another download is in progress
     */
    fun startAlbumDownload(albumId: Long, title: String): Boolean {
        if (!downloadMutex.tryLock()) {
            Log.w(TAG, "Cannot start download - another download is in progress")
            return false
        }
        
        _downloadState.value = DownloadState.Downloading(
            type = DownloadType.ALBUM,
            title = title,
            itemId = albumId.toString()
        )
        
        managerScope.launch {
            try {
                Log.d(TAG, "Starting album download: $title (ID: $albumId)")
                
                val result = rustService.downloadAlbum(
                    albumId = albumId.toString(),
                    outputDir = downloadDirectory,
                    quality = currentQuality
                )
                
                _downloadState.value = DownloadState.Completed(
                    type = DownloadType.ALBUM,
                    title = title,
                    successCount = result.successful.size,
                    failedCount = result.failed.size
                )
                
                Log.d(TAG, "Album download completed: ${result.successful.size} succeeded, ${result.failed.size} failed")
                
                // Scan all successful files
                result.successful.forEach { success ->
                    scanFile(success.path)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Album download failed: $title", e)
                _downloadState.value = DownloadState.Error(
                    title = title,
                    message = e.message ?: "Unknown error"
                )
            } finally {
                downloadMutex.unlock()
            }
        }
        
        return true
    }
    
    /**
     * Start downloading an entire playlist.
     * This method launches the download in the manager's own scope,
     * so it continues even if the caller's scope is cancelled.
     * 
     * @param playlistId The Deezer playlist ID
     * @param title The playlist title (for display purposes)
     * @return true if download started, false if another download is in progress
     */
    fun startPlaylistDownload(playlistId: Long, title: String): Boolean {
        if (!downloadMutex.tryLock()) {
            Log.w(TAG, "Cannot start download - another download is in progress")
            return false
        }
        
        _downloadState.value = DownloadState.Downloading(
            type = DownloadType.PLAYLIST,
            title = title,
            itemId = playlistId.toString()
        )
        
        managerScope.launch {
            try {
                Log.d(TAG, "Starting playlist download: $title (ID: $playlistId)")
                
                val result = rustService.downloadPlaylist(
                    playlistId = playlistId.toString(),
                    outputDir = downloadDirectory,
                    quality = currentQuality
                )
                
                _downloadState.value = DownloadState.Completed(
                    type = DownloadType.PLAYLIST,
                    title = title,
                    successCount = result.successful.size,
                    failedCount = result.failed.size
                )
                
                Log.d(TAG, "Playlist download completed: ${result.successful.size} succeeded, ${result.failed.size} failed")
                
                // Scan all successful files
                result.successful.forEach { success ->
                    scanFile(success.path)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Playlist download failed: $title", e)
                _downloadState.value = DownloadState.Error(
                    title = title,
                    message = e.message ?: "Unknown error"
                )
            } finally {
                downloadMutex.unlock()
            }
        }
        
        return true
    }
    
    /**
     * Helper to scan a file into MediaStore so it appears in music apps immediately.
     */
    private fun scanFile(path: String) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                null // Auto-detect mime type based on extension
            ) { _, uri ->
                Log.d(TAG, "Scanned $path -> $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan file: $path", e)
        }
    }

    
    // Legacy suspend functions for backwards compatibility (deprecated)
    @Deprecated("Use startTrackDownload instead", ReplaceWith("startTrackDownload(trackId, title)"))
    suspend fun downloadTrack(trackId: Long, title: String) {
        startTrackDownload(trackId, title)
    }
    
    @Deprecated("Use startAlbumDownload instead", ReplaceWith("startAlbumDownload(albumId, title)"))
    suspend fun downloadAlbum(albumId: Long, title: String) {
        startAlbumDownload(albumId, title)
    }
    
    @Deprecated("Use startPlaylistDownload instead", ReplaceWith("startPlaylistDownload(playlistId, title)"))
    suspend fun downloadPlaylist(playlistId: Long, title: String) {
        startPlaylistDownload(playlistId, title)
    }
}

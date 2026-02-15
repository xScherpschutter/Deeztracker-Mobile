package com.crowstar.deeztrackermobile.features.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import com.crowstar.deeztrackermobile.features.deezer.DeezerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import uniffi.rusteer.DownloadQuality
import android.media.MediaScannerConnection 
import java.io.File
import kotlin.coroutines.resume

/**
 * Singleton manager for handling music downloads.
 * Ensures only one download happens at a time.
 * Uses its own coroutine scope so downloads survive navigation.
 */
class DownloadManager private constructor(
    private val context: Context,
    private val musicRepository: com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository,
    private val playlistRepository: com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
) {
    
    companion object {
        private const val TAG = "DownloadManager"
        
        @Volatile
        private var INSTANCE: DownloadManager? = null
        
        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                // Manually constructing dependencies here for singleton simplicity
                // ideally this should be DI
                val musicRepo = com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository(context.contentResolver)
                val playlistRepo = com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository(context)
                
                INSTANCE ?: DownloadManager(context.applicationContext, musicRepo, playlistRepo).also { INSTANCE = it }
            }
        }
    }
    
    // Internal scope that survives navigation - uses SupervisorJob so failures don't cancel other jobs
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val rustService = RustDeezerService(context)
    private val deezerRepository = DeezerRepository()
    
    // Queue for sequential processing (limited to 20 items to prevent saturation)
    private val downloadChannel = kotlinx.coroutines.channels.Channel<DownloadRequest>(20)
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    // Trigger that increments when downloads complete and are confirmed in MediaStore
    private val _downloadRefreshTrigger = MutableStateFlow(0)
    val downloadRefreshTrigger: StateFlow<Int> = _downloadRefreshTrigger.asStateFlow()
    
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    init {
        // Start processing the queue
        managerScope.launch {
            for (request in downloadChannel) {
                processRequest(request)
            }
        }
    }

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
            val locationPref = prefs.getString("download_location", "MUSIC")
            val rootDir = if (locationPref == "DOWNLOADS") {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            }
            
            val deezDir = File(rootDir, "Deeztracker")
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
    @OptIn(ExperimentalCoroutinesApi::class)
    fun resetState() {
        if (downloadChannel.isEmpty) {
            _downloadState.value = DownloadState.Idle
        }
    }
    
    fun startTrackDownload(trackId: Long, title: String, targetPlaylistId: String? = null): Boolean {
        downloadChannel.trySend(DownloadRequest.Track(trackId, title, targetPlaylistId))
        return true
    }
    
    fun startAlbumDownload(albumId: Long, title: String): Boolean {
        downloadChannel.trySend(DownloadRequest.Album(albumId, title))
        return true
    }
    
    fun startPlaylistDownload(playlistId: Long, title: String): Boolean {
        downloadChannel.trySend(DownloadRequest.Playlist(playlistId, title))
        return true
    }
    
    suspend fun isTrackDownloaded(trackTitle: String, artistName: String): Boolean {
        return try {
            val localTracks = musicRepository.getAllTracks()
            
            // Title Normalization: Strip 'feat', lowercase, remove non-alphanumeric
            fun normalizeTitle(input: String): String {
                val withoutFeat = input.replace(Regex("(?i)[\\(\\[]?(?:feat\\.|ft\\.|featuring|with).*"), "")
                return withoutFeat.lowercase().replace(Regex("[^a-z0-9]"), "")
            }

            // Artist Normalization: Lowercase, remove non-alphanumeric (Keep 'feat' info as it might be relevant for artist content)
            fun normalizeArtist(input: String): String {
                return input.lowercase().replace(Regex("[^a-z0-9]"), "")
            }
            
            val normalizedTitle = normalizeTitle(trackTitle)
            val normalizedArtist = normalizeArtist(artistName)
            
            localTracks.any { track ->
                val localTitle = normalizeTitle(track.title)
                val localArtist = normalizeArtist(track.artist)
                
                // Title: Strict match (after stripping feat)
                val titleMatch = localTitle == normalizedTitle
                
                // Artist: Partial match allowed (to handle "Artist A" vs "Artist A, Artist B")
                val artistMatch = localArtist.contains(normalizedArtist) || normalizedArtist.contains(localArtist)
                
                titleMatch && artistMatch
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if track is downloaded", e)
            false
        }
    }

    private suspend fun processRequest(request: DownloadRequest) {
        _downloadState.value = DownloadState.Downloading(
            type = when(request) {
                is DownloadRequest.Track -> DownloadType.TRACK
                is DownloadRequest.Album -> DownloadType.ALBUM
                is DownloadRequest.Playlist -> DownloadType.PLAYLIST
            },
            title = request.title,
            itemId = request.id.toString()
        )
        
        try {
            Log.d(TAG, "Starting download: ${request.title} (ID: ${request.id})")
            
            when (request) {
                is DownloadRequest.Track -> {
                    val result = rustService.downloadTrack(
                        trackId = request.id.toString(),
                        outputDir = downloadDirectory,
                        quality = currentQuality
                    )
                    Log.d(TAG, "Track download completed: ${result.path}")
                    
                    // Scan and Link to Playlist BEFORE marking as completed
                    val uri = scanFileSuspend(result.path)
                    if (uri != null) {
                        // Retry finding ID as MediaStore might have slight indexing delay
                        var trackId: Long? = null
                        // Increase retries to 5 and delay to 1s to be very robust
                        for (i in 0..4) {
                            trackId = musicRepository.getTrackIdByPath(result.path)
                            if (trackId != null) break
                            kotlinx.coroutines.delay(1000)
                        }

                        if (trackId != null) {
                            // If this track should be added to a playlist, do it now
                            if (request.playlistId != null) {
                                Log.d(TAG, "Adding downloaded track $trackId to playlist ${request.playlistId}")
                                playlistRepository.addTrackToPlaylist(request.playlistId, trackId)
                            }
                            
                            // Trigger UI refresh after file is confirmed in MediaStore
                            _downloadRefreshTrigger.value += 1
                        } else {
                            Log.e(TAG, "Could not find MediaStore ID for ${result.path} after retries")
                        }
                    } else {
                        Log.e(TAG, "Failed to scan file into MediaStore: ${result.path}")
                    }
                    
                    // Mark as completed AFTER scanning and adding to playlist
                    _downloadState.value = DownloadState.Completed(
                        type = DownloadType.TRACK,
                        title = request.title,
                        successCount = 1
                    )
                }
                is DownloadRequest.Album -> {
                    // Get album tracks first
                    val albumTracks = try {
                        deezerRepository.getAlbumTracks(request.id).data
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get album tracks", e)
                        emptyList()
                    }
                    
                    // Check which tracks are already downloaded
                    var successCount = 0
                    var failedCount = 0
                    var skippedCount = 0
                    
                    for (track in albumTracks) {
                        val isAlreadyDownloaded = isTrackDownloaded(
                            track.title,
                            track.artist?.name ?: ""
                        )
                        
                        if (isAlreadyDownloaded) {
                            skippedCount++
                            Log.d(TAG, "Skipping already downloaded track: ${track.title}")
                        } else {
                            try {
                                // Update state to show this specific track is downloading
                                _downloadState.value = DownloadState.Downloading(
                                    type = DownloadType.ALBUM,
                                    title = request.title,
                                    itemId = request.id.toString(),
                                    currentTrackId = track.id.toString()
                                )
                                
                                val result = rustService.downloadTrack(
                                    trackId = track.id.toString(),
                                    outputDir = downloadDirectory,
                                    quality = currentQuality
                                )
                                successCount++
                                scanFileSuspend(result.path)
                                Log.d(TAG, "Downloaded track: ${track.title}")
                                
                                // Trigger UI refresh after this track is confirmed in MediaStore
                                _downloadRefreshTrigger.value += 1
                            } catch (e: Exception) {
                                failedCount++
                                Log.e(TAG, "Failed to download track: ${track.title}", e)
                            }
                        }
                    }
                    
                    _downloadState.value = DownloadState.Completed(
                        type = DownloadType.ALBUM,
                        title = request.title,
                        successCount = successCount,
                        failedCount = failedCount,
                        skippedCount = skippedCount
                    )
                    Log.d(TAG, "Album download completed: $successCount succeeded, $skippedCount skipped, $failedCount failed")
                }
                is DownloadRequest.Playlist -> {
                    // Get playlist tracks first
                    val playlistTracks = try {
                        deezerRepository.getPlaylistTracks(request.id).data
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get playlist tracks", e)
                        emptyList()
                    }
                    
                    // Check which tracks are already downloaded
                    var successCount = 0
                    var failedCount = 0
                    var skippedCount = 0
                    
                    for (track in playlistTracks) {
                        val isAlreadyDownloaded = isTrackDownloaded(
                            track.title,
                            track.artist?.name ?: ""
                        )
                        
                        if (isAlreadyDownloaded) {
                            skippedCount++
                            Log.d(TAG, "Skipping already downloaded track: ${track.title}")
                        } else {
                            try {
                                // Update state to show this specific track is downloading
                                _downloadState.value = DownloadState.Downloading(
                                    type = DownloadType.PLAYLIST,
                                    title = request.title,
                                    itemId = request.id.toString(),
                                    currentTrackId = track.id.toString()
                                )
                                
                                val result = rustService.downloadTrack(
                                    trackId = track.id.toString(),
                                    outputDir = downloadDirectory,
                                    quality = currentQuality
                                )
                                successCount++
                                scanFileSuspend(result.path)
                                Log.d(TAG, "Downloaded track: ${track.title}")
                                
                                // Trigger UI refresh after this track is confirmed in MediaStore
                                _downloadRefreshTrigger.value += 1
                            } catch (e: Exception) {
                                failedCount++
                                Log.e(TAG, "Failed to download track: ${track.title}", e)
                            }
                        }
                    }
                    
                    _downloadState.value = DownloadState.Completed(
                        type = DownloadType.PLAYLIST,
                        title = request.title,
                        successCount = successCount,
                        failedCount = failedCount,
                        skippedCount = skippedCount
                    )
                    Log.d(TAG, "Playlist download completed: $successCount succeeded, $skippedCount skipped, $failedCount failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${request.title}", e)
            _downloadState.value = DownloadState.Error(
                title = request.title,
                message = e.message ?: "Unknown error"
            )
        }
        
        // Brief delay could be added here if needed to let UI update state before next item
        // e.g., delay(500)
    }

    private sealed class DownloadRequest(val id: Long, val title: String) {
        class Track(id: Long, title: String, val playlistId: String? = null) : DownloadRequest(id, title)
        class Album(id: Long, title: String) : DownloadRequest(id, title)
        class Playlist(id: Long, title: String) : DownloadRequest(id, title)
    }
    
    /**
     * Helper to scan a file into MediaStore suspendedly.
     */
    private suspend fun scanFileSuspend(path: String): android.net.Uri? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                null // Auto-detect mime type
            ) { _, uri ->
                if (cont.isActive) cont.resume(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect file: $path", e)
            if (cont.isActive) cont.resume(null)
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

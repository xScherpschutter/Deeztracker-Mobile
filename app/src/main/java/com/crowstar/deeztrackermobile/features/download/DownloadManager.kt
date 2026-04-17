package com.crowstar.deeztrackermobile.features.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import com.crowstar.deeztrackermobile.features.deezer.DeezerRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import uniffi.rusteer.DownloadQuality
import android.media.MediaScannerConnection 
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: LocalMusicRepository,
    private val playlistRepository: LocalPlaylistRepository,
    private val rustService: RustDeezerService,
    private val deezerRepository: DeezerRepository
) {
    private val TAG = "DownloadManager"
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadChannel = kotlinx.coroutines.channels.Channel<DownloadRequest>(20)
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    // FAST CACHE: Memory set for O(1) download checks during scroll
    private val _downloadedKeys = MutableStateFlow<Set<String>>(emptySet())
    val downloadedKeys: StateFlow<Set<String>> = _downloadedKeys.asStateFlow()
    
    private val _downloadRefreshTrigger = MutableStateFlow(0)
    val downloadRefreshTrigger: StateFlow<Int> = _downloadRefreshTrigger.asStateFlow()
    
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    init {
        // Initial cache load
        refreshDownloadedKeys()
        
        managerScope.launch {
            for (request in downloadChannel) {
                processRequest(request)
            }
        }
    }

    private fun refreshDownloadedKeys() {
        managerScope.launch {
            try {
                val keys = musicRepository.getAllTracks().map { 
                    generateTrackKey(it.title, it.artist)
                }.toSet()
                _downloadedKeys.value = keys
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing keys", e)
            }
        }
    }

    /**
     * Normalizes titles by stripping feature information and non-alphanumeric characters.
     */
    private fun normalizeTitle(input: String): String {
        val withoutFeat = input.replace(Regex("(?i)\\s*[\\(\\[](?:feat\\.?|ft\\.?|featuring|with)[^\\)\\]]*[\\)\\]]"), "")
            .replace(Regex("(?i)\\s*(?:feat\\.?|ft\\.?|featuring|with).*"), "")
        return withoutFeat.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Normalizes artist names by extracting the primary artist before standard separators,
     * then lowercasing and removing non-alphanumeric characters.
     */
    private fun normalizeArtist(input: String): String {
        val primaryArtist = input.split(Regex("[,/;&]|\\b(?i)(feat\\.?|ft\\.?|featuring|with)\\b")).first()
        return primaryArtist.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    fun generateTrackKey(title: String, artist: String): String {
        return "${normalizeTitle(title)}|${normalizeArtist(artist)}"
    }

    fun isTrackDownloadedFast(title: String, artist: String): Boolean {
        return _downloadedKeys.value.contains(generateTrackKey(title, artist))
    }

    val currentQuality: DownloadQuality
        get() {
            val saved = prefs.getString("audio_quality", "MP3_128")
            return when (saved) {
                "MP3_320" -> DownloadQuality.MP3_320
                "FLAC" -> DownloadQuality.FLAC
                else -> DownloadQuality.MP3_128
            }
        }
    
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
    
    fun isDownloading(): Boolean = _downloadState.value is DownloadState.Downloading
    
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
            val normalizedTitle = normalizeTitle(trackTitle)
            val normalizedArtist = normalizeArtist(artistName)
            
            // First try O(1) cache
            if (_downloadedKeys.value.contains("$normalizedTitle|$normalizedArtist")) {
                return true
            }

            // Fallback to O(N) scan for flexible matching (e.g. partial artist matches)
            val localTracks = musicRepository.getAllTracks()
            localTracks.any { track ->
                val localTitle = normalizeTitle(track.title)
                val localArtist = normalizeArtist(track.artist)
                
                val titleMatch = localTitle == normalizedTitle
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
            when (request) {
                is DownloadRequest.Track -> {
                    val result = rustService.downloadTrack(
                        trackId = request.id.toString(),
                        outputDir = downloadDirectory,
                        quality = currentQuality
                    )
                    
                    val uri = scanFileSuspend(result.path)
                    if (uri != null) {
                        // FIX: Manually inject the key immediately so the UI doesn't have to wait for MediaStore refresh
                        val newKey = generateTrackKey(request.title, result.artist ?: "") 
                        _downloadedKeys.update { it + newKey }
                        
                        refreshDownloadedKeys()
                        _downloadRefreshTrigger.value += 1
                    }
                    
                    _downloadState.value = DownloadState.Completed(
                        type = DownloadType.TRACK,
                        title = request.title,
                        successCount = 1
                    )
                }
                is DownloadRequest.Album -> {
                    val albumTracks = try {
                        deezerRepository.getAlbumTracks(request.id).data
                    } catch (e: Exception) {
                        emptyList()
                    }
                    
                    var successCount = 0
                    var failedCount = 0
                    var skippedCount = 0
                    
                    for (track in albumTracks) {
                        if (isTrackDownloadedFast(track.title, track.artist?.name ?: "")) {
                            skippedCount++
                        } else {
                            try {
                                _downloadState.value = DownloadState.Downloading(
                                    type = DownloadType.ALBUM,
                                    title = request.title,
                                    itemId = request.id.toString(),
                                    currentTrackId = track.id.toString()
                                )
                                rustService.downloadTrack(
                                    trackId = track.id.toString(),
                                    outputDir = downloadDirectory,
                                    quality = currentQuality
                                )
                                successCount++
                                refreshDownloadedKeys()
                                _downloadRefreshTrigger.value += 1
                            } catch (e: Exception) {
                                failedCount++
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
                }
                is DownloadRequest.Playlist -> {
                    val playlistTracks = try {
                        deezerRepository.getPlaylistTracks(request.id).data
                    } catch (e: Exception) {
                        emptyList()
                    }
                    
                    var successCount = 0
                    var failedCount = 0
                    var skippedCount = 0
                    
                    for (track in playlistTracks) {
                        if (isTrackDownloadedFast(track.title, track.artist?.name ?: "")) {
                            skippedCount++
                        } else {
                            try {
                                _downloadState.value = DownloadState.Downloading(
                                    type = DownloadType.PLAYLIST,
                                    title = request.title,
                                    itemId = request.id.toString(),
                                    currentTrackId = track.id.toString()
                                )
                                rustService.downloadTrack(
                                    trackId = track.id.toString(),
                                    outputDir = downloadDirectory,
                                    quality = currentQuality
                                )
                                successCount++
                                refreshDownloadedKeys()
                                _downloadRefreshTrigger.value += 1
                            } catch (e: Exception) {
                                failedCount++
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
                }
            }
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error(
                title = request.title,
                message = e.message ?: "Unknown error"
            )
        }
    }

    private sealed class DownloadRequest(val id: Long, val title: String) {
        class Track(id: Long, title: String, val playlistId: String? = null) : DownloadRequest(id, title)
        class Album(id: Long, title: String) : DownloadRequest(id, title)
        class Playlist(id: Long, title: String) : DownloadRequest(id, title)
    }
    
    private suspend fun scanFileSuspend(path: String): android.net.Uri? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, uri ->
                if (cont.isActive) cont.resume(uri)
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }
}

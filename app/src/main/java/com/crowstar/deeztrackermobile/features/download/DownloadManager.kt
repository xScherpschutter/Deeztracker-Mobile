package com.crowstar.deeztrackermobile.features.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import com.crowstar.deeztrackermobile.features.deezer.DeezerRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.media.MediaScannerConnection 
import dagger.hilt.android.qualifiers.ApplicationContext
import uniffi.rusteer.DownloadQuality
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
    
    // Búfer aumentado a 100 para evitar pérdida de peticiones
    private val downloadChannel = kotlinx.coroutines.channels.Channel<DownloadRequest>(100)
    
    // Control de concurrencia: máximo 3 descargas de tracks en paralelo
    private val downloadSemaphore = Semaphore(3)
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    // IDs de tracks que se están descargando actualmente (para mostrar múltiples spinners)
    private val _activeDownloads = MutableStateFlow<Set<String>>(emptySet())
    val activeDownloads: StateFlow<Set<String>> = _activeDownloads.asStateFlow()
    
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
                // Loteamos el procesamiento para que múltiples peticiones individuales
                // puedan mostrar su spinner simultáneamente mientras esperan el semáforo.
                launch {
                    processRequest(request)
                }
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

    private fun normalizeTitle(input: String?): String {
        val str = input ?: ""
        val withoutFeat = str.replace(Regex("(?i)\\s*[\\(\\[](?:feat\\.?|ft\\.?|featuring|with)[^\\)\\]]*[\\)\\]]"), "")
            .replace(Regex("(?i)\\s*(?:feat\\.?|ft\\.?|featuring|with).*"), "")
        return withoutFeat.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun normalizeArtist(input: String?): String {
        val str = input ?: ""
        val primaryArtist = str.split(Regex("[,/;&]|\\b(?i)(feat\\.?|ft\\.?|featuring|with)\\b")).first()
        return primaryArtist.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    fun generateTrackKey(title: String?, artist: String?): String {
        return "${normalizeTitle(title)}|${normalizeArtist(artist)}"
    }

    fun isTrackDownloadedFast(title: String?, artist: String?): Boolean {
        return _downloadedKeys.value.contains(generateTrackKey(title, artist))
    }

    suspend fun checkIfTrackDownloaded(trackTitle: String, artistName: String): Boolean {
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
        if (downloadChannel.isEmpty && _activeDownloads.value.isEmpty()) {
            _downloadState.value = DownloadState.Idle
        }
    }
    
    fun startTrackDownload(trackId: Long, title: String, targetPlaylistId: String? = null): Boolean {
        managerScope.launch {
            downloadChannel.send(DownloadRequest.Track(trackId, title, targetPlaylistId))
        }
        return true
    }
    
    fun startAlbumDownload(albumId: Long, title: String): Boolean {
        managerScope.launch {
            downloadChannel.send(DownloadRequest.Album(albumId, title))
        }
        return true
    }
    
    fun startPlaylistDownload(playlistId: Long, title: String): Boolean {
        managerScope.launch {
            downloadChannel.send(DownloadRequest.Playlist(playlistId, title))
        }
        return true
    }

    private suspend fun processRequest(request: DownloadRequest) {
        // Actualizamos estado general (esto sirve para notificaciones de "Descargando X álbum")
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
                    val success = downloadSingleTrackWithControl(request.id, request.title)
                    _downloadState.value = DownloadState.Completed(
                        type = DownloadType.TRACK,
                        title = request.title,
                        successCount = if (success) 1 else 0,
                        failedCount = if (success) 0 else 1
                    )
                }
                is DownloadRequest.Album -> {
                    val tracks = try {
                        deezerRepository.getAlbumTracks(request.id).data
                    } catch (e: Exception) {
                        emptyList()
                    }
                    processBulkDownload(DownloadType.ALBUM, request.title, tracks)
                }
                is DownloadRequest.Playlist -> {
                    val tracks = try {
                        deezerRepository.getPlaylistTracks(request.id).data
                    } catch (e: Exception) {
                        emptyList()
                    }
                    processBulkDownload(DownloadType.PLAYLIST, request.title, tracks)
                }
            }
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error(
                title = request.title,
                message = e.message ?: "Unknown error"
            )
        }
    }

    private suspend fun downloadSingleTrackWithControl(trackId: Long, title: String): Boolean {
        val idStr = trackId.toString()
        // Marcar como activo inmediatamente para el spinner
        _activeDownloads.update { it + idStr }
        
        return try {
            // Esperar turno en el semáforo (máximo 3 a la vez)
            downloadSemaphore.withPermit {
                val result = rustService.downloadTrack(
                    trackId = idStr,
                    outputDir = downloadDirectory,
                    quality = currentQuality
                )
                
                val uri = scanFileSuspend(result.path)
                if (uri != null) {
                    val newKey = generateTrackKey(title, result.artist ?: "") 
                    _downloadedKeys.update { it + newKey }
                    _downloadRefreshTrigger.value += 1
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading track $trackId", e)
            false
        } finally {
            // Quitar de activos al terminar (éxito o error)
            _activeDownloads.update { it - idStr }
        }
    }

    private suspend fun processBulkDownload(
        type: DownloadType, 
        requestTitle: String, 
        tracks: List<com.crowstar.deeztrackermobile.features.deezer.Track>
    ) {
        var successCount = 0
        var failedCount = 0
        var skippedCount = 0
        
        // Usamos supervisorScope para que el fallo de un track no cancele los demás
        supervisorScope {
            tracks.map { track ->
                async {
                    if (isTrackDownloadedFast(track.title, track.artist?.name ?: "")) {
                        synchronized(this@DownloadManager) { skippedCount++ }
                    } else {
                        val success = downloadSingleTrackWithControl(track.id, track.title ?: "Unknown")
                        synchronized(this@DownloadManager) {
                            if (success) successCount++ else failedCount++
                        }
                    }
                }
            }.awaitAll()
        }
        
        _downloadState.value = DownloadState.Completed(
            type = type,
            title = requestTitle,
            successCount = successCount,
            failedCount = failedCount,
            skippedCount = skippedCount
        )
        refreshDownloadedKeys() // Refresco final por si acaso
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

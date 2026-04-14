package com.crowstar.deeztrackermobile.ui.playlist

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import com.crowstar.deeztrackermobile.features.localmusic.LocalMusicRepository
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.crowstar.deeztrackermobile.features.localmusic.LocalPlaylistRepository
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import uniffi.rusteer.Track
import com.crowstar.deeztrackermobile.R
import java.io.BufferedReader
import java.io.InputStreamReader

@HiltViewModel
class ImportPlaylistViewModel @Inject constructor(
    private val rustService: RustDeezerService,
    private val localRepo: LocalMusicRepository,
    private val playlistRepository: LocalPlaylistRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _playlistName = MutableStateFlow("")
    val playlistName: StateFlow<String> = _playlistName.asStateFlow()

    private val _importedTracks = MutableStateFlow<List<ImportedTrackState>>(emptyList())
    val importedTracks: StateFlow<List<ImportedTrackState>> = _importedTracks.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _progressMessage = MutableStateFlow("")
    val progressMessage: StateFlow<String> = _progressMessage.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadedCount = MutableStateFlow(0)
    val downloadedCount: StateFlow<Int> = _downloadedCount.asStateFlow()

    private val _totalToDownload = MutableStateFlow(0)
    val totalToDownload: StateFlow<Int> = _totalToDownload.asStateFlow()

    val downloadRefreshTrigger = downloadManager.downloadRefreshTrigger

    private var tracksToDownload: List<Track> = emptyList()

    fun setProcessing(processing: Boolean, message: String = "") {
        _isProcessing.value = processing
        _progressMessage.value = message
    }

    fun updateProgressMessage(message: String) {
        _progressMessage.value = message
    }

    fun incrementDownloadedCount() {
        _downloadedCount.value += 1
    }

    fun startImport(context: Context, uri: Uri, 
                    readingMsg: String, 
                    checkingMsg: String, 
                    searchingMsg: String,
                    onInvalidFile: () -> Unit,
                    onEmptyFile: () -> Unit,
                    onCorrupted: () -> Unit,
                    onIOError: () -> Unit,
                    onUnknownError: () -> Unit) {
        viewModelScope.launch {
            setProcessing(true, readingMsg)
            try {
                val fileName = getFileName(context, uri)
                if (!isValidPlaylistFile(fileName)) {
                    onInvalidFile()
                    return@launch
                }

                val (name, queries) = parsePlaylist(context, uri)
                _playlistName.value = name

                updateProgressMessage(checkingMsg)
                val localTracks = localRepo.getAllTracks()

                val stateList = queries.map { query ->
                    val match = findLocalMatch(query, localTracks)
                    if (match != null) {
                        ImportedTrackState(query, ImportStatus.FoundLocally(match))
                    } else {
                        ImportedTrackState(query, ImportStatus.Missing)
                    }
                }
                _importedTracks.value = stateList

                updateProgressMessage(searchingMsg)
                val updatedList = stateList.toMutableList()
                stateList.forEachIndexed { index, item ->
                    if (item.status is ImportStatus.Missing) {
                        val searchResult = rustService.searchTracks(item.rawQuery).firstOrNull()
                        if (searchResult != null) {
                            updatedList[index] = item.copy(status = ImportStatus.FoundOnDeezer(searchResult))
                        } else {
                            updatedList[index] = item.copy(status = ImportStatus.NotFound)
                        }
                        _importedTracks.value = updatedList.toList()
                    }
                }
            } catch (e: InvalidPlaylistException) {
                when (e.reason) {
                    InvalidPlaylistReason.EMPTY_FILE -> onEmptyFile()
                    InvalidPlaylistReason.CORRUPTED -> onCorrupted()
                    InvalidPlaylistReason.INVALID_FORMAT -> onInvalidFile()
                }
            } catch (e: java.io.IOException) {
                onIOError()
            } catch (e: Exception) {
                onUnknownError()
            } finally {
                setProcessing(false)
            }
        }
    }

    fun executeImport(startingDownloadTemplate: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val toDownload = _importedTracks.value.mapNotNull { 
                (it.status as? ImportStatus.FoundOnDeezer)?.track
            }

            if (toDownload.isEmpty()) {
                createPlaylistWithTracks()
                onComplete()
            } else {
                _isDownloading.value = true
                _isProcessing.value = true
                _downloadedCount.value = 0
                _totalToDownload.value = toDownload.size
                tracksToDownload = toDownload
                _progressMessage.value = startingDownloadTemplate.format(toDownload.size)

                toDownload.forEach { track ->
                    downloadManager.startTrackDownload(track.id.toLong(), track.title)
                }
            }
        }
    }

    suspend fun createPlaylistWithTracks() {
        val playlistId = playlistRepository.createPlaylist(_playlistName.value)
        val allLocalTracks = localRepo.getAllTracks()

        _importedTracks.value.forEach { item ->
            if (item.status is ImportStatus.FoundLocally) {
                playlistRepository.addTrackToPlaylist(playlistId, item.status.localTrack.id)
            }
        }

        tracksToDownload.forEach { track ->
            val matchedTrack = allLocalTracks.find { localTrack ->
                val titleMatch = localTrack.title.equals(track.title, ignoreCase = true)
                val artistMatch = localTrack.artist.contains(track.artist, ignoreCase = true) ||
                                track.artist.contains(localTrack.artist, ignoreCase = true)
                titleMatch && artistMatch
            }
            if (matchedTrack != null) {
                playlistRepository.addTrackToPlaylist(playlistId, matchedTrack.id)
            }
        }
        
        _isDownloading.value = false
        _isProcessing.value = false
        _downloadedCount.value = 0
        _totalToDownload.value = 0
        tracksToDownload = emptyList()
    }

    // Copied Helper functions from the Screen to make ViewModel autonomous
    private fun isValidPlaylistFile(fileName: String?): Boolean {
        if (fileName == null) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("m3u", "m3u8", "pls")
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) cursor.getString(nameIndex) else null
            } else null
        }
    }

    private suspend fun parsePlaylist(context: Context, uri: Uri): Pair<String, List<String>> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        var name = context.getString(R.string.import_playlist_default_name)
        val fileName = getFileName(context, uri)
        if (fileName != null) {
            name = fileName.substringBeforeLast(".")
            if (!isValidPlaylistFile(fileName)) {
                throw InvalidPlaylistException(InvalidPlaylistReason.INVALID_FORMAT, "File extension not supported: $fileName")
            }
        }
        val queries = mutableListOf<String>()
        var hasValidContent = false
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line = reader.readLine()
                    var lastWasExtInf = false
                    var lineCount = 0
                    while (line != null) {
                        lineCount++
                        line = line.trim()
                        if (line.startsWith("#EXTM3U") || line.startsWith("[playlist]")) {
                            hasValidContent = true
                        }
                        if (line.startsWith("#EXTINF:")) {
                            val info = line.substringAfter(",", "").trim()
                            if (info.isNotEmpty()) {
                                queries.add(info)
                                lastWasExtInf = true
                                hasValidContent = true
                            }
                        } else if (line.startsWith("File") && line.contains("=")) {
                            val path = line.substringAfter("=").trim()
                            if (path.isNotEmpty()) {
                                val filename = path.substringAfterLast("/").substringAfterLast("\\")
                                val nameWithoutExt = filename.substringBeforeLast(".")
                                if (nameWithoutExt.isNotEmpty()) {
                                    queries.add(nameWithoutExt)
                                    hasValidContent = true
                                }
                            }
                        } else if (!line.startsWith("#") && !line.startsWith("[") && line.isNotEmpty()) {
                            if (!lastWasExtInf) {
                                val filename = line.substringAfterLast("/").substringAfterLast("\\")
                                val nameWithoutExt = filename.substringBeforeLast(".")
                                if (nameWithoutExt.isNotEmpty()) {
                                    queries.add(nameWithoutExt)
                                    hasValidContent = true
                                }
                            }
                            lastWasExtInf = false
                        }
                        line = reader.readLine()
                    }
                    if (lineCount == 0) throw InvalidPlaylistException(InvalidPlaylistReason.EMPTY_FILE, "Playlist file is empty")
                }
            } ?: throw InvalidPlaylistException(InvalidPlaylistReason.CORRUPTED, "Could not open input stream")
        } catch (e: InvalidPlaylistException) {
            throw e
        } catch (e: Exception) {
            throw InvalidPlaylistException(InvalidPlaylistReason.CORRUPTED, "Error reading playlist file: ${e.message}")
        }
        if (queries.isEmpty() && !hasValidContent) throw InvalidPlaylistException(InvalidPlaylistReason.CORRUPTED, "No valid tracks found in playlist")
        Pair(name, queries.distinct())
    }

    private fun findLocalMatch(query: String, tracks: List<LocalTrack>): LocalTrack? {
        val normalizedQuery = query.lowercase().replace(Regex("[^a-z0-9]"), "")
        return tracks.find { track ->
            val t = track.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            val a = track.artist.lowercase().replace(Regex("[^a-z0-9]"), "")
            val full = "$a$t"
            if (t == normalizedQuery) return@find true
            if (full.contains(normalizedQuery) || normalizedQuery.contains(full)) return@find true
            false
        }
    }
}

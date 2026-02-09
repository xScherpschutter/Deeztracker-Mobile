package com.crowstar.deeztrackermobile.features.localmusic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalPlaylistRepository(private val context: Context) {

    private val playlistsFile = File(context.filesDir, "playlists.json")
    private val mutex = kotlinx.coroutines.sync.Mutex()
    
    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists

    suspend fun loadPlaylists() = withContext(Dispatchers.IO) {
        if (!playlistsFile.exists()) {
            // Create default Favorites playlist
            val initialPlaylists = listOf(LocalPlaylist(id = "favorites", name = "Favorites"))
            savePlaylistsToFile(initialPlaylists)
            _playlists.value = initialPlaylists
            return@withContext
        }

        try {
            val jsonString = playlistsFile.readText()
            val jsonArray = JSONArray(jsonString)
            val loadedPlaylists = mutableListOf<LocalPlaylist>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val tracksJson = obj.getJSONArray("trackIds")
                val trackIds = mutableListOf<Long>()
                for (j in 0 until tracksJson.length()) {
                    trackIds.add(tracksJson.getLong(j))
                }
                loadedPlaylists.add(LocalPlaylist(id, name, trackIds))
            }
            
            // Ensure Favorites exists
            if (loadedPlaylists.none { it.id == "favorites" }) {
                loadedPlaylists.add(0, LocalPlaylist(id = "favorites", name = "Favorites"))
                savePlaylistsToFile(loadedPlaylists)
            }
            
            _playlists.value = loadedPlaylists
        } catch (e: Exception) {
            e.printStackTrace()
            _playlists.value = emptyList()
        }
    }

    suspend fun createPlaylist(name: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val newPlaylist = LocalPlaylist(name = name)
            val current = _playlists.value.toMutableList()
            current.add(newPlaylist)
            savePlaylistsToFile(current)
            _playlists.value = current
            newPlaylist.id
        }
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _playlists.value.filter { it.id != playlistId }
            savePlaylistsToFile(current)
            _playlists.value = current
        }
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _playlists.value.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(name = newName)
                } else playlist
            }
            savePlaylistsToFile(current)
            _playlists.value = current
        }
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _playlists.value.map { playlist ->
                if (playlist.id == playlistId) {
                    if (!playlist.trackIds.contains(trackId)) {
                        playlist.copy(trackIds = playlist.trackIds + trackId)
                    } else playlist
                } else playlist
            }
            if (current != _playlists.value) {
                savePlaylistsToFile(current)
                _playlists.value = current
            }
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _playlists.value.map { playlist ->
                 if (playlist.id == playlistId) {
                    playlist.copy(trackIds = playlist.trackIds - trackId)
                } else playlist
            }
            savePlaylistsToFile(current)
            _playlists.value = current
        }
    }

    fun isFavorite(trackId: Long): Boolean {
        // Can be called from UI thread, so suspend is okay or just run on StateFlow
        return _playlists.value.find { it.id == "favorites" }?.trackIds?.contains(trackId) == true
    }

    suspend fun toggleFavorite(trackId: Long) {
        val favorites = _playlists.value.find { it.id == "favorites" } ?: return
        if (favorites.trackIds.contains(trackId)) {
            removeTrackFromPlaylist("favorites", trackId)
        } else {
            addTrackToPlaylist("favorites", trackId)
        }
    }

    private fun savePlaylistsToFile(playlists: List<LocalPlaylist>) {
        try {
            val jsonArray = JSONArray()
            playlists.forEach { playlist ->
                val obj = JSONObject()
                obj.put("id", playlist.id)
                obj.put("name", playlist.name)
                val tracksArray = JSONArray()
                playlist.trackIds.forEach { tracksArray.put(it) }
                obj.put("trackIds", tracksArray)
                jsonArray.put(obj)
            }
            playlistsFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

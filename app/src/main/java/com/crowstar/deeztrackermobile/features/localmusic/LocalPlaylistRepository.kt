package com.crowstar.deeztrackermobile.features.localmusic

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localMusicRepository: LocalMusicRepository
) {

    private val playlistsFile = File(context.filesDir, "playlists.json")
    private val mutex = Mutex()
    
    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists

    suspend fun loadPlaylists() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!playlistsFile.exists()) {
                Log.d("DeezTracker", "Playlists file not found, creating Favorites")
                val initialPlaylists = listOf(LocalPlaylist(id = "favorites", name = "Favorites"))
                savePlaylistsToFile(initialPlaylists)
                _playlists.value = initialPlaylists
                return@withContext
            }

            try {
                val jsonString = playlistsFile.readText()
                Log.d("DeezTracker", "Loading playlists from file: ${jsonString.take(100)}...")
                val jsonArray = JSONArray(jsonString)
                val loadedPlaylists = mutableListOf<LocalPlaylist>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val tracksJson = obj.getJSONArray(if (obj.has("tracks")) "tracks" else "trackIds")
                    val tracks = mutableListOf<PlaylistTrack>()
                    
                    if (obj.has("tracks")) {
                        for (j in 0 until tracksJson.length()) {
                            val trackObj = tracksJson.getJSONObject(j)
                            tracks.add(PlaylistTrack(
                                id = trackObj.getLong("id"),
                                title = trackObj.getString("title"),
                                artist = trackObj.getString("artist"),
                                album = trackObj.getString("album"),
                                albumId = trackObj.getLong("albumId"),
                                duration = trackObj.getLong("duration"),
                                albumArtUri = if (trackObj.isNull("albumArtUri")) null else trackObj.getString("albumArtUri"),
                                isStreaming = trackObj.optBoolean("isStreaming", false)
                            ))
                        }
                    } else {
                        val allLocalTracks = localMusicRepository.getAllTracks()
                        for (j in 0 until tracksJson.length()) {
                            val trackId = tracksJson.getLong(j)
                            allLocalTracks.find { it.id == trackId }?.let {
                                tracks.add(it.toPlaylistTrack())
                            }
                        }
                    }
                    loadedPlaylists.add(LocalPlaylist(id, name, tracks))
                }
                
                if (loadedPlaylists.none { it.id == "favorites" }) {
                    loadedPlaylists.add(0, LocalPlaylist(id = "favorites", name = "Favorites"))
                    savePlaylistsToFile(loadedPlaylists)
                }
                
                _playlists.value = loadedPlaylists
                Log.d("DeezTracker", "Loaded ${loadedPlaylists.size} playlists successfully")
            } catch (e: Exception) {
                Log.e("DeezTracker", "Error loading playlists", e)
                _playlists.value = listOf(LocalPlaylist(id = "favorites", name = "Favorites"))
            }
        }
    }

    suspend fun createPlaylist(name: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val newPlaylist = LocalPlaylist(name = name)
            val current = _playlists.value.toMutableList()
            current.add(newPlaylist)
            savePlaylistsToFile(current)
            _playlists.value = current
            Log.d("DeezTracker", "Created playlist: $name with ID: ${newPlaylist.id}")
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

    suspend fun addTrackToPlaylist(playlistId: String, track: PlaylistTrack) = withContext(Dispatchers.IO) {
        mutex.withLock {
            Log.d("DeezTracker", "Attempting to add track '${track.title}' to playlist ID: $playlistId")
            val currentPlaylists = _playlists.value
            
            // Debug available IDs
            val availableIds = currentPlaylists.map { "${it.name}:${it.id}" }
            Log.d("DeezTracker", "Available playlists: $availableIds")

            var found = false
            val updated = currentPlaylists.map { playlist ->
                // Check by ID or fallback to Name for Favorites
                if (playlist.id == playlistId || (playlistId == "favorites" && playlist.name == "Favorites")) {
                    if (!playlist.tracks.any { it.id == track.id }) {
                        found = true
                        Log.d("DeezTracker", "Playlist found. Adding track.")
                        playlist.copy(tracks = playlist.tracks + track)
                    } else {
                        Log.d("DeezTracker", "Track already in playlist. Skipping.")
                        playlist
                    }
                } else playlist
            }

            if (found) {
                savePlaylistsToFile(updated)
                _playlists.value = updated
                Log.d("DeezTracker", "Playlist state updated and saved.")
            } else {
                Log.e("DeezTracker", "Playlist with ID $playlistId NOT FOUND among available playlists.")
            }
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _playlists.value.map { playlist ->
                 if (playlist.id == playlistId || (playlistId == "favorites" && playlist.name == "Favorites")) {
                    playlist.copy(tracks = playlist.tracks.filter { it.id != trackId })
                } else playlist
            }
            savePlaylistsToFile(current)
            _playlists.value = current
        }
    }

    fun isFavorite(trackId: Long): Boolean {
        return _playlists.value.find { it.id == "favorites" || it.name == "Favorites" }?.tracks?.any { it.id == trackId } == true
    }

    suspend fun toggleFavorite(track: PlaylistTrack) {
        val favorites = _playlists.value.find { it.id == "favorites" || it.name == "Favorites" } ?: return
        if (favorites.tracks.any { it.id == track.id }) {
            Log.d("DeezTracker", "Removing from favorites: ${track.title}")
            removeTrackFromPlaylist(favorites.id, track.id)
        } else {
            Log.d("DeezTracker", "Adding to favorites: ${track.title}")
            addTrackToPlaylist(favorites.id, track)
        }
    }

    private fun savePlaylistsToFile(playlists: List<LocalPlaylist>) {
        try {
            Log.d("DeezTracker", "Writing ${playlists.size} playlists to disk...")
            val jsonArray = JSONArray()
            playlists.forEach { playlist ->
                val obj = JSONObject()
                obj.put("id", playlist.id)
                obj.put("name", playlist.name)
                val tracksArray = JSONArray()
                playlist.tracks.forEach { track ->
                    val trackObj = JSONObject()
                    trackObj.put("id", track.id)
                    trackObj.put("title", track.title)
                    trackObj.put("artist", track.artist)
                    trackObj.put("album", track.album)
                    trackObj.put("albumId", track.albumId)
                    trackObj.put("duration", track.duration)
                    trackObj.put("albumArtUri", track.albumArtUri ?: JSONObject.NULL)
                    trackObj.put("isStreaming", track.isStreaming)
                    tracksArray.put(trackObj)
                }
                obj.put("tracks", tracksArray)
                jsonArray.put(obj)
            }
            val jsonStr = jsonArray.toString()
            playlistsFile.writeText(jsonStr)
            Log.d("DeezTracker", "Save successful. File size: ${playlistsFile.length()} bytes")
        } catch (e: Exception) {
            Log.e("DeezTracker", "FAILED to save playlists", e)
        }
    }
}

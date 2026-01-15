package com.crowstar.deeztrackermobile.features.lyrics

import android.content.Context
import android.util.Log
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException

class LyricsRepository(private val context: Context) {

    private val api: LrcLibApi

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(LrcLibApi::class.java)
    }

    suspend fun getLyrics(track: LocalTrack): String? {
        val cacheFile = getCacheFile(track)
        
        // 1. Check Cache
        if (cacheFile.exists()) {
            Log.d("LyricsRepository", "Found lyrics in cache: ${cacheFile.absolutePath}")
            return try {
                cacheFile.readText()
            } catch (e: Exception) {
                Log.e("LyricsRepository", "Error reading cache", e)
                null
            }
        }

        // 2. Fetch from API
        return try {
            Log.d("LyricsRepository", "Fetching lyrics from API for: ${track.title} - ${track.artist}")
            // Duration in seconds needed for better accuracy
            // Assuming track.duration is ms, convert to seconds
            val durationSeconds = track.duration / 1000.0
            
            val response = api.getLyrics(
                trackName = track.title,
                artistName = track.artist,
                albumName = track.album,
                duration = durationSeconds
            )
            
            val lyrics = response.syncedLyrics ?: response.plainLyrics
            
            if (!lyrics.isNullOrBlank()) {
                // 3. Save to Cache
                saveToCache(cacheFile, lyrics)
                lyrics
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e("LyricsRepository", "Error fetching lyrics", e)
            null
        }
    }

    private fun getCacheFile(track: LocalTrack): File {
        val cacheDir = File(context.cacheDir, "lyrics")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        // Sanitize filename to avoid IO issues
        val safeArtist = track.artist.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val safeTitle = track.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return File(cacheDir, "${safeArtist}_${safeTitle}.lrc")
    }

    private fun saveToCache(file: File, content: String) {
        try {
            file.writeText(content)
        } catch (e: IOException) {
            Log.e("LyricsRepository", "Failed to cache lyrics", e)
        }
    }
}

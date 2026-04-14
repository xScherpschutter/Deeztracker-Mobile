package com.crowstar.deeztrackermobile.features.lyrics

import android.content.Context
import android.util.Log
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: LrcLibApi
) {

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 500L
    }

    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = MAX_RETRIES,
        initialDelay: Long = INITIAL_BACKOFF_MS,
        operation: String,
        block: suspend () -> T
    ): T? {
        var currentDelay = initialDelay
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) {
                    Log.e("LyricsRepository", "$operation failed after $maxRetries attempts: ${e.message}", e)
                    return null
                }
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return null
    }

    suspend fun getLyrics(track: LocalTrack): String? {
        val cacheFile = getCacheFile(track)
        
        if (cacheFile.exists()) {
            val cachedContent = try {
                 cacheFile.readText()
            } catch (e: Exception) {
                null
            }
            
            if (cachedContent == "NOT_FOUND" || cachedContent.isNullOrBlank()) {
                cacheFile.delete()
            } else {
                return cachedContent
            }
        }

        val durationSeconds = track.duration / 1000.0
        val strictResponse = safeApiCall(track.title, track.artist, track.album, durationSeconds)
        
        if (!strictResponse?.syncedLyrics.isNullOrBlank()) {
            val lyrics = strictResponse!!.syncedLyrics!!
            saveToCache(cacheFile, lyrics)
            return lyrics
        }

        val searchResults = safeSearch("${track.title} ${track.artist}")
        val bestSynced = searchResults.take(5).firstNotNullOfOrNull { it.syncedLyrics.takeIf { s -> !s.isNullOrBlank() } }
        
        if (bestSynced != null) {
            saveToCache(cacheFile, bestSynced)
            return bestSynced
        }
        
        val bestPlain = strictResponse?.plainLyrics.takeIf { !it.isNullOrBlank() }
            ?: searchResults.firstNotNullOfOrNull { it.plainLyrics.takeIf { s -> !s.isNullOrBlank() } }
            
        if (bestPlain != null) {
             saveToCache(cacheFile, bestPlain)
             return bestPlain
        }

        return null
    }

    private suspend fun safeSearch(query: String): List<LrcLibResponse> {
        val results = retryWithBackoff(
            operation = "Fuzzy search for '$query'"
        ) {
            api.search(query = query)
        }
        return results ?: emptyList()
    }

    private suspend fun safeApiCall(
        trackName: String, 
        artistName: String, 
        albumName: String?, 
        duration: Double?
    ): LrcLibResponse? {
        return retryWithBackoff(
            operation = "Exact match for '$trackName - $artistName'"
        ) {
            api.getLyrics(
                trackName = trackName, 
                artistName = artistName, 
                albumName = albumName, 
                duration = duration
            )
        }
    }

    private fun getCacheFile(track: LocalTrack): File {
        val cacheDir = File(context.cacheDir, "lyrics")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
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

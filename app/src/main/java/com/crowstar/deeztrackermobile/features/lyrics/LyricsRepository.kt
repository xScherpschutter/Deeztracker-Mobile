package com.crowstar.deeztrackermobile.features.lyrics

import android.content.Context
import android.util.Log
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class LyricsRepository(private val context: Context) {

    private val api: LrcLibApi
    
    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 500L
    }

    init {
        // Configure OkHttp with timeouts to prevent hanging connections
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(LrcLibApi::class.java)
    }
    
    /**
     * Retry a suspending function with exponential backoff
     */
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
                Log.w("LyricsRepository", "$operation attempt ${attempt + 1} failed: ${e.message}. Retrying in ${currentDelay}ms...")
                delay(currentDelay)
                currentDelay *= 2 // Exponential backoff
            }
        }
        return null
    }

    suspend fun getLyrics(track: LocalTrack): String? {
        val cacheFile = getCacheFile(track)
        
        // 1. Check Cache
        if (cacheFile.exists()) {
            val cachedContent = try {
                 cacheFile.readText()
            } catch (e: Exception) {
                null
            }
            
            // If cache contains "NOT_FOUND" from previous versions or is invalid, ignore it
            if (cachedContent == "NOT_FOUND" || cachedContent.isNullOrBlank()) {
                Log.d("LyricsRepository", "Invalid or 'NOT_FOUND' in cache. Deleting and refetching.")
                cacheFile.delete()
            } else {
                Log.d("LyricsRepository", "Found lyrics in cache: ${cacheFile.absolutePath}")
                return cachedContent
            }
        }

        // 2. Fetch from API with prioritization for SYNCED lyrics
        Log.d("LyricsRepository", "Starting lyrics search for: ${track.title} - ${track.artist}")
        val durationSeconds = track.duration / 1000.0

        // Step A: Exact match (Title, Artist, Album, Duration)
        val strictResponse = safeApiCall(track.title, track.artist, track.album, durationSeconds)
        
        // If we found synced lyrics immediately, return them
        if (!strictResponse?.syncedLyrics.isNullOrBlank()) {
            Log.d("LyricsRepository", "Found synced lyrics via Exact Match!")
            val lyrics = strictResponse!!.syncedLyrics!!
            saveToCache(cacheFile, lyrics)
            return lyrics
        }

        // Step B: Fuzzy Search (Fallback if strict failed or only had plain lyrics)
        Log.d("LyricsRepository", "Exact match failed or unsynced. Trying Fuzzy Search...")
        
        val searchResults = safeSearch("${track.title} ${track.artist}")
        
        // Look for SYNCED lyrics in the top results (using first 5 to be safe)
        val bestSynced = searchResults.take(5).firstNotNullOfOrNull { it.syncedLyrics.takeIf { s -> !s.isNullOrBlank() } }
        
        if (bestSynced != null) {
            Log.d("LyricsRepository", "Found synced lyrics via Fuzzy Search!")
            saveToCache(cacheFile, bestSynced)
            return bestSynced
        }
        
        // Step C: Fallback to Plain Lyrics
        // Prioritize strict result plain lyrics, then search result plain lyrics
        val bestPlain = strictResponse?.plainLyrics.takeIf { !it.isNullOrBlank() }
            ?: searchResults.firstNotNullOfOrNull { it.plainLyrics.takeIf { s -> !s.isNullOrBlank() } }
            
        if (bestPlain != null) {
             Log.d("LyricsRepository", "Only plain lyrics found.")
             saveToCache(cacheFile, bestPlain)
             return bestPlain
        }

        Log.d("LyricsRepository", "No lyrics found after all strategies.")
        return null
    }

    private suspend fun safeSearch(query: String): List<LrcLibResponse> {
        Log.d("LyricsRepository", "Fuzzy searching with query: '$query'")
        
        val results = retryWithBackoff(
            operation = "Fuzzy search for '$query'"
        ) {
            api.search(query = query)
        }
        
        return if (results != null) {
            Log.d("LyricsRepository", "Fuzzy search found ${results.size} results")
            results
        } else {
            Log.e("LyricsRepository", "Fuzzy search failed after all retries")
            emptyList()
        }
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

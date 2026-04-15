package com.crowstar.deeztrackermobile.features.rusteer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.util.Log
import com.crowstar.deeztrackermobile.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uniffi.rusteer.DownloadQuality
import java.net.URI
import javax.inject.Inject

@AndroidEntryPoint
class AudioStreamService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    @Inject
    lateinit var rustDeezerService: RustDeezerService

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DataSource.Factory {
                        RusteerDataSource(rustDeezerService)
                    }
                )
            )
            .build()
            
        mediaSession = MediaSession.Builder(this, player!!)
            .setId("AudioStreamService")
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "streaming_channel",
                "Audio Streaming",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showLoadingNotification() {
        val notification = NotificationCompat.Builder(this, "streaming_channel")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Deeztracker")
            .setContentText("Cargando stream...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        startForeground(1337, notification)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trackId = intent?.getStringExtra("track_id")
        
        if (trackId != null) {
            showLoadingNotification()
            // Start streaming in a coroutine to not block main thread but await preload
            scope.launch {
                startStreaming(trackId)
            }
        }
        
        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(UnstableApi::class)
    private suspend fun startStreaming(trackId: String) {
        try {
            // 1. Wait for Rust to initialize the buffer and fetch metadata
            rustDeezerService.preloadTrack(trackId, DownloadQuality.MP3_320)
            
            // 2. Once preloaded (entry exists in STREAM_CACHE), tell ExoPlayer to play
            withContext(Dispatchers.Main) {
                val mediaItem = MediaItem.fromUri("rusteer://$trackId")
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.play()
            }
        } catch (e: Exception) {
            Log.e("AudioStreamService", "Failed to start stream for $trackId", e)
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

/**
 * Custom DataSource for ExoPlayer to pull data directly from the Rust buffer
 */
@UnstableApi
class RusteerDataSource(private val rustDeezerService: RustDeezerService) : DataSource {

    private var trackId: String? = null
    private var currentPosition: Long = 0
    private var isOpen = false

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        // Optional: implement if needed for analytics
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri: URI = URI.create(dataSpec.uri.toString())
        if (uri.scheme == "rusteer") {
            trackId = uri.host
            currentPosition = dataSpec.position
            isOpen = true
            return C.LENGTH_UNSET.toLong()
        }
        throw Exception("Invalid Rusteer URI")
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (!isOpen || trackId == null) {
            return C.RESULT_END_OF_INPUT
        }

        if (readLength == 0) return 0

        // Perform blocking read to Rust over UniFFI
        return runBlocking(Dispatchers.IO) {
            try {
                val chunk = rustDeezerService.readAudioChunk(trackId!!, currentPosition.toULong(), readLength.toUInt())
                if (chunk.isEmpty()) {
                    return@runBlocking C.RESULT_END_OF_INPUT
                }
                
                val bytesToCopy = Math.min(chunk.size, readLength)
                System.arraycopy(chunk, 0, buffer, offset, bytesToCopy)
                currentPosition += bytesToCopy.toLong()
                
                bytesToCopy
            } catch (e: Exception) {
                e.printStackTrace()
                C.RESULT_END_OF_INPUT
            }
        }
    }

    override fun getUri(): android.net.Uri? {
        return trackId?.let { android.net.Uri.parse("rusteer://$it") }
    }

    override fun close() {
        trackId?.let { 
            // Depending on the use case, you might cancel preload here,
            // but for LRU cache, it's better to keep it alive in Rust.
        }
        isOpen = false
        trackId = null
    }
}
package com.crowstar.deeztrackermobile.features.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.crowstar.deeztrackermobile.MainActivity
import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uniffi.rusteer.DownloadQuality
import java.net.URI
import javax.inject.Inject

@AndroidEntryPoint
class MusicService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    @Inject
    lateinit var rustDeezerService: RustDeezerService

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "deeztracker_music"
        private const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "music_prefs"
        private const val KEY_LAST_TRACK_ID = "last_track_id"
        private const val KEY_LAST_POSITION = "last_position"
        private const val KEY_SHUFFLE_MODE = "shuffle_mode"
        const val CMD_SET_VOLUME = "SET_VOLUME"
        const val KEY_VOLUME = "volume"
    }

    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        createNotificationChannel()
        restorePlaybackState()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(
                        DataSource.Factory {
                            RusteerDataSource(this, rustDeezerService)
                        }
                    )
            )
            .build()
        
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlayerState()
                if (playbackState == Player.STATE_ENDED) {
                    // Handle end of playlist if needed
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayerState()
                if (!isPlaying) {
                    savePlaybackState()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updatePlayerState()
                savePlaybackState()
                
                // If it's a streaming item, trigger preload
                mediaItem?.localConfiguration?.uri?.let { uri ->
                    if (uri.scheme == "rusteer") {
                        val trackId = uri.host ?: ""
                        serviceScope.launch {
                            try {
                                rustDeezerService.preloadTrack(trackId, DownloadQuality.MP3_320)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    try {
                        loudnessEnhancer?.release()
                        loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                        loudnessEnhancer?.enabled = false
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })

        // Use MediaLibrarySession instead of generic MediaSession
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, object : MediaLibraryService.MediaLibrarySession.Callback {
            // Placeholder: Implement browser root and item logic if needed for external access (Android Auto etc)
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<androidx.media3.session.LibraryResult<MediaItem>> {
                 return Futures.immediateFuture(androidx.media3.session.LibraryResult.ofItem(
                     MediaItem.Builder()
                        .setMediaId("root")
                        .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build())
                        .build(),
                     params
                 ))
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<androidx.media3.session.LibraryResult<MediaItem>> {
                return Futures.immediateFuture(androidx.media3.session.LibraryResult.ofError(androidx.media3.session.LibraryResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                // Ensure we preload if the first item is streaming
                mediaItems.firstOrNull()?.localConfiguration?.uri?.let { uri ->
                    if (uri.scheme == "rusteer") {
                        val trackId = uri.host ?: ""
                        // We can't easily suspend here, but we can launch a job
                        serviceScope.launch {
                            try {
                                rustDeezerService.preloadTrack(trackId, DownloadQuality.MP3_320)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                return super.onAddMediaItems(mediaSession, controller, mediaItems)
            }

            // Add other overrides as necessary
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                val sessionCommands = connectionResult.availableSessionCommands
                    .buildUpon()
                    .add(androidx.media3.session.SessionCommand(CMD_SET_VOLUME, Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.accept(sessionCommands, connectionResult.availablePlayerCommands)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: androidx.media3.session.SessionCommand,
                args: Bundle
            ): ListenableFuture<androidx.media3.session.SessionResult> {
                if (customCommand.customAction == CMD_SET_VOLUME) {
                    val volume = args.getFloat(KEY_VOLUME, 1.0f)
                    if (volume <= 1.0f) {
                        player.volume = volume
                        try {
                            loudnessEnhancer?.enabled = false
                        } catch (e: Exception) { e.printStackTrace() }
                    } else {
                        // Boost logic
                        player.volume = 1.0f
                        try {
                            if (loudnessEnhancer == null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                                 loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
                            }
                            
                            val boost = ((volume - 1.0f) * 4 * 1500).toInt()
                            loudnessEnhancer?.setTargetGain(boost)
                            loudnessEnhancer?.enabled = true
                        } catch (e: Exception) { 
                            e.printStackTrace()
                        }
                    }
                    return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        })
        .setBitmapLoader(CustomBitmapLoader(this))  // Use custom bitmap loader to prevent caching issues
        .setSessionActivity(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()
    }

    @OptIn(UnstableApi::class) 
    private fun restorePlaybackState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTrackPath = prefs.getString(KEY_LAST_TRACK_ID, null)
        val lastPosition = prefs.getLong(KEY_LAST_POSITION, 0L)
        val shuffleMode = prefs.getBoolean(KEY_SHUFFLE_MODE, false)

        player.shuffleModeEnabled = shuffleMode

        if (lastTrackPath != null) {
            val uri = if (lastTrackPath.startsWith("rusteer://")) {
                Uri.parse(lastTrackPath)
            } else {
                val file = File(lastTrackPath)
                if (file.exists()) Uri.fromFile(file) else null
            }
            
            if (uri != null) {
                val mediaItem = MediaItem.fromUri(uri)
                player.setMediaItem(mediaItem)
                player.seekTo(lastPosition)
                player.prepare()
                // Don't auto-play on restore
                player.pause() 
            }
        }
    }

    private fun savePlaybackState() {
        val currentMediaItem = player.currentMediaItem ?: return
        // Use full URI as ID to distinguish between local and streaming
        val path = currentMediaItem.localConfiguration?.uri?.toString()
        
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_LAST_TRACK_ID, path)
            putLong(KEY_LAST_POSITION, player.currentPosition)
            putBoolean(KEY_SHUFFLE_MODE, player.shuffleModeEnabled)
            apply()
        }
    }

    private fun updatePlayerState() {
        // Broadcast state updates (to be consumed by PlayerController)
    }

    // Return MediaLibrarySession here
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        savePlaybackState()
        mediaLibrarySession.release()
        player.release()
        loudnessEnhancer?.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Music Playback"
            val descriptionText = "Controls for music playback"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

/**
 * Custom DataSource for ExoPlayer to pull data directly from the Rust buffer
 * or delegate to DefaultDataSource for other schemes (file://, http://, etc)
 */
@UnstableApi
class RusteerDataSource(
    private val context: Context,
    private val rustDeezerService: RustDeezerService
) : DataSource {

    private val defaultDataSource: DataSource by lazy {
        androidx.media3.datasource.DefaultDataSource.Factory(context).createDataSource()
    }
    
    private var activeDataSource: DataSource? = null
    private var trackId: String? = null
    private var currentPosition: Long = 0
    private var isOpen = false

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        defaultDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uriString = dataSpec.uri.toString()
        if (uriString.startsWith("rusteer://")) {
            val uri = URI.create(uriString)
            trackId = uri.host ?: uriString.substringAfter("rusteer://")
            currentPosition = dataSpec.position
            isOpen = true
            activeDataSource = null // We handle it ourselves
            
            // Critical: Wait for Rust to initialize buffer and fetch headers
            runBlocking(Dispatchers.IO) {
                try {
                    rustDeezerService.preloadTrack(trackId!!, DownloadQuality.MP3_320)
                } catch (e: Exception) {
                    Log.e("RusteerDataSource", "Preload failed for $trackId", e)
                }
            }
            
            return C.LENGTH_UNSET.toLong()
        } else {
            activeDataSource = defaultDataSource
            return defaultDataSource.open(dataSpec)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (activeDataSource == defaultDataSource) {
            return defaultDataSource.read(buffer, offset, readLength)
        }

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
                Log.e("RusteerDataSource", "Error reading chunk for $trackId", e)
                C.RESULT_END_OF_INPUT
            }
        }
    }

    override fun getUri(): android.net.Uri? {
        if (activeDataSource == defaultDataSource) {
            return defaultDataSource.uri
        }
        return trackId?.let { android.net.Uri.parse("rusteer://$it") }
    }

    override fun close() {
        if (activeDataSource == defaultDataSource) {
            defaultDataSource.close()
        }
        activeDataSource = null
        isOpen = false
        trackId = null
    }
}
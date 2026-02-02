package com.crowstar.deeztrackermobile.features.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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

class MusicService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

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
            val file = File(lastTrackPath)
            if (file.exists()) {
                val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
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
        // We use the URI path as the ID for local files
        val path = currentMediaItem.localConfiguration?.uri?.path
        
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

package com.crowstar.deeztrackermobile.data.model

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val coverUrl: String?,
    val streamUrl: String
) {
    fun toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(coverUrl?.let { Uri.parse(it) })
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }
}

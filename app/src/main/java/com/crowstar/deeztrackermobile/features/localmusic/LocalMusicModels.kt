package com.crowstar.deeztrackermobile.features.localmusic

/**
 * Represents a locally stored music track with metadata from MediaStore
 */
data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long, // in milliseconds
    val filePath: String,
    val size: Long, // in bytes
    val mimeType: String,
    val dateAdded: Long, // timestamp
    val dateModified: Long, // timestamp
    val albumArtUri: String? = null,
    val track: Int? = null, // Track number
    val year: Int? = null,
    val isStreaming: Boolean = false
) {
    /**
     * Formatted duration in MM:SS format
     */
    fun getFormattedDuration(): String {
        val seconds = (duration / 1000).toInt()
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(minutes, secs)
    }

    /**
     * Formatted file size (MB, KB)
     */
    fun getFormattedSize(): String {
        return when {
            size >= 1024 * 1024 -> "%.2f MB".format(size / (1024.0 * 1024.0))
            size >= 1024 -> "%.2f KB".format(size / 1024.0)
            else -> "$size B"
        }
    }
}

/**
 * Represents an album from local music
 */
data class LocalAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val albumArtUri: String?
)

/**
 * Represents an artist from local music
 */
data class LocalArtist(
    val id: Long,
    val name: String,
    val numberOfTracks: Int,
    val numberOfAlbums: Int,
    val artistArtUri: String? = null
)

/**
 * Represents a track in a playlist, containing basic metadata to avoid
 * needing to query MediaStore every time.
 */
data class PlaylistTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val albumArtUri: String? = null,
    val isStreaming: Boolean = false
)

fun PlaylistTrack.toLocalTrack(): LocalTrack {
    return LocalTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        albumArtUri = albumArtUri,
        isStreaming = isStreaming,
        filePath = "", // Not needed for playlist display
        size = 0,
        mimeType = "",
        dateAdded = 0,
        dateModified = 0
    )
}

fun LocalTrack.toPlaylistTrack(): PlaylistTrack {
    return PlaylistTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        albumArtUri = albumArtUri,
        isStreaming = isStreaming
    )
}

fun com.crowstar.deeztrackermobile.features.deezer.Track.toPlaylistTrack(albumArtUri: String? = null): PlaylistTrack {
    return PlaylistTrack(
        id = this.id,
        title = this.title ?: "Unknown Title",
        artist = this.artist?.name ?: "Unknown Artist",
        album = this.album?.title ?: "Unknown Album",
        albumId = this.album?.id ?: 0L,
        duration = (this.duration?.toLong() ?: 0L) * 1000,
        albumArtUri = albumArtUri ?: this.album?.coverBig ?: this.album?.coverMedium,
        isStreaming = true
    )
}

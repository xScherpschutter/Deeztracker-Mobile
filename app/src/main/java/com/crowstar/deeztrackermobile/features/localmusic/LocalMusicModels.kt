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
    val year: Int? = null
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

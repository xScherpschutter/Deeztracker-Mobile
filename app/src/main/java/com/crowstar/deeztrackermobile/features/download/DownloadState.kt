package com.crowstar.deeztrackermobile.features.download

/**
 * Represents the type of content being downloaded.
 */
enum class DownloadType {
    TRACK,
    ALBUM,
    PLAYLIST
}

/**
 * Represents the current state of the download manager.
 */
sealed class DownloadState {
    /**
     * No download in progress.
     */
    object Idle : DownloadState()
    
    /**
     * A download is currently in progress.
     */
    data class Downloading(
        val type: DownloadType,
        val title: String,
        val itemId: String
    ) : DownloadState()
    
    /**
     * A download has completed successfully.
     */
    data class Completed(
        val type: DownloadType,
        val title: String,
        val successCount: Int,
        val failedCount: Int = 0,
        val skippedCount: Int = 0  // Tracks that were already downloaded
    ) : DownloadState()
    
    /**
     * A download has failed.
     */
    data class Error(
        val title: String,
        val message: String
    ) : DownloadState()
}

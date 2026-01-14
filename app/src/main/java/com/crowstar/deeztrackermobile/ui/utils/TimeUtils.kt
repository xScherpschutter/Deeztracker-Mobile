package com.crowstar.deeztrackermobile.ui.utils

/**
 * Formats duration from seconds to "M:SS" format.
 * Example: 185 seconds -> "3:05"
 */
fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

/**
 * Formats time from milliseconds to "M:SS" format.
 * Example: 185000ms -> "3:05"
 */
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

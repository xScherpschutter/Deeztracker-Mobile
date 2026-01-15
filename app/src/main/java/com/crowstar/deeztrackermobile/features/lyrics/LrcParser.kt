package com.crowstar.deeztrackermobile.features.lyrics

data class LrcLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    
    private val LRC_REGEX = Regex("\\[(\\d+):(\\d+(\\.\\d+)?)\\](.*)")

    fun parse(lrcContent: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        
        lrcContent.lines().forEach { line ->
            val match = LRC_REGEX.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toDoubleOrNull() ?: 0.0
                val text = match.groupValues[4].trim()
                
                val timeMs = ((min * 60 + sec) * 1000).toLong()
                
                // Only add if text is not empty? Or keep empty lines for instrumental breaks?
                // Keeping it usually good practice.
                lines.add(LrcLine(timeMs, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    fun getActiveLineIndex(lyrics: List<LrcLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        
        // Find the last line that starts before or at current position
        // indexOfLast is O(N), for large lyrics binary search might be better but N is usually small (<100)
        // so linear scan is fine, or even `binarySearch`.
        
        // Using binary search approach for efficiency (standard for time-based lookups)
        // We want the element where timeMs <= positionMs and next element timeMs > positionMs
        
        var low = 0
        var high = lyrics.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) / 2
            val line = lyrics[mid]

            if (line.timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}

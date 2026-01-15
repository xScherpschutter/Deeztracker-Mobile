package com.crowstar.deeztrackermobile.features.lyrics

data class LrcLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    
    // Matches [mm:ss.xx] or [mm:ss]
    private val TIMESTAMP_REGEX = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]")

    fun parse(lrcContent: String): List<LrcLine> {
        if (lrcContent.isBlank()) return emptyList()

        // Debug log to see what we are parsing
        val preview = if (lrcContent.length > 200) lrcContent.take(200) + "..." else lrcContent
        android.util.Log.d("LrcParser", "Parsing content: $preview")

        // Sanitize: Handle literal \n text that sometimes comes from APIs
        val cleanContent = lrcContent.replace("\\n", "\n")

        val lines = ArrayList<LrcLine>()
        var foundSyncedLine = false

        cleanContent.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEach

            // Find all timestamps in the line (handles multiple timestamps like [00:12][00:24]chorus)
            val matches = TIMESTAMP_REGEX.findAll(trimmedLine).toList()
            
            if (matches.isNotEmpty()) {
                foundSyncedLine = true
                // The text is everything after the last timestamp
                val text = trimmedLine.substring(matches.last().range.last + 1).trim()
                
                for (match in matches) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val secStr = match.groupValues[2]
                    val sec = secStr.toDoubleOrNull() ?: 0.0
                    
                    val timeMs = ((min * 60 + sec) * 1000).toLong()
                    lines.add(LrcLine(timeMs, text))
                }
            }
        }

        // Fallback: If no timestamps were found, treat as plain lyrics
        if (!foundSyncedLine && lines.isEmpty()) {
            android.util.Log.d("LrcParser", "No timestamps found. Falling back to plain lyrics.")
            lrcContent.lines().forEach { line ->
                if (line.isNotBlank()) {
                    // Use MAX_VALUE so they are never "active" for auto-scroll
                    lines.add(LrcLine(Long.MAX_VALUE, line.trim()))
                }
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    fun getActiveLineIndex(lyrics: List<LrcLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        
        // If plain lyrics (MAX_VALUE), never highlight
        if (lyrics[0].timeMs == Long.MAX_VALUE) return -1

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

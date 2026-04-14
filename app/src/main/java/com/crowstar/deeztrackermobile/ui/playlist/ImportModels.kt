package com.crowstar.deeztrackermobile.ui.playlist

import com.crowstar.deeztrackermobile.features.localmusic.LocalTrack
import uniffi.rusteer.Track

data class ImportedTrackState(
    val rawQuery: String,
    val status: ImportStatus
)

sealed class ImportStatus {
    object Missing : ImportStatus()
    data class FoundLocally(val localTrack: LocalTrack) : ImportStatus()
    data class FoundOnDeezer(val track: Track) : ImportStatus()
    object NotFound : ImportStatus()
}

enum class InvalidPlaylistReason {
    EMPTY_FILE,
    CORRUPTED,
    INVALID_FORMAT
}

class InvalidPlaylistException(val reason: InvalidPlaylistReason, message: String) : Exception(message)

package com.crowstar.deeztrackermobile.features.localmusic

import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.crowstar.deeztrackermobile.features.deezer.Track
import com.crowstar.deeztrackermobile.features.download.DownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class LocalMusicRepository @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {
    private val contentResolver: ContentResolver = context.contentResolver

    private val _libraryUpdateTrigger = MutableStateFlow(0)
    val libraryUpdateTrigger: StateFlow<Int> = _libraryUpdateTrigger.asStateFlow()

    fun notifyLibraryChanged() {
        _libraryUpdateTrigger.update { it + 1 }
    }

    /**
     * Get all tracks from local music
     */
    suspend fun getAllTracks(): List<LocalTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<LocalTrack>()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown"
                val album = cursor.getString(albumColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val filePath = cursor.getString(dataColumn) ?: ""
                val size = cursor.getLong(sizeColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val mimeType = cursor.getString(mimeTypeColumn) ?: "audio/mpeg"
                val trackNumber = cursor.getInt(trackColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)

                if (filePath.isNotEmpty()) {
                    tracks.add(
                        LocalTrack(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            filePath = filePath,
                            size = size,
                            albumId = albumId,
                            albumArtUri = getAlbumArtUri(albumId),
                            mimeType = mimeType,
                            track = trackNumber,
                            dateAdded = dateAdded,
                            dateModified = dateModified
                        )
                    )
                }
            }
        }

        tracks
    }

    /**
     * Get all albums from local music
     */
    suspend fun getAllAlbums(): List<LocalAlbum> = withContext(Dispatchers.IO) {
        val allTracks = getAllTracks()
        allTracks.groupBy { it.albumId }.map { (albumId, tracks) ->
            val firstTrack = tracks.first()
            LocalAlbum(
                id = albumId,
                title = firstTrack.album,
                artist = firstTrack.artist,
                trackCount = tracks.size,
                albumArtUri = firstTrack.albumArtUri
            )
        }
    }

    /**
     * Get all artists from local music
     */
    suspend fun getAllArtists(): List<LocalArtist> = withContext(Dispatchers.IO) {
        val allTracks = getAllTracks()
        allTracks.groupBy { it.artist }.map { (artistName, tracks) ->
            val firstTrack = tracks.first()
            LocalArtist(
                id = firstTrack.id,
                name = artistName,
                numberOfTracks = tracks.size,
                numberOfAlbums = tracks.map { it.albumId }.distinct().size,
                artistArtUri = firstTrack.albumArtUri
            )
        }
    }

    /**
     * Get tracks for a specific album
     */
    suspend fun getTracksForAlbum(albumId: Long): List<LocalTrack> = withContext(Dispatchers.IO) {
        val allTracks = getAllTracks()
        allTracks.filter { it.albumId == albumId }
    }

    /**
     * Get tracks for a specific artist
     */
    suspend fun getTracksForArtist(artistName: String): List<LocalTrack> = withContext(Dispatchers.IO) {
        val allTracks = getAllTracks()
        allTracks.filter { it.artist == artistName }
    }

    /**
     * Get the album art URI for a given album ID
     */
    fun getAlbumArtUri(albumId: Long): String {
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        ).toString()
    }

    /**
     * Request deletion of a single track (Android 10+)
     */
    suspend fun requestDeleteTrack(trackId: Long): IntentSender? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
            MediaStore.createDeleteRequest(contentResolver, listOf(uri)).intentSender
        } else null
    }

    /**
     * For Android 10+ delete requests (multiple)
     */
    suspend fun requestDeleteTracks(ids: List<Long>): IntentSender? = withContext(Dispatchers.IO) {
        val uris = ids.map { id -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(contentResolver, uris).intentSender
        } else null
    }

    suspend fun findIdForPath(path: String): Long? = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(path)

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            } else null
        }
    }

    suspend fun getDownloadedTracks(downloadPaths: List<String>): List<LocalTrack> = withContext(Dispatchers.IO) {
        val allTracks = getAllTracks()
        if (downloadPaths.isEmpty()) return@withContext emptyList()
        
        allTracks.filter { track -> 
            downloadPaths.any { path -> 
                track.filePath.contains(path, ignoreCase = true) 
            } 
        }
    }

    suspend fun deleteTrack(track: LocalTrack): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(track.filePath)
            if (file.exists()) {
                file.delete()
            }
            contentResolver.delete(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(track.id.toString())
            )
            notifyLibraryChanged()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getTrackIdByPath(path: String): Long? {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(path)

        return contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            } else null
        }
    }

    /**
     * Get total storage space on the device
     */
    fun getTotalStorageSpace(): Long {
        return context.getExternalFilesDir(null)?.totalSpace ?: 0L
    }
}

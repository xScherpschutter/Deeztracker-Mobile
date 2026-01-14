package com.crowstar.deeztrackermobile.features.localmusic

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for accessing local music files using Android MediaStore API
 */
class LocalMusicRepository(private val contentResolver: ContentResolver) {

    /**
     * Get all music tracks from device storage
     */
    suspend fun getAllTracks(): List<LocalTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<LocalTrack>()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR
        )

        // Filter: only music files (not notifications, ringtones, etc.)
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
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)
                
                // Get album art URI
                val albumArtUri = getAlbumArtUri(albumId)

                tracks.add(
                    LocalTrack(
                        id = id,
                        title = cursor.getString(titleColumn) ?: "Unknown",
                        artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                        album = cursor.getString(albumColumn) ?: "Unknown Album",
                        albumId = albumId,
                        duration = cursor.getLong(durationColumn),
                        filePath = cursor.getString(dataColumn) ?: "",
                        size = cursor.getLong(sizeColumn),
                        mimeType = cursor.getString(mimeTypeColumn) ?: "",
                        dateAdded = cursor.getLong(dateAddedColumn),
                        dateModified = cursor.getLong(dateModifiedColumn),
                        albumArtUri = albumArtUri,
                        track = cursor.getInt(trackColumn),
                        year = cursor.getInt(yearColumn)
                    )
                )
            }
        }

        tracks
    }

    /**
     * Get all albums from local music
     */
    suspend fun getAllAlbums(): List<LocalAlbum> = withContext(Dispatchers.IO) {
        val albums = mutableListOf<LocalAlbum>()
        
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS
        )

        val sortOrder = "${MediaStore.Audio.Albums.ALBUM} ASC"

        contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val numberOfSongsColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                
                albums.add(
                    LocalAlbum(
                        id = id,
                        title = cursor.getString(albumColumn) ?: "Unknown Album",
                        artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                        trackCount = cursor.getInt(numberOfSongsColumn),
                        albumArtUri = getAlbumArtUri(id)
                    )
                )
            }
        }

        albums
    }

    /**
     * Get tracks for a specific album
     */
    suspend fun getTracksForAlbum(albumId: Long): List<LocalTrack> = withContext(Dispatchers.IO) {
        val tracks = getAllTracks()
        tracks.filter { it.albumId == albumId }
    }

    /**
     * Search tracks by query (title, artist, or album)
     */
    suspend fun searchTracks(query: String): List<LocalTrack> = withContext(Dispatchers.IO) {
        val tracks = getAllTracks()
        tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
        }
    }

    /**
     * Get album art URI for an album
     */
    private fun getAlbumArtUri(albumId: Long): String? {
        return try {
            val artworkUri = Uri.parse("content://media/external/audio/albumart")
            ContentUris.withAppendedId(artworkUri, albumId).toString()
        } catch (e: Exception) {
            null
        }
    }
}

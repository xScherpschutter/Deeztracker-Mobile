package com.crowstar.deeztrackermobile.features.localmusic

import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Build
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
     * Get all artists from local music
     */
    suspend fun getAllArtists(): List<LocalArtist> = withContext(Dispatchers.IO) {
        val artists = mutableListOf<LocalArtist>()
        
        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS
        )

        val sortOrder = "${MediaStore.Audio.Artists.ARTIST} ASC"

        contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            val numberOfTracksColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
            val numberOfAlbumsColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)

            while (cursor.moveToNext()) {
                artists.add(
                    LocalArtist(
                        id = cursor.getLong(idColumn),
                        name = cursor.getString(artistColumn) ?: "Unknown Artist",
                        numberOfTracks = cursor.getInt(numberOfTracksColumn),
                        numberOfAlbums = cursor.getInt(numberOfAlbumsColumn)
                    )
                )
            }
        }

        artists
    }

    /**
     * Get tracks for a specific album
     */
    suspend fun getTracksForAlbum(albumId: Long): List<LocalTrack> = withContext(Dispatchers.IO) {
        val tracks = getAllTracks()
        tracks.filter { it.albumId == albumId }
    }

    /**
     * Get tracks for a specific artist
     */
    suspend fun getTracksForArtist(artistName: String): List<LocalTrack> = withContext(Dispatchers.IO) {
        val tracks = getAllTracks()
        tracks.filter { it.artist == artistName }
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
     * Request deletion of a track
     * Returns an IntentSender to ask for user permission if needed (Android 10+), or null if deleted directly/failed.
     */
    suspend fun requestDeleteTrack(trackId: Long): IntentSender? = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
            return@withContext pendingIntent.intentSender
        } else {
            // Android 10 and below
            try {
                contentResolver.delete(uri, null, null)
                // If successful, return null (no intent needed)
                return@withContext null
            } catch (securityException: android.app.RecoverableSecurityException) {
                // Android 10 specific scoped storage exception
                return@withContext securityException.userAction.actionIntent.intentSender
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
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
    /**
     * Get tracks located in the specific download directory
     * This uses in-memory filtering for simplicity as getAllTracks is already optimal.
     */
    suspend fun getDownloadedTracks(downloadPath: String): List<LocalTrack> = withContext(Dispatchers.IO) {
        val allTracks = getAllTracks()
        allTracks.filter { it.filePath.startsWith(downloadPath) }
    }
}

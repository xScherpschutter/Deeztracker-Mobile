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

@Singleton
class LocalMusicRepository @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {
    private val contentResolver: ContentResolver = context.contentResolver

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
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val filePath = cursor.getString(dataColumn)
                val size = cursor.getLong(sizeColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val mimeType = cursor.getString(mimeTypeColumn) ?: "audio/mpeg"
                val trackNumber = cursor.getInt(trackColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)

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
            val countColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                albums.add(
                    LocalAlbum(
                        id = id,
                        title = cursor.getString(albumColumn) ?: "Unknown Album",
                        artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                        trackCount = cursor.getInt(countColumn),
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

            val artistArtMap = getEfficientArtistArtMap()

            while (cursor.moveToNext()) {
                val artistName = cursor.getString(artistColumn) ?: "Unknown Artist"
                artists.add(
                    LocalArtist(
                        id = cursor.getLong(idColumn),
                        name = artistName,
                        numberOfTracks = cursor.getInt(numberOfTracksColumn),
                        numberOfAlbums = cursor.getInt(numberOfAlbumsColumn),
                        artistArtUri = artistArtMap[artistName]
                    )
                )
            }
        }

        artists
    }

    private fun getEfficientArtistArtMap(): Map<String, String> {
        val artMap = mutableMapOf<String, String>()
        val projection = arrayOf(
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums._ID
        )
        
        contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            null, null, null
        )?.use { cursor ->
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            
            while (cursor.moveToNext()) {
                val artist = cursor.getString(artistCol) ?: continue
                if (!artMap.containsKey(artist)) {
                    val albumId = cursor.getLong(idCol)
                    getAlbumArtUri(albumId)?.let { artMap[artist] = it }
                }
            }
        }
        return artMap
    }

    private fun getAlbumArtUri(albumId: Long): String? {
        return try {
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            ).toString()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTracksForAlbum(albumId: Long): List<LocalTrack> = withContext(Dispatchers.IO) {
        val tracks = getAllTracks()
        tracks.filter { it.albumId == albumId }
    }

    suspend fun getTracksForArtist(artistName: String): List<LocalTrack> = withContext(Dispatchers.IO) {
        val tracks = getAllTracks()
        tracks.filter { it.artist == artistName }
    }

    suspend fun searchLocalMusic(query: String): List<LocalTrack> = withContext(Dispatchers.IO) {
        val allTracks = getAllTracks()
        if (query.isBlank()) return@withContext allTracks
        
        allTracks.filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.artist.contains(query, ignoreCase = true) || 
            it.album.contains(query, ignoreCase = true)
        }
    }

    suspend fun getDownloadedTracks(downloadPaths: List<String>): List<LocalTrack> = withContext(Dispatchers.IO) {
        val allTracks = getAllTracks()
        allTracks.filter { track -> downloadPaths.any { path -> track.filePath.endsWith(path) } }
    }

    suspend fun deleteTrack(track: LocalTrack): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(track.filePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    contentResolver.delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Audio.Media._ID} = ?",
                        arrayOf(track.id.toString())
                    )
                }
                deleted
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * For Android 10+ delete requests
     */
    suspend fun requestDeleteTrack(id: Long): IntentSender? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            MediaStore.createDeleteRequest(contentResolver, listOf(uri)).intentSender
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
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                return@withContext cursor.getLong(idColumn)
            }
        }
        return@withContext null
    }

    /**
     * Alias for findIdForPath to support older calls
     */
    suspend fun getTrackIdByPath(path: String): Long? = findIdForPath(path)

    /**
     * Get total storage space on the device
     */
    fun getTotalStorageSpace(): Long {
        return context.getExternalFilesDir(null)?.totalSpace ?: 0L
    }
}

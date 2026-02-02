package com.crowstar.deeztrackermobile.features.localmusic

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

data class TrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val year: String
)

class MetadataEditor(private val context: Context) {

    companion object {
        private const val TAG = "MetadataEditor"
    }

    fun readMetadata(filePath: String): TrackMetadata? {
        return try {
            Log.d(TAG, "Reading metadata from: $filePath")
            val file = File(filePath)
            
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: $filePath")
                return null
            }
            
            if (!file.canRead()) {
                Log.e(TAG, "Cannot read file: $filePath")
                return null
            }
            
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            
            val metadata = TrackMetadata(
                title = tag.getFirst(FieldKey.TITLE) ?: "",
                artist = tag.getFirst(FieldKey.ARTIST) ?: "",
                album = tag.getFirst(FieldKey.ALBUM) ?: "",
                year = tag.getFirst(FieldKey.YEAR) ?: ""
            )
            
            Log.d(TAG, "Successfully read metadata: title=${metadata.title}, artist=${metadata.artist}")
            metadata
        } catch (e: Exception) {
            Log.e(TAG, "Error reading metadata from $filePath", e)
            e.printStackTrace()
            null
        }
    }

    fun writeMetadata(
        filePath: String, 
        metadata: TrackMetadata, 
        onScanComplete: (() -> Unit)? = null
    ): Boolean {
        return try {
            Log.d(TAG, "Writing metadata to: $filePath")
            Log.d(TAG, "New metadata: title=${metadata.title}, artist=${metadata.artist}, album=${metadata.album}, year=${metadata.year}")
            
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: $filePath")
                return false
            }
            
            // Read the audio file
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            
            // Update metadata fields
            tag.setField(FieldKey.TITLE, metadata.title)
            tag.setField(FieldKey.ARTIST, metadata.artist)
            tag.setField(FieldKey.ALBUM, metadata.album)
            
            try {
                tag.setField(FieldKey.YEAR, metadata.year)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set YEAR field: ${e.message}")
            }
            
            // Write changes directly to file
            AudioFileIO.write(audioFile)
            Log.d(TAG, "Successfully wrote metadata to file")
            
            // Scan the file to update MediaStore
            MediaScannerConnection.scanFile(
                context,
                arrayOf(filePath),
                null
            ) { path, scannedUri ->
                Log.d(TAG, "MediaScanner completed for: $path, URI: $scannedUri")
                onScanComplete?.invoke()
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing metadata to $filePath", e)
            e.printStackTrace()
            false
        }
    }
}

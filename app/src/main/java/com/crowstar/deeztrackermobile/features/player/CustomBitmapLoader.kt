package com.crowstar.deeztrackermobile.features.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Custom BitmapLoader for Media3 that prevents artwork caching issues
 * and ensures the default icon is shown when tracks have no album art.
 */
@UnstableApi
class CustomBitmapLoader(private val context: Context) : BitmapLoader {
    
    private val defaultIcon by lazy {
        BitmapFactory.decodeResource(
            context.resources,
            com.crowstar.deeztrackermobile.R.drawable.ic_app_icon
        )
    }
    
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                future.set(bitmap ?: defaultIcon)
            } catch (e: Exception) {
                future.set(defaultIcon)
            }
        }
        return future
    }
    
    override fun loadBitmap(uri: Uri, options: BitmapFactory.Options?): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val bitmap = when (uri.scheme) {
                    "content" -> {
                        // Load from content URI (album art)
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            if (options != null) {
                                BitmapFactory.decodeStream(inputStream, null, options)
                            } else {
                                BitmapFactory.decodeStream(inputStream)
                            }
                        }
                    }
                    "android.resource" -> {
                        // This is our default icon - use the pre-loaded one
                        defaultIcon
                    }
                    else -> null
                }
                
                future.set(bitmap ?: defaultIcon)
            } catch (e: Exception) {
                // On any error, use default icon
                e.printStackTrace()
                future.set(defaultIcon)
            }
        }
        
        return future
    }
}

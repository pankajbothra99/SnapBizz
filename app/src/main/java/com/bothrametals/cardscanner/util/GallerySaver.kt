package com.bothrametals.cardscanner.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Copies card photos into the phone's Gallery, in the album "Card Scanner" (Pictures/Card Scanner). */
object GallerySaver {

    /** Returns true if the photo was saved. */
    fun save(context: Context, source: File, fileName: String): Boolean = runCatching {
        if (!source.exists()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + GalleryNames.ALBUM)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: return false
            val ok = resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) }; true } ?: false
            if (!ok) {
                resolver.delete(uri, null, null)
                return false
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), GalleryNames.ALBUM)
            if (!dir.exists() && !dir.mkdirs()) return false
            var dest = File(dir, fileName)
            var n = 2
            while (dest.exists()) dest = File(dir, fileName.removeSuffix(".jpg") + " ($n).jpg").also { n++ }
            source.copyTo(dest)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf("image/jpeg"), null)
            true
        }
    }.getOrDefault(false)
}

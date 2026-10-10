package com.bothrametals.cardscanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {

    /** Copies [uri] to [dest] as a JPEG no bigger than [maxDim] px on the long side, upright. */
    fun copyAndShrink(context: Context, uri: Uri, dest: File, maxDim: Int = 2000) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("Could not open the image")

        val rotation = runCatching { resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } }.getOrNull() ?: 0
        var bmp = scaleDown(decoded, maxDim)
        if (rotation != 0) bmp = rotate(bmp, rotation)
        FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    fun load(file: File): Bitmap? = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()

    /** The card centred on a white square, small enough to be a contact photo. */
    fun contactPhoto(file: File, size: Int = 480): ByteArray? {
        val src = load(file) ?: return null
        val scale = size.toFloat() / max(src.width, src.height)
        val w = (src.width * scale).roundToInt().coerceAtLeast(1)
        val h = (src.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(square).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, ((size - w) / 2f), ((size - h) / 2f), null)
        }
        return ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, 82, out)
            out.toByteArray()
        }
    }

    private fun exifRotation(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun scaleDown(src: Bitmap, maxDim: Int): Bitmap {
        val longSide = max(src.width, src.height)
        if (longSide <= maxDim) return src
        val s = maxDim.toFloat() / longSide
        return Bitmap.createScaledBitmap(src, (src.width * s).roundToInt(), (src.height * s).roundToInt(), true)
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}

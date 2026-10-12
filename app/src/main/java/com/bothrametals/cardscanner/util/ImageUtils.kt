package com.bothrametals.cardscanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
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

    /** The card photo as base64 JPEG for the AI (long side at most [maxDim] px). */
    fun base64Jpeg(file: File, maxDim: Int = 2048, quality: Int = 85): String? {
        val src = load(file) ?: return null
        val scaled = scaleDown(src, maxDim)
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    /** A smaller copy of the card photo for the cloud backup (kept well under the 1 MB limit per photo). */
    fun cloudJpeg(file: File): ByteArray? {
        val src = load(file) ?: return null
        for ((dim, q) in listOf(1200 to 70, 1000 to 62, 800 to 55)) {
            val bytes = ByteArrayOutputStream().use { out ->
                scaleDown(src, dim).compress(Bitmap.CompressFormat.JPEG, q, out)
                out.toByteArray()
            }
            if (bytes.size < 700_000) return bytes
        }
        return null
    }

    /** Black-and-white QR code image for [text]. */
    fun qrBitmap(text: String, size: Int = 720): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
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

package com.bothrametals.cardscanner.util

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await

/** Finds QR codes on a card photo with Google ML Kit, fully on the phone (free, no internet needed). */
class QrReader {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_AZTEC)
            .build()
    )

    /** The text held by each QR code on the photo (usually none or one). Never fails. */
    suspend fun read(bitmap: Bitmap): List<String> = try {
        scanner.process(InputImage.fromBitmap(bitmap, 0)).await()
            .mapNotNull { it.rawValue?.trim()?.takeIf { v -> v.isNotEmpty() } }
            .distinct()
    } catch (e: Exception) {
        emptyList()
    }

    fun close() = scanner.close()
}

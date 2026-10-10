package com.bothrametals.cardscanner.util

import android.graphics.Bitmap
import com.bothrametals.cardscanner.parser.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Reads text from a card photo using Google ML Kit, fully on the phone (no internet needed). */
class OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Returns the card's lines in reading order. Tries other rotations if the card was photographed sideways. */
    suspend fun read(bitmap: Bitmap, side: Int): List<OcrLine> {
        var best = recognize(bitmap, 0, side)
        if (letterCount(best) < 25) {
            for (rotation in listOf(90, 270, 180)) {
                val attempt = recognize(bitmap, rotation, side)
                if (letterCount(attempt) > letterCount(best) * 1.3 + 5) best = attempt
            }
        }
        return best
    }

    private suspend fun recognize(bitmap: Bitmap, rotation: Int, side: Int): List<OcrLine> {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, rotation)).await()
        val uprightHeight = if (rotation == 90 || rotation == 270) bitmap.width else bitmap.height
        return result.textBlocks.flatMap { block ->
            block.lines.map { line ->
                val h = line.boundingBox?.height() ?: 0
                // Height relative to the card (per mille) so front and back photos are comparable
                OcrLine(line.text, (h * 1000L / uprightHeight.coerceAtLeast(1)).toInt(), side)
            }
        }
    }

    private fun letterCount(lines: List<OcrLine>) = lines.sumOf { l -> l.text.count { it.isLetterOrDigit() } }

    fun close() = recognizer.close()
}

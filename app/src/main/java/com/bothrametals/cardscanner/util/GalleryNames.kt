package com.bothrametals.cardscanner.util

import com.bothrametals.cardscanner.parser.CardFields
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** File names for card photos in the Gallery, e.g. "Pankaj Bothra - Bothra Metals - Front - 11 Oct 2026.jpg". Pure Kotlin. */
object GalleryNames {

    const val ALBUM = "Card Scanner"

    fun fileName(f: CardFields, back: Boolean, scannedAt: Long): String {
        val parts = listOf(f.name, f.company.ifBlank { f.emails.firstOrNull().orEmpty() })
            .map { clean(it) }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("Business card") }
        val date = SimpleDateFormat("dd MMM yyyy HHmm", Locale.ENGLISH).format(Date(scannedAt))
        return (parts + (if (back) "Back" else "Front") + date).joinToString(" - ").take(120) + ".jpg"
    }

    /** Removes characters that aren't allowed in file names. */
    fun clean(s: String): String = s
        .replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(50)
        .trim()
}

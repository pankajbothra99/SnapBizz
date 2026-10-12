package com.bothrametals.cardscanner.util

import com.bothrametals.cardscanner.parser.CardFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GalleryNamesTest {

    private val time = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 11, 18, 5) }.timeInMillis

    @Test
    fun nameAndCompany() {
        val n = GalleryNames.fileName(CardFields(name = "Pankaj Bothra", company = "Bothra Metals & Alloys Ltd."), back = false, scannedAt = time)
        assertEquals("Pankaj Bothra - Bothra Metals & Alloys Ltd. - Front - 11 Oct 2026 1805.jpg", n)
    }

    @Test
    fun badCharactersRemovedAndFallback() {
        val n = GalleryNames.fileName(CardFields(name = "A/B: \"C\"", company = ""), back = true, scannedAt = time)
        assertTrue(n, !n.contains('/') && !n.contains(':') && !n.contains('"'))
        assertTrue(n, n.contains("- Back -"))
        val empty = GalleryNames.fileName(CardFields(), back = false, scannedAt = time)
        assertTrue(empty, empty.startsWith("Business card - Front"))
    }
}

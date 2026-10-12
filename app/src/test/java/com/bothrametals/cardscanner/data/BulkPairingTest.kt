package com.bothrametals.cardscanner.data

import com.bothrametals.cardscanner.data.BulkPairing.Photo
import org.junit.Assert.assertEquals
import org.junit.Test

class BulkPairingTest {

    private val card = true
    private val back = false

    @Test
    fun backSideJoinsThePhotoBefore() {
        val groups = BulkPairing.pair(listOf(Photo(card), Photo(back), Photo(card), Photo(card), Photo(back)))
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3, 4)), groups)
    }

    @Test
    fun logoOnlyFrontJoinsTheCloserPhotoInTime() {
        // Card A at 10:00. Then card B: logo side at 10:10, details side 10 seconds later.
        val groups = BulkPairing.pair(listOf(
            Photo(card, 0L), Photo(back, 600_000L), Photo(card, 610_000L),
        ))
        assertEquals(listOf(listOf(0), listOf(2, 1)), groups)
    }

    @Test
    fun photosFarApartAreNotPaired() {
        val groups = BulkPairing.pair(listOf(Photo(card, 0L), Photo(back, 3_600_000L)))
        assertEquals(listOf(listOf(0), listOf(1)), groups)
    }

    @Test
    fun allCardsStaySeparate() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), BulkPairing.pair(listOf(Photo(card), Photo(card), Photo(card))))
    }

    @Test
    fun aCardGetsOnlyOneBack() {
        val groups = BulkPairing.pair(listOf(Photo(card), Photo(back), Photo(back)))
        assertEquals(listOf(listOf(0, 1), listOf(2)), groups)
    }

    @Test
    fun emptyAndSingle() {
        assertEquals(emptyList<List<Int>>(), BulkPairing.pair(emptyList()))
        assertEquals(listOf(listOf(0)), BulkPairing.pair(listOf(Photo(back))))
    }
}

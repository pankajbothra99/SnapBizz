package com.bothrametals.cardscanner.data

import kotlin.math.abs

/**
 * Bulk import from the Gallery: decides which photos are the back of which card.
 *
 * A photo with no phone number or email (a logo, a product list…) is treated as the back side
 * of the neighbouring card photo – the one taken closest in time (or the one just before it if
 * the time is unknown). Photos taken more than [MAX_GAP_MS] apart are never paired.
 * Pure Kotlin so it can be unit tested.
 */
object BulkPairing {

    const val MAX_GAP_MS = 5 * 60_000L

    class Photo(val hasContact: Boolean, val takenAt: Long? = null)

    /** Returns groups of photo indexes; the first index of each group is the front of the card. */
    fun pair(photos: List<Photo>): List<List<Int>> {
        val n = photos.size
        val backOf = IntArray(n) { -1 }      // for a card photo: index of its back side
        val attachedTo = IntArray(n) { -1 }  // for a back-side photo: index of its card photo

        fun gap(a: Int, b: Int): Long? {
            val x = photos[a].takenAt ?: return null
            val y = photos[b].takenAt ?: return null
            return abs(x - y)
        }

        for (i in 0 until n) {
            if (photos[i].hasContact) continue
            val candidates = listOf(i - 1, i + 1).filter { it in 0 until n && photos[it].hasContact && backOf[it] == -1 }
            if (candidates.isEmpty()) continue
            val best = if (candidates.size == 2) {
                val gPrev = gap(i, candidates[0])
                val gNext = gap(i, candidates[1])
                if (gPrev != null && gNext != null && gNext < gPrev) candidates[1] else candidates[0]
            } else candidates[0]
            val g = gap(i, best)
            if (g != null && g > MAX_GAP_MS) continue
            backOf[best] = i
            attachedTo[i] = best
        }

        val groups = ArrayList<List<Int>>()
        for (i in 0 until n) {
            when {
                photos[i].hasContact -> groups += listOfNotNull(i, backOf[i].takeIf { it >= 0 })
                attachedTo[i] == -1 -> groups += listOf(i) // no details and no partner: still imported as its own card
            }
        }
        return groups
    }
}

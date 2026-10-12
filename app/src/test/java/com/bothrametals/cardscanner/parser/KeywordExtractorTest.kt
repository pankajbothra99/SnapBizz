package com.bothrametals.cardscanner.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordExtractorTest {

    @Test
    fun findsBusinessLineAndKeywordsOnPhone() {
        val card = CardParser.parse(listOf(
            OcrLine("KRISHNA RECYCLERS", 40),
            OcrLine("Dealers in Aluminium, Copper & Brass Scrap", 18),
            OcrLine("Amit Gupta", 30),
            OcrLine("Partner", 18),
            OcrLine("+91 77001 12233", 14),
        ))
        assertEquals("Dealers in Aluminium, Copper & Brass Scrap", card.business)
        for (k in listOf("aluminium", "aluminum", "copper", "brass", "scrap", "recycling", "dealer")) {
            assertTrue("keyword $k in ${card.keywords}", k in card.keywords)
        }
        assertTrue("business line not repeated in other text", !card.extraInfo.contains("Dealers in"))
    }

    @Test
    fun gradesAndProducts() {
        val k = KeywordExtractor.keywords(listOf("Manufacturers of ADC-12 & LM6 alloy ingots and die castings"))
        assertTrue(k.toString(), k.containsAll(listOf("ADC12", "LM6", "alloys", "ingots", "die casting", "castings", "manufacturer")))
    }

    @Test
    fun noFalseHitsInsideWords() {
        val k = KeywordExtractor.keywords(listOf("Platinum Tower, Leadership Road"))
        assertTrue(k.toString(), "lead" !in k && "tin" !in k)
    }

    @Test
    fun cleanRemovesDuplicatesAndJunk() {
        assertEquals(listOf("aluminium", "Scrap", "ADC12"), KeywordExtractor.clean(listOf("aluminium", "Aluminium", " Scrap ", "x", "ADC12, scrap")))
    }
}

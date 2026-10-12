package com.bothrametals.cardscanner.data

import com.bothrametals.cardscanner.parser.CardFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubGroupsTest {

    private val scrap = SubGroup("sg_scrap", "Scrap", 1)
    private val finished = SubGroup("sg_fin", "Finished products", 2)
    private val ingots = SubGroup("sg_ing", "Ingots", 3)
    private val event = EventGroup("ev1", "Aluminium Expo", subGroups = listOf(finished, scrap, ingots))
    private fun card(vararg ids: String) = CardRecord("c1", 1, 1, null, null, emptyList(), CardFields(name = "A B"), eventId = "ev1", subGroupIds = ids.toList())

    @Test
    fun namesFromAiAreMatchedLoosely() {
        assertEquals(listOf("sg_scrap", "sg_fin"), SubGroups.idsFromNames(listOf("scrap", " Finished  Products ", "Machinery", "SCRAP"), event.subGroups))
        assertEquals("Scrap", SubGroups.findByName(event.subGroups, "  scrap ")?.name)
        assertEquals("Scrap buyers", SubGroups.cleanName("  Scrap   buyers "))
    }

    @Test
    fun offlineGuessUsesMeaningfulWords() {
        val dealer = CardFields(company = "Gupta Metals", business = "Dealers in aluminium & copper scrap", keywords = listOf("scrap", "copper"))
        assertEquals(listOf("sg_scrap"), SubGroups.suggestOffline(dealer, event.subGroups))
        val maker = CardFields(company = "Apex Alloys", business = "Manufacturers of aluminium ingot and finished castings")
        assertEquals("singular 'ingot' matches 'Ingots'; 'finished' matches", setOf("sg_fin", "sg_ing"), SubGroups.suggestOffline(maker, event.subGroups).toSet())
        val generic = CardFields(company = "XYZ Products", business = "Suppliers of industrial goods")
        assertTrue("generic words alone don't decide", SubGroups.suggestOffline(generic, event.subGroups).isEmpty())
    }

    @Test
    fun toggleKeepsEventOrderAndRefreshesNames() {
        val a = SubGroups.toggle(card(), "sg_scrap", event)
        val b = SubGroups.toggle(a, "sg_fin", event)
        assertEquals(listOf("sg_fin", "sg_scrap"), b.subGroupIds)
        assertEquals(listOf("Finished products", "Scrap"), b.subGroupNames)
        assertEquals(listOf("sg_fin"), SubGroups.toggle(b, "sg_scrap", event).subGroupIds)
    }

    @Test
    fun deletedOrRenamedSubGroupsShowCorrectly() {
        val c = card("sg_scrap", "sg_gone").copy(subGroupNames = listOf("Old scrap name", "Gone"))
        assertEquals(listOf("Scrap"), SubGroups.namesFor(c, event))
        assertEquals("without the event, the saved names show", listOf("Old scrap name", "Gone"), SubGroups.namesFor(c, null))
        assertTrue(SubGroups.matches(c, "sg_scrap", event))
        assertTrue(SubGroups.matches(card("sg_gone"), SubGroups.NOT_SORTED, event))
        assertTrue(SubGroups.matches(c, "", event))
    }
}

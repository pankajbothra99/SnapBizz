package com.bothrametals.cardscanner.data

import com.bothrametals.cardscanner.cloud.CloudMapper
import com.bothrametals.cardscanner.parser.CardFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatesTest {

    private fun card(id: String, f: CardFields, event: String = "", at: Long = 1) =
        CardRecord(id, at, at, null, null, emptyList(), f, eventId = event, scannedBy = "Ramesh")

    private val existing = listOf(
        card("old1", CardFields(name = "Rajesh Agarwal", company = "Shree Ganesh Aluminium Pvt Ltd", mobiles = listOf("+91 98765 43210"))),
        card("old2", CardFields(name = "Neha Kapoor", company = "Zenith Metals", emails = listOf("neha@zenithmetals.in")), event = "expo"),
        card("old3", CardFields(name = "Amit Gupta", company = "Krishna Recyclers")),
    )

    @Test
    fun sameMobileWrittenDifferently() {
        val m = Duplicates.find(card("new", CardFields(name = "R. Agarwal", mobiles = listOf("098765 43210"))), existing)
        assertEquals("old1", m?.card?.id)
        assertEquals("same mobile number", m?.reason)
    }

    @Test
    fun sameEmailIgnoringCase() {
        val m = Duplicates.find(card("new", CardFields(emails = listOf("Neha@ZenithMetals.in")), event = "expo"), existing)
        assertEquals("old2", m?.card?.id)
        assertTrue(m!!.sameEvent)
    }

    @Test
    fun sameNameAndCompanyWithoutNumbers() {
        val m = Duplicates.find(card("new", CardFields(name = "AMIT GUPTA", company = "KRISHNA RECYCLERS & CO")), existing)
        assertEquals("old3", m?.card?.id)
    }

    @Test
    fun differentPersonSameCompanyIsNotDuplicate() {
        assertEquals(null, Duplicates.find(card("new", CardFields(name = "Sunil Gupta", company = "Krishna Recyclers")), existing))
    }

    @Test
    fun cardIsNotItsOwnDuplicate() {
        assertEquals(null, Duplicates.find(existing[0], existing))
    }

    @Test
    fun eventRoundTrip() {
        val e = EventGroup("ev1", "Aluminium India 2026", "Mumbai", "pankaj@bothrametals.com",
            listOf("Pankaj@BothraMetals.com", "staff@bothrametals.com"), closed = false, createdAt = 5, updatedAt = 6)
        val back = CloudMapper.eventFromMap(CloudMapper.eventToMap(e))!!
        assertEquals(listOf("pankaj@bothrametals.com", "staff@bothrametals.com"), back.members)
        assertEquals("Aluminium India 2026 · Mumbai", back.label)
        assertTrue(back.canManage("PANKAJ@bothrametals.com", isAdmin = false))
        assertTrue(!back.canManage("staff@bothrametals.com", isAdmin = false))
    }

    @Test
    fun cardKeepsEventThroughCloud() {
        val c = card("c", CardFields(name = "X"), event = "ev1").copy(eventName = "Aluminium India 2026")
        val r = CloudMapper.fromMap(CloudMapper.toMap(c))!!.record
        assertEquals("ev1", r.eventId)
        assertEquals("Aluminium India 2026", r.eventName)
    }
}

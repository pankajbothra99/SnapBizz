package com.bothrametals.cardscanner.cloud

import com.bothrametals.cardscanner.data.CardRecord
import com.bothrametals.cardscanner.data.EventGroup
import com.bothrametals.cardscanner.data.SubGroup
import com.bothrametals.cardscanner.parser.CardFields
import com.bothrametals.cardscanner.parser.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudMapperTest {

    private fun card(updated: Long = 100, dirty: Boolean = false, owner: String = "pankaj@bothrametals.com", vis: String = "company") = CardRecord(
        id = "c1", createdAt = 50, updatedAt = updated, frontImage = "/f.jpg", backImage = null,
        rawLines = listOf(OcrLine("BOTHRA METALS", 40, 0)),
        fields = CardFields(name = "Pankaj Bothra", mobiles = listOf("+91 98140 12345"), notes = "met at expo"),
        savedToContacts = true, readBy = "ai", uncertain = listOf("gstin"), scannedBy = "Pankaj",
        ownerEmail = owner, visibility = vis, cloudDirty = dirty,
    )

    /** Simulates what Firestore gives back: whole numbers as Long, lists as List<Any>. */
    private fun roundTrip(m: Map<String, Any?>): Map<String, Any?> = m.mapValues { (_, v) ->
        when (v) {
            is Int -> v.toLong()
            else -> v
        }
    }

    @Test
    fun roundTripKeepsEverythingButPhoneOnlyData() {
        val original = card()
        val remote = CloudMapper.fromMap(roundTrip(CloudMapper.toMap(original)))!!
        assertEquals(false, remote.deleted)
        assertEquals(true, remote.hasFront)
        assertEquals(false, remote.hasBack)
        val r = remote.record
        assertEquals("Pankaj Bothra", r.fields.name)
        assertEquals(listOf("+91 98140 12345"), r.fields.mobiles)
        assertEquals("met at expo", r.fields.notes)
        assertEquals(listOf(OcrLine("BOTHRA METALS", 40, 0)), r.rawLines)
        assertEquals("pankaj@bothrametals.com", r.ownerEmail)
        assertEquals("ai", r.readBy)
        assertEquals(listOf("gstin"), r.uncertain)
        assertEquals("phone-only data is not sent", false, r.savedToContacts)
        assertEquals(null, r.frontImage)
    }

    @Test
    fun mergeKeepsLocalPhotoAndContactsFlag() {
        val local = card(updated = 100)
        val remote = CloudMapper.fromMap(CloudMapper.toMap(card(updated = 200).copy(fields = CardFields(name = "Edited"))))!!
        assertEquals(CloudMapper.Action.TAKE_REMOTE, CloudMapper.decide(local, remote))
        val merged = CloudMapper.mergeRemoteIntoLocal(local, remote.record)
        assertEquals("Edited", merged.fields.name)
        assertEquals("/f.jpg", merged.frontImage)
        assertEquals(true, merged.savedToContacts)
        assertEquals(false, merged.cloudDirty)
    }

    @Test
    fun unsentNewerLocalEditWins() {
        val local = card(updated = 300, dirty = true)
        val remote = CloudMapper.fromMap(CloudMapper.toMap(card(updated = 200)))!!
        assertEquals(CloudMapper.Action.KEEP_LOCAL, CloudMapper.decide(local, remote))
    }

    @Test
    fun deletedInCloudRemovesFromPhone() {
        val local = card(updated = 100)
        val tomb = CloudMapper.fromMap(CloudMapper.tombstone(local, 500))!!
        assertTrue(tomb.deleted)
        assertEquals(CloudMapper.Action.DELETE_LOCAL, CloudMapper.decide(local, tomb))
        assertEquals("unknown deleted card: nothing to do", CloudMapper.Action.KEEP_LOCAL, CloudMapper.decide(null, tomb))
    }

    @Test
    fun newCardFromColleagueIsAdded() {
        val remote = CloudMapper.fromMap(CloudMapper.toMap(card(owner = "staff@bothrametals.com")))!!
        assertEquals(CloudMapper.Action.TAKE_REMOTE, CloudMapper.decide(null, remote))
    }

    @Test
    fun permissions() {
        val mine = card(owner = "pankaj@bothrametals.com")
        val theirsShared = card(owner = "staff@bothrametals.com")
        val theirsPrivate = card(owner = "staff@bothrametals.com", vis = "private")
        val me = "Pankaj@BothraMetals.com"
        assertTrue(CloudMapper.canEdit(mine, me, isAdmin = false))
        assertTrue(!CloudMapper.canEdit(theirsShared, me, isAdmin = false))
        assertTrue(CloudMapper.canSee(theirsShared, me, isAdmin = false))
        assertTrue(!CloudMapper.canSee(theirsPrivate, me, isAdmin = false))
        assertTrue("admin sees and edits everything", CloudMapper.canSee(theirsPrivate, me, isAdmin = true) && CloudMapper.canEdit(theirsPrivate, me, isAdmin = true))
    }

    @Test
    fun subGroupsTravelWithEventsAndCards() {
        val ev = EventGroup("ev1", "Aluminium Expo", createdBy = "a@x.com", members = listOf("A@X.com"),
            subGroups = listOf(SubGroup("sg1", "Scrap", 10), SubGroup("sg2", "Finished products", 20)))
        val back = CloudMapper.eventFromMap(roundTrip(CloudMapper.eventToMap(ev)))!!
        assertEquals(ev.subGroups, back.subGroups)
        assertEquals(listOf("a@x.com"), back.members)
        assertEquals("older events without sub-groups still load", emptyList<SubGroup>(),
            CloudMapper.eventFromMap(mapOf("id" to "ev2", "name" to "Old"))!!.subGroups)

        val c = card().copy(eventId = "ev1", subGroupIds = listOf("sg1"), subGroupNames = listOf("Scrap"), subGroupsSuggested = true)
        val r = CloudMapper.fromMap(roundTrip(CloudMapper.toMap(c)))!!.record
        assertEquals(listOf("sg1"), r.subGroupIds)
        assertEquals(listOf("Scrap"), r.subGroupNames)
        assertEquals(true, r.subGroupsSuggested)

        val w = card().copy(qrTexts = listOf("https://apex.in"), webNote = "Makes castings.", webNoteUrl = "https://apex.in/", webCheckedAt = 1234)
        val rw = CloudMapper.fromMap(roundTrip(CloudMapper.toMap(w)))!!.record
        assertEquals(listOf("https://apex.in"), rw.qrTexts)
        assertEquals("Makes castings.", rw.webNote)
        assertEquals("https://apex.in/", rw.webNoteUrl)
        assertEquals(1234L, rw.webCheckedAt)
    }

    @Test
    fun badDocumentIsIgnored() {
        assertEquals(null, CloudMapper.fromMap(mapOf("name" to "no id")))
    }
}

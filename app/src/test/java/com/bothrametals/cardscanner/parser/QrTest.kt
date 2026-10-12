package com.bothrametals.cardscanner.parser

import com.bothrametals.cardscanner.ai.FieldKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrTest {

    private val vcard = """
        BEGIN:VCARD
        VERSION:3.0
        N:Bothra;Pankaj;;;
        FN:Pankaj Bothra
        ORG:Bothra Metals & Alloys Ltd.;Sales
        TITLE:Director
        TEL;TYPE=CELL:+919814012345
        TEL;TYPE=WORK,VOICE:0161-2345678
        TEL;TYPE=FAX:0161-2345699
        EMAIL;TYPE=INTERNET:Pankaj@BothraMetals.com
        URL:https://www.bothrametals.com/
        ADR;TYPE=WORK:;;Plot 45\, Focal Point;Ludhiana;Punjab;141010;India
        END:VCARD
    """.trimIndent()

    @Test
    fun readsVcard() {
        val q = QrParser.parse(listOf(vcard))
        assertTrue(q.hasContact)
        val c = q.contact
        assertEquals("Pankaj Bothra", c.name)
        assertEquals("Bothra Metals & Alloys Ltd.", c.company)
        assertEquals("Director", c.jobTitle)
        assertEquals(listOf("+91 98140 12345"), c.mobiles)
        assertEquals(listOf("+91 161 2345678"), c.phones)
        assertEquals(listOf("+91 161 2345699"), c.faxes)
        assertEquals(listOf("pankaj@bothrametals.com"), c.emails)
        assertEquals(listOf("www.bothrametals.com"), q.websites)
        assertEquals("Plot 45, Focal Point, Ludhiana, Punjab, 141010, India", c.address)
    }

    @Test
    fun readsMecardAndLinks() {
        val m = QrParser.parse(listOf("MECARD:N:Jain,Sunil;TEL:9811022334;EMAIL:sunil@jaintrading.in;URL:jaintrading.in;;"))
        assertEquals("Sunil Jain", m.contact.name)
        assertEquals(listOf("+91 98110 22334"), m.contact.mobiles)
        assertEquals(listOf("jaintrading.in"), m.websites)

        val wa = QrParser.parse(listOf("https://wa.me/919909988776?text=Hi"))
        assertEquals(listOf("+91 99099 88776"), wa.whatsapp)

        val site = QrParser.parse(listOf("https://dcard.in/AbC12?utm_source=card"))
        assertEquals("path case kept, tracking dropped", listOf("dcard.in/AbC12"), site.websites)

        val social = QrParser.parse(listOf("https://www.linkedin.com/company/apex-castings"))
        assertTrue(social.websites.isEmpty())
        assertEquals(listOf("LinkedIn: https://www.linkedin.com/company/apex-castings"), social.other)

        assertEquals(listOf("https://example.com/cards/pankaj.vcf"), QrParser.parse(listOf("https://example.com/cards/pankaj.vcf")).vcfLinks)
        assertEquals(listOf("UPI: apexcastings@okhdfc"), QrParser.parse(listOf("upi://pay?pa=apexcastings%40okhdfc&pn=Apex")).other)
        assertTrue(QrParser.parse(listOf("WIFI:S:Guest;T:WPA;P:secret;;")).isEmpty)
    }

    @Test
    fun qrWinsOverMisreadsAndAddsMissing() {
        val printed = CardFields(
            name = "Pankai Bothra", company = "Bothra Metals & Alloys Limited", jobTitle = "Director",
            mobiles = listOf("+91 98140 12346"), phones = emptyList(),
            emails = listOf("pankaj@bothrametais.com"), websites = listOf("www.bothrametals.com"),
            address = "Plot 45, Focal Point, Phase VIII, Ludhiana, Punjab 141010",
        )
        val r = QrMerge.merge(printed, QrParser.parse(listOf(vcard)))
        val f = r.fields
        assertEquals("QR fixes the name", "Pankaj Bothra", f.name)
        assertEquals("Ltd vs Limited is the same company", "Bothra Metals & Alloys Limited", f.company)
        assertEquals("one digit off → QR number", listOf("+91 98140 12345"), f.mobiles)
        assertEquals("missing office number added", listOf("+91 161 2345678"), f.phones)
        assertEquals(listOf("+91 161 2345699"), f.faxes)
        assertEquals(listOf("pankaj@bothrametals.com"), f.emails)
        assertEquals(listOf("www.bothrametals.com"), f.websites)
        assertTrue(f.extraInfo.contains("Address in QR code"))
        assertEquals(setOf(FieldKeys.NAME, FieldKeys.MOBILES, FieldKeys.EMAILS), r.flagged)
    }

    @Test
    fun nothingInQrChangesNothing() {
        val printed = CardFields(name = "A B", mobiles = listOf("+91 98110 22334"))
        val r = QrMerge.merge(printed, QrParser.parse(emptyList()))
        assertEquals(printed, r.fields)
        assertTrue(r.flagged.isEmpty())
        val wa = QrMerge.merge(printed, QrParser.parse(listOf("https://wa.me/919811022334")))
        assertEquals("same number already there", listOf("+91 98110 22334"), wa.fields.mobiles)
    }
}

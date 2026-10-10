package com.bothrametals.cardscanner.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sample Indian business cards (fictional people) run through the card reader. */
class CardParserTest {

    private fun l(text: String, h: Int = 30, side: Int = 0) = OcrLine(text, h, side)

    private data class Expect(
        val name: String? = null, val title: String? = null, val company: String? = null,
        val mobiles: List<String>? = null, val phones: List<String>? = null, val faxes: List<String>? = null,
        val emails: List<String>? = null, val websites: List<String>? = null, val gstin: String? = null,
        val addressContains: List<String> = emptyList(), val prefix: String? = null,
    )

    private fun check(label: String, lines: List<OcrLine>, e: Expect) {
        val r = CardParser.parse(lines)
        e.name?.let { assertEquals("$label name", it, r.name) }
        e.title?.let { assertEquals("$label title", it, r.jobTitle) }
        e.company?.let { assertEquals("$label company", it, r.company) }
        e.mobiles?.let { assertEquals("$label mobiles", it, r.mobiles) }
        e.phones?.let { assertEquals("$label phones", it, r.phones) }
        e.faxes?.let { assertEquals("$label faxes", it, r.faxes) }
        e.emails?.let { assertEquals("$label emails", it, r.emails) }
        e.websites?.let { assertEquals("$label websites", it, r.websites) }
        e.gstin?.let { assertEquals("$label gstin", it, r.gstin) }
        e.prefix?.let { assertEquals("$label prefix", it, r.prefix) }
        e.addressContains.forEach { assertTrue("$label address has $it: ${r.address}", r.address.contains(it, true)) }
    }

    @Test
    fun card1() {
        check("1 classic metals card", listOf(
        l("BOTHRA METALS & ALLOYS LTD.", 48),
        l("Pankaj Bothra", 36),
        l("Director", 22),
        l("Plot No. 45, Focal Point, Phase VIII", 18),
        l("Ludhiana - 141010 (Punjab) India", 18),
        l("Mob: +91 98140 12345 | Off: 0161-2345678/79", 18),
        l("E-mail: pankaj@bothrametals.com", 18),
        l("www.bothrametals.com", 18),
        l("GSTIN : 03AABCB1234C1Z5", 16),
    ), Expect(name = "Pankaj Bothra", title = "Director", company = "BOTHRA METALS & ALLOYS LTD.",
        mobiles = listOf("+91 98140 12345"), phones = listOf("+91 161 2345678", "+91 161 2345679"),
        emails = listOf("pankaj@bothrametals.com"), websites = listOf("www.bothrametals.com"),
        gstin = "03AABCB1234C1Z5", addressContains = listOf("Focal Point", "141010")))
    }

    @Test
    fun card2() {
        check("2 all caps name, M/T/E labels", listOf(
        l("RAJESH KUMAR AGARWAL", 40),
        l("MANAGING DIRECTOR", 20),
        l("Shree Ganesh Aluminium Extrusions Pvt. Ltd.", 30),
        l("M: 9876543210, 9812345678", 18),
        l("T: (022) 2345 6789  F: (022) 2345 6790", 18),
        l("E: rajesh.agarwal@sgaepl.co.in", 18),
        l("Unit 12, Andheri Industrial Estate,", 16),
        l("Off. Veera Desai Road, Andheri (W), Mumbai 400 053", 16),
    ), Expect(name = "Rajesh Kumar Agarwal", title = "Managing Director", company = "Shree Ganesh Aluminium Extrusions Pvt. Ltd.",
        mobiles = listOf("+91 98765 43210", "+91 98123 45678"), phones = listOf("+91 22 23456789"), faxes = listOf("+91 22 23456790"),
        emails = listOf("rajesh.agarwal@sgaepl.co.in"), addressContains = listOf("Andheri Industrial Estate", "400 053")))
    }

    @Test
    fun card3() {
        check("3 prefix and back side", listOf(
        l("Dr. Anita Sharma", 34),
        l("Head - Quality Assurance", 20),
        l("Hindalco-style Industries Limited", 26),
        l("anita.sharma@example-industries.com", 16),
        l("+91-79-2658 1234 (Direct)", 16),
        l("Cell +91 99099 88776", 16),
        l("Manufacturers of Aluminium Rolled Products", 20, side = 1),
        l("Registered Office: 3rd Floor, Ashram Road,", 16, side = 1),
        l("Ahmedabad, Gujarat - 380009", 16, side = 1),
    ), Expect(name = "Anita Sharma", prefix = "Dr.", title = "Head - Quality Assurance", company = "Hindalco-style Industries Limited",
        mobiles = listOf("+91 99099 88776"), phones = listOf("+91 79 26581234"), addressContains = listOf("Ashram Road", "380009")))
    }

    @Test
    fun card4() {
        check("4 trader with gmail, no company suffix", listOf(
        l("JAIN TRADING COMPANY", 44),
        l("Dealers in Non-Ferrous Metal Scrap", 18),
        l("Sunil Jain", 30),
        l("(Proprietor)", 18),
        l("Shop No. 7, Loha Mandi, Naraina, New Delhi-110028", 16),
        l("Mobile : 98110 22334 / 98110 22335", 16),
        l("Email : jaintrading77@gmail.com", 16),
    ), Expect(name = "Sunil Jain", title = "(Proprietor)", company = "JAIN TRADING COMPANY",
        mobiles = listOf("+91 98110 22334", "+91 98110 22335"), emails = listOf("jaintrading77@gmail.com"),
        addressContains = listOf("Loha Mandi", "110028")))
    }

    @Test
    fun card5() {
        check("5 OCR noise: O for 0, spaces around @", listOf(
        l("Vikram Mehta", 32),
        l("Sr. Manager - Purchase", 20),
        l("Apex Castings Pvt Ltd", 30),
        l("Mob.: 98l4O 55667", 16),
        l("vikram @ apexcastings . com", 16),
        l("GST No. 27AAACA1234B1ZQ", 14),
        l("MIDC Bhosari, Pune - 411026", 14),
    ), Expect(name = "Vikram Mehta", title = "Sr. Manager - Purchase", company = "Apex Castings Pvt Ltd",
        mobiles = listOf("+91 98140 55667"), gstin = "27AAACA1234B1ZQ", addressContains = listOf("Bhosari", "411026")))
    }

    @Test
    fun card6() {
        check("6 foreign card", listOf(
        l("Mohammed Al Farsi", 32),
        l("Sales Manager", 20),
        l("Gulf Aluminium Trading LLC", 30),
        l("Tel: +971 4 123 4567  Mob: +971 50 123 4567", 16),
        l("m.alfarsi@gulfalu.ae", 16),
        l("P.O. Box 12345, Dubai, UAE", 16),
    ), Expect(name = "Mohammed Al Farsi", title = "Sales Manager", company = "Gulf Aluminium Trading LLC",
        mobiles = listOf("+971 50 123 4567"), phones = listOf("+971 4 123 4567"), emails = listOf("m.alfarsi@gulfalu.ae")))
    }

    @Test
    fun card7() {
        check("7 no heights (gallery/plain text), name after company", listOf(
        l("KRISHNA RECYCLERS", 0),
        l("Amit Gupta", 0),
        l("Partner", 0),
        l("+91 7700 112233", 0),
        l("info@krishnarecyclers.in", 0),
        l("Kh. No. 123, Village Sondhi, Bhiwadi, Rajasthan 301019", 0),
    ), Expect(name = "Amit Gupta", title = "Partner", company = "KRISHNA RECYCLERS",
        mobiles = listOf("+91 77001 12233"), emails = listOf("info@krishnarecyclers.in"), addressContains = listOf("301019")))
    }

    @Test
    fun card8() {
        check("8 two numbers merged by OCR", listOf(
        l("S. K. Verma", 30),
        l("General Manager (Works)", 20),
        l("National Steel Tubes Ltd", 30),
        l("Ph 98765 11111 98765 22222", 16),
    ), Expect(name = "S. K. Verma", title = "General Manager (Works)", mobiles = listOf("+91 98765 11111", "+91 98765 22222")))
    }

    @Test
    fun card9() {
        check("9 logo-only company, name from email", listOf(
        l("Neha Kapoor", 26),
        l("Business Development", 18),
        l("neha.kapoor@zenithmetals.in", 14),
        l("+91 99887 76655", 14),
        l("Tel/Fax: 0120-4567890", 14),
        l("C-56, Sector 63, Noida, U.P. 201301", 14),
    ), Expect(name = "Neha Kapoor", title = "Business Development", mobiles = listOf("+91 99887 76655"),
        phones = listOf("+91 120 4567890"), addressContains = listOf("Sector 63")))
    }

    @Test
    fun card10() {
        check("10 bangalore landline +91 80", listOf(
        l("Ramesh Iyer", 30),
        l("Chief Executive Officer", 18),
        l("Iyer Precision Engineering Pvt Ltd", 28),
        l("Ph: +91 80 4123 4567", 14),
        l("ramesh@iyerprecision.com | www.iyerprecision.com", 14),
        l("No. 23, 2nd Cross, Peenya Industrial Area, Bengaluru 560058", 14),
    ), Expect(name = "Ramesh Iyer", title = "Chief Executive Officer", phones = listOf("+91 80 41234567"),
        websites = listOf("www.iyerprecision.com"), emails = listOf("ramesh@iyerprecision.com"), addressContains = listOf("Peenya")))
    }
}

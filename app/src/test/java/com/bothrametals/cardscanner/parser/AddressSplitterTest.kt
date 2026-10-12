package com.bothrametals.cardscanner.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressSplitterTest {

    private fun check(input: String, street: String, city: String, state: String, pin: String, country: String = "India") {
        val p = AddressSplitter.split(input)
        assertEquals("street of: $input", street, p.street)
        assertEquals("city of: $input", city, p.city)
        assertEquals("state of: $input", state, p.state)
        assertEquals("pin of: $input", pin, p.postcode)
        assertEquals("country of: $input", country, p.country)
    }

    @Test fun ludhiana() = check("Plot No. 45, Focal Point, Phase VIII, Ludhiana - 141010 (Punjab) India",
        "Plot No. 45, Focal Point, Phase VIII", "Ludhiana", "Punjab", "141010")

    @Test fun mumbaiSpacedPin() = check("Unit 12, Andheri Industrial Estate, Off. Veera Desai Road, Andheri (W), Mumbai 400 053",
        "Unit 12, Andheri Industrial Estate, Off. Veera Desai Road, Andheri (W)", "Mumbai", "", "400053")

    @Test fun ahmedabadWithState() = check("3rd Floor, Ashram Road, Ahmedabad, Gujarat - 380009",
        "3rd Floor, Ashram Road", "Ahmedabad", "Gujarat", "380009")

    @Test fun delhiDash() = check("Shop No. 7, Loha Mandi, Naraina, New Delhi-110028",
        "Shop No. 7, Loha Mandi, Naraina", "New Delhi", "Delhi", "110028")

    @Test fun noCommas() = check("MIDC Bhosari Pune 411026", "MIDC Bhosari", "Pune", "", "411026")

    @Test fun shortState() = check("C-56, Sector 63, Noida, U.P. 201301", "C-56, Sector 63", "Noida", "Uttar Pradesh", "201301")

    @Test fun newDelhiWithComma() = check("A-12, Okhla Phase II, New Delhi, Delhi 110020",
        "A-12, Okhla Phase II", "New Delhi", "Delhi", "110020")

    @Test fun foreignStaysWhole() {
        val p = AddressSplitter.split("P.O. Box 12345, Dubai, UAE")
        assertEquals("P.O. Box 12345, Dubai, UAE", p.street)
        assertEquals("", p.city)
    }
}

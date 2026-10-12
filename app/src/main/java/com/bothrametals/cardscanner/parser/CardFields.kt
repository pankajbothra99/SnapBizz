package com.bothrametals.cardscanner.parser

/** One line of text read from the card. [height] is the text height in pixels (bigger = more prominent). */
data class OcrLine(
    val text: String,
    val height: Int = 0,
    /** 0 = front of card, 1 = back of card */
    val side: Int = 0,
)

/** Everything we pull out of a business card. All fields are editable by the user before saving. */
data class CardFields(
    val prefix: String = "",
    val name: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val mobiles: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val faxes: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val websites: List<String> = emptyList(),
    val address: String = "",
    val gstin: String = "",
    /** What the company does, e.g. "Manufacturers of aluminium alloy ingots & die castings". */
    val business: String = "",
    /** Search words: products, services, materials, industry (saved in the contact's notes). */
    val keywords: List<String> = emptyList(),
    /** Other text printed on the card (taglines, product lists, second address...). */
    val extraInfo: String = "",
    /** The user's own notes, e.g. "Met at Aluminium India expo, wants 20 MT ingots". */
    val notes: String = "",
) {
    val displayName: String
        get() = name.ifBlank { company.ifBlank { emails.firstOrNull() ?: mobiles.firstOrNull() ?: "Unnamed card" } }

    fun searchText(): String = listOf(
        name, jobTitle, company, address, gstin, extraInfo, notes, business, keywords.joinToString(" "),
        mobiles.joinToString(" "), phones.joinToString(" "), emails.joinToString(" "), websites.joinToString(" "),
    ).joinToString(" ").lowercase()
}

package com.bothrametals.cardscanner.data

import com.bothrametals.cardscanner.parser.CardFields

/** Finds a card already scanned for the same person (same mobile, same email, or same name at the same company). Pure Kotlin. */
object Duplicates {

    class Match(val card: CardRecord, val reason: String, val sameEvent: Boolean)

    fun find(newCard: CardRecord, existing: List<CardRecord>): Match? {
        val f = newCard.fields
        val myMobiles = f.mobiles.map { last10(it) }.filter { it.length == 10 }.toSet()
        val myPhones = f.phones.map { last10(it) }.filter { it.length >= 8 }.toSet()
        val myEmails = f.emails.map { it.trim().lowercase() }.filter { it.contains('@') }.toSet()
        val myName = nameKey(f.name)
        val myCompany = companyKey(f)

        val candidates = existing.filter { it.id != newCard.id }
        val matches = candidates.mapNotNull { other ->
            val o = other.fields
            val reason = when {
                o.mobiles.any { last10(it) in myMobiles } -> "same mobile number"
                o.emails.any { it.trim().lowercase() in myEmails } -> "same email"
                myName.isNotEmpty() && myCompany.isNotEmpty() && nameKey(o.name) == myName && companyKey(o) == myCompany -> "same name and company"
                myPhones.isNotEmpty() && myName.isNotEmpty() && nameKey(o.name) == myName && o.phones.any { last10(it) in myPhones } -> "same name and office number"
                else -> null
            } ?: return@mapNotNull null
            Match(other, reason, sameEvent = newCard.eventId.isNotBlank() && other.eventId == newCard.eventId)
        }
        // Prefer a match from the same event, then the most recent one
        return matches.sortedWith(compareByDescending<Match> { it.sameEvent }.thenByDescending { it.card.createdAt }).firstOrNull()
    }

    private fun last10(phone: String): String = phone.filter { it.isDigit() }.takeLast(10)

    private fun nameKey(name: String): String =
        name.lowercase().split(Regex("""[^a-z]+""")).filter { it.length >= 2 }.joinToString(" ")

    /** First meaningful word of the company, or the email domain, so "Bothra Metals & Alloys Ltd" ≈ "BOTHRA METALS". */
    private fun companyKey(f: CardFields): String {
        val stop = setOf("the", "m/s", "ms", "messrs", "shree", "shri", "sri", "new")
        val word = f.company.lowercase().split(Regex("""[^a-z0-9]+""")).firstOrNull { it.length >= 3 && it !in stop }
        if (word != null) return word
        return f.emails.firstOrNull()?.substringAfter('@')?.substringBefore('.')?.lowercase()
            ?.takeIf { it !in setOf("gmail", "yahoo", "hotmail", "rediffmail", "outlook", "icloud") }.orEmpty()
    }
}

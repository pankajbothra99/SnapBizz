package com.bothrametals.cardscanner.parser

import com.bothrametals.cardscanner.ai.FieldKeys

/**
 * Free double-checks run on every card (AI or phone reading):
 * - fixes common misreads in emails/websites (".corn" → ".com", "gmai1" → "gmail")
 * - compares the name with the email address, and the website with the email domain
 * - validates mobile numbers and the GSTIN check digit
 * Anything that looks doubtful is added to the "please check" set (shown in orange).
 */
object CrossCheck {

    data class Result(val fields: CardFields, val uncertain: Set<String>)

    private val EMAIL_OK = Regex("""^[a-z0-9._%+\-]+@[a-z0-9\-]+(\.[a-z0-9\-]+)*\.[a-z]{2,}$""")
    private val WEBSITE_OK = Regex("""^(www\.)?[a-z0-9\-]+(\.[a-z0-9\-]+)*\.[a-z]{2,}(/\S*)?$""")
    private val MOBILE_OK = Regex("""^\+91 [6-9]\d{4} \d{5}$""")
    private val INTL_OK = Regex("""^\+\d[\d ]{6,18}$""")

    private val DOMAIN_FIXES = listOf(
        Regex("""\.corn$""") to ".com",
        Regex("""\.c0m$""") to ".com",
        Regex("""\.con$""") to ".com",
        Regex("""\.cpm$""") to ".com",
        Regex("""\.co,in$""") to ".co.in",
        Regex("""\.co\.ln$""") to ".co.in",
        Regex("""\.ln$""") to ".in",
        Regex("""\.c0\.in$""") to ".co.in",
        Regex("""\.net\.ln$""") to ".net.in",
        Regex(""",com$""") to ".com",
    )
    private val PROVIDER_FIXES = mapOf(
        "gmai1.com" to "gmail.com", "gmali.com" to "gmail.com", "grnail.com" to "gmail.com", "gmial.com" to "gmail.com",
        "gmall.com" to "gmail.com", "gmaii.com" to "gmail.com", "yah00.com" to "yahoo.com", "yahoo.co.ln" to "yahoo.co.in",
        "rediffmai1.com" to "rediffmail.com", "redifmail.com" to "rediffmail.com", "hotmai1.com" to "hotmail.com",
        "outl00k.com" to "outlook.com",
    )

    fun apply(input: CardFields, alreadyUncertain: Set<String> = emptySet()): Result {
        val flags = alreadyUncertain.toMutableSet()

        // ---- emails
        val emails = input.emails.map { fixEmail(it) }.distinct()
        if (emails.any { !EMAIL_OK.matches(it) }) flags += FieldKeys.EMAILS

        // ---- websites
        var websites = input.websites.map { fixWebsite(it) }.distinct()
        if (websites.any { !WEBSITE_OK.matches(it) }) flags += FieldKeys.WEBSITES

        // Website vs email domain: "bothrametal.com" vs "bothrametals.com" → one of them is misread
        val emailDomains = emails.map { it.substringAfter('@') }.filter { it.isNotEmpty() }
        val agreed = emailDomains.groupingBy { it }.eachCount().filter { it.value >= 2 }.keys
        websites = websites.map { w ->
            val host = w.removePrefix("www.").substringBefore('/')
            val close = emailDomains.firstOrNull { d -> d != host && d.length >= 6 && editDistance(d, host) in 1..2 }
            when {
                close == null -> w
                close in agreed -> { // two or more emails agree, so trust them
                    (if (w.startsWith("www.")) "www." else "") + close + w.removePrefix("www.").removePrefix(host)
                }
                else -> { flags += FieldKeys.WEBSITES; flags += FieldKeys.EMAILS; w }
            }
        }

        // ---- name vs email
        if (input.name.isBlank()) flags += FieldKeys.NAME else {
            val nameTokens = input.name.lowercase().split(Regex("""[^a-z]+""")).filter { it.length >= 4 }
            val localTokens = emails.flatMap { it.substringBefore('@').split(Regex("""[^a-z]+""")) }.filter { it.length >= 4 }
            // "Pankai" vs "pankaj@…": same length, one letter different → one of them is a misread.
            // ("bothra" vs "pbothra" is just an initial, so it is fine.)
            val nearMiss = nameTokens.any { n ->
                localTokens.none { it.contains(n) } &&
                    localTokens.any { l -> l.length == n.length && editDistance(n, l) == 1 }
            }
            if (nearMiss) {
                flags += FieldKeys.NAME
                flags += FieldKeys.EMAILS
            }
        }
        if (input.company.isBlank()) flags += FieldKeys.COMPANY

        // ---- numbers
        if (input.mobiles.any { !(MOBILE_OK.matches(it) || (INTL_OK.matches(it) && !it.startsWith("+91"))) }) flags += FieldKeys.MOBILES
        if (input.mobiles.isEmpty() && input.phones.isEmpty()) flags += FieldKeys.MOBILES

        // ---- GSTIN check digit
        if (input.gstin.isNotBlank() && !CardParser.gstinChecksumOk(input.gstin)) flags += FieldKeys.GSTIN

        return Result(input.copy(emails = emails, websites = websites), flags)
    }

    fun fixEmail(raw: String): String {
        var e = raw.trim().lowercase().replace(" ", "").trim('.', ',', ';', ':')
        if (!e.contains('@')) return e
        var domain = e.substringAfter('@')
        for ((rx, rep) in DOMAIN_FIXES) domain = domain.replace(rx, rep)
        domain = PROVIDER_FIXES[domain] ?: domain
        e = e.substringBefore('@') + "@" + domain
        return e
    }

    fun fixWebsite(raw: String): String {
        var w = raw.trim().lowercase().replace(" ", "").removePrefix("https://").removePrefix("http://").trimEnd('/', '.', ',')
        if (w.startsWith("www,")) w = "www." + w.removePrefix("www,")
        val host = w.substringBefore('/')
        var fixedHost = host
        for ((rx, rep) in DOMAIN_FIXES) fixedHost = fixedHost.replace(rx, rep)
        return fixedHost + w.removePrefix(host)
    }

    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}

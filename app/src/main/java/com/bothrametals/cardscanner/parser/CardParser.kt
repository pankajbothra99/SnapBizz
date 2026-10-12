package com.bothrametals.cardscanner.parser

/**
 * Turns the raw text lines read from a business card into structured contact fields.
 *
 * Tuned for Indian business cards: +91 mobile / STD landline formats, GSTIN, PIN codes,
 * Indian address words (Nagar, Marg, MIDC, Focal Point...) and common company suffixes.
 * Pure Kotlin (no Android code) so it can be unit tested on a PC.
 */
object CardParser {

    enum class PhoneType { MOBILE, WORK, FAX, HOME }

    private class Candidate(val index: Int, val line: OcrLine, val text: String)

    // ---------------------------------------------------------------- patterns

    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}""")

    private const val ALL_TLDS =
        "co\\.in|org\\.in|net\\.in|firm\\.in|gen\\.in|ind\\.in|co\\.uk|com|in|net|org|biz|info|co|io|ae|uk|us|de|cn|asia|store|tech|online|global|world|shop|xyz|site|business|company"
    private val STRONG_TLDS = setOf("com", "in", "co.in", "org.in", "net.in", "firm.in", "net", "org", "biz", "info", "io")

    private val WEBSITE = Regex(
        """(?i)\b((?:https?://)?(?:www\.)?[a-z0-9][a-z0-9\-]*(?:\.[a-z0-9\-]+)*?\.($ALL_TLDS))\b(?:/[^\s,]*)?"""
    )

    /** A run of digits with spaces, dashes, dots or brackets inside it. */
    private val PHONE = Regex("""(?:\+|\b00)?\d[\d \-–().]{5,}\d""")
    private val EXT_SUFFIX = Regex("""^\s*/\s*(\d{1,4})(?!\d)""")

    private val GST_LABEL = Regex("""(?i)\bgst\s*(?:in)?\s*(?:no\.?|number|#)?\s*[:.\-]?\s*""")
    private val GST_LOOSE = Regex("""\b[0-9OoIl]{2}[A-Za-z0-9]{13}\b""")
    private val GST_STRICT = Regex("""^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$""")

    private val PINCODE = Regex("""(?<!\d)[1-9]\d{2}\s?\d{3}(?!\d)""")

    private val CONTACT_LABELS = Regex(
        """(?i)\b(e-?mail|email|mail\s*id|mail|web\s*site|website|web|url|mob(?:ile)?|cell|tel(?:e(?:phone)?)?|ph(?:one)?|fax|off(?:ice)?|res(?:idence)?|whats\s?app|wa|gst(?:in)?|contact|no)\b\.?"""
    )
    private val SINGLE_LETTER_LABEL = Regex("""(?<![A-Za-z])[MTEFWPOH]\s*[:.](?=\s|$|\d|\+|[a-z])""")
    private val EDGE_PUNCT = Regex("""^[\s:;,|/\\\-–•·*]+|[\s:;,|/\\\-–•·*]+$""")

    // ---------------------------------------------------------------- keyword lists

    private val COMPANY_STRONG = listOf(
        "ltd", "limited", "pvt", "private", "llp", "inc", "corp", "corporation", "pte", "gmbh", "plc", "llc",
        "& co", "and co", "& sons", "and sons", "(p)", "opc",
    )
    private val COMPANY_MEDIUM = setOf(
        "industries", "industry", "enterprises", "enterprise", "traders", "trading", "exports", "exporters", "export",
        "imports", "importers", "impex", "overseas", "international", "group", "brothers", "bros", "metals", "metal",
        "alloys", "alloy", "steel", "steels", "ispat", "castings", "casting", "forgings", "forging", "engineering",
        "engineers", "works", "mills", "solutions", "technologies", "technology", "systems", "services", "agencies",
        "agency", "associates", "consultants", "consultancy", "logistics", "manufacturing", "manufacturers", "mfg",
        "products", "polymers", "plastics", "chemicals", "pharma", "pharmaceuticals", "textiles", "fabrics", "motors",
        "automobiles", "foundry", "recycling", "recyclers", "scrap", "aluminium", "aluminum", "copper", "brass", "infra",
        "infrastructure", "builders", "developers", "constructions", "hospital", "bank", "labs", "laboratories",
        "studio", "company", "ventures", "holdings", "capital", "finance", "securities", "insurance", "electricals",
        "electronics", "machines", "machinery", "tools", "equipments", "equipment", "hydraulics", "packaging", "papers",
        "foods", "agro", "extrusions", "extrusion", "wires", "cables", "tubes", "pipes", "industrial corporation",
        "commercial", "corporate", "global", "mart", "stores", "store", "distributors", "suppliers", "refinery",
    )
    private val TAGLINE_HINT = Regex(
        """(?i)\b(of|in|for|dealers?|deals|suppliers?\s+of|stockists?|specialists?|since|iso|certified|approved|authori[sz]ed|leading|quality|we\s)\b"""
    )
    private val TITLE_WORDS = setOf(
        "director", "directors", "managing", "md", "ceo", "cfo", "coo", "cto", "cmo", "founder", "co-founder", "cofounder",
        "proprietor", "propr", "prop", "partner", "owner", "chairman", "chairperson", "president", "vice", "vp", "head",
        "manager", "mgr", "gm", "agm", "dgm", "executive", "exec", "officer", "engineer", "consultant", "advisor",
        "adviser", "sales", "marketing", "purchase", "procurement", "accounts", "accountant", "representative", "rep",
        "assistant", "asst", "senior", "sr", "jr", "junior", "lead", "supervisor", "incharge", "in-charge", "specialist",
        "analyst", "architect", "designer", "developer", "coordinator", "co-ordinator", "admin", "administrator",
        "secretary", "chartered", "advocate", "trustee", "principal", "operations", "production", "quality", "hr",
        "business", "development", "regional", "zonal", "area", "national", "general", "deputy", "joint", "chief",
        "commercial", "technical", "export", "import", "logistics", "dispatch", "store", "plant", "works",
    )
    private val TITLE_STRONG = setOf(
        "director", "directors", "ceo", "cfo", "coo", "cto", "cmo", "founder", "co-founder", "cofounder", "proprietor",
        "propr", "partner", "owner", "chairman", "chairperson", "president", "vp", "manager", "mgr", "gm", "agm", "dgm",
        "executive", "officer", "engineer", "consultant", "advisor", "adviser", "head", "md", "incharge", "in-charge",
        "supervisor", "accountant", "advocate", "coordinator", "co-ordinator", "secretary", "representative", "specialist",
    )
    private val ADDRESS_WORDS = setOf(
        "road", "rd", "street", "st", "marg", "lane", "nagar", "colony", "sector", "sec", "plot", "phase", "industrial",
        "area", "estate", "midc", "gidc", "riico", "sidco", "hsiidc", "upsidc", "focal", "point", "chowk", "bazar",
        "bazaar", "market", "complex", "tower", "towers", "building", "bldg", "floor", "flr", "block", "near", "opp",
        "opposite", "behind", "village", "vill", "post", "po", "p.o", "tehsil", "teh", "district", "dist", "distt",
        "pin", "pincode", "state", "india", "highway", "nh", "bypass", "ward", "gali", "mohalla", "society",
        "apartment", "apartments", "apt", "house", "h.no", "shop", "unit", "park", "zone", "sez", "gate", "cross",
        "main", "layout", "extension", "extn", "enclave", "vihar", "puram", "bagh", "ganj", "kunj", "chawl", "wadi",
        "pada", "peth", "khasra", "survey", "gat", "industrial-area", "ind", "warehouse", "godown", "office", "regd",
        "registered", "works", "factory", "unit-ii", "unit-i", "kolkata", "mumbai",
    )
    private val PLACES = setOf(
        // states / UTs
        "andhra", "pradesh", "arunachal", "assam", "bihar", "chhattisgarh", "goa", "gujarat", "haryana", "himachal",
        "jharkhand", "karnataka", "kerala", "madhya", "maharashtra", "manipur", "meghalaya", "mizoram", "nagaland",
        "odisha", "orissa", "punjab", "rajasthan", "sikkim", "tamil", "nadu", "telangana", "tripura", "uttar",
        "uttarakhand", "bengal", "delhi", "chandigarh", "jammu", "kashmir", "ladakh", "puducherry", "pondicherry",
        // cities
        "new delhi", "mumbai", "bombay", "kolkata", "calcutta", "chennai", "madras", "bengaluru", "bangalore",
        "hyderabad", "secunderabad", "ahmedabad", "pune", "surat", "jaipur", "lucknow", "kanpur", "nagpur", "indore",
        "thane", "bhopal", "visakhapatnam", "vadodara", "baroda", "ludhiana", "agra", "nashik", "faridabad", "meerut",
        "rajkot", "varanasi", "srinagar", "aurangabad", "dhanbad", "amritsar", "navi", "allahabad", "prayagraj",
        "ranchi", "howrah", "coimbatore", "jabalpur", "gwalior", "vijayawada", "jodhpur", "madurai", "raipur", "kota",
        "guwahati", "solapur", "hubli", "bareilly", "moradabad", "mysore", "mysuru", "gurgaon", "gurugram", "aligarh",
        "jalandhar", "bhubaneswar", "salem", "warangal", "noida", "ghaziabad", "dehradun", "jamshedpur", "bhilai",
        "cuttack", "kochi", "cochin", "bhavnagar", "jamnagar", "ujjain", "sonipat", "panipat", "karnal", "ambala",
        "patiala", "bathinda", "mohali", "panchkula", "rohtak", "hisar", "bhiwadi", "neemrana", "alwar", "silvassa",
        "daman", "vapi", "ankleshwar", "bharuch", "morbi", "mehsana", "gandhinagar", "kolhapur", "sangli", "satara",
        "pimpri", "chinchwad", "chakan", "hosur", "sriperumbudur", "tiruppur", "erode", "trichy", "tiruchirappalli",
        "mangalore", "mangaluru", "belgaum", "belagavi", "haridwar", "rudrapur", "baddi", "una", "jammu", "kathua",
        "durgapur", "asansol", "siliguri", "rourkela", "jharsuguda", "angul", "bokaro", "korba", "bilaspur", "visakha",
        "nellore", "guntur", "tirupati", "kakinada", "dubai", "sharjah", "singapore", "ludhiana-", "mandi", "gobindgarh",
        "khanna", "rajpura", "derabassi", "zirakpur", "sahibabad", "greater", "manesar", "bawal", "dharuhera",
    )
    private val NOT_NAME_WORDS = setOf(
        "visiting", "card", "thank", "thanks", "welcome", "since", "iso", "certified", "company", "quality",
        "excellence", "trust", "india", "regd", "office", "works", "factory", "branch", "head", "email", "website",
        "mobile", "phone", "contact", "us", "the", "of", "and", "for", "in", "with", "pvt", "ltd", "limited",
        "gstin", "address", "fax", "tel", "www", "deals", "dealers", "manufacturers", "manufacturer", "exporters",
        "importers", "suppliers", "stockists", "authorised", "authorized", "distributor", "distributors", "brand",
        "products", "services", "solutions", "group", "sales", "marketing", "aluminium", "metals", "steel", "ingots",
        "scrap", "alloys", "copper", "brass",
    )
    private val NAME_PREFIXES = listOf(
        "dr.", "dr", "er.", "er", "ca", "cs", "cma", "mr.", "mr", "mrs.", "mrs", "ms.", "ms", "shri", "sh.", "smt.",
        "smt", "prof.", "prof", "adv.", "capt.", "col.",
    )
    private val GENERIC_MAIL_LOCALS = setOf(
        "info", "sales", "contact", "office", "admin", "accounts", "account", "enquiry", "enquiries", "inquiry",
        "export", "exports", "purchase", "support", "mail", "hello", "marketing", "hr", "care", "service", "orders",
        "works", "factory", "director", "md", "ceo",
    )
    private val FREE_MAIL_DOMAINS = setOf(
        "gmail", "yahoo", "hotmail", "rediffmail", "rediff", "outlook", "live", "icloud", "aol", "ymail", "msn",
        "protonmail", "zoho", "yandex", "mail",
    )

    // ---------------------------------------------------------------- entry point

    fun parse(input: List<OcrLine>): CardFields {
        val lines = input.map { it.copy(text = cleanLine(it.text)) }.filter { it.text.isNotBlank() }
        val maxHeight = (lines.maxOfOrNull { it.height } ?: 0).coerceAtLeast(1)

        val emails = LinkedHashSet<String>()
        val websites = LinkedHashSet<String>()
        val mobiles = LinkedHashSet<String>()
        val phones = LinkedHashSet<String>()
        val faxes = LinkedHashSet<String>()
        var gstin = ""
        val candidates = ArrayList<Candidate>()

        for ((index, line) in lines.withIndex()) {
            var t = fixDigitLookalikes(line.text)
            var hadContact = false

            // GSTIN
            val gst = extractGstin(t)
            if (gst != null) {
                if (gstin.isEmpty()) gstin = gst.first
                t = gst.second
                hadContact = true
            }

            // Emails
            t = t.replace(Regex("""\s*@\s*"""), "@")
            if (t.contains('@')) t = t.replace(Regex("""(?<=[A-Za-z0-9])\s*\.\s*(?=[A-Za-z]{2,})"""), ".")
            t = EMAIL.replace(t) { m ->
                emails += m.value.lowercase().trimEnd('.', '-')
                hadContact = true
                " "
            }

            // Websites
            t = WEBSITE.replace(t) { m ->
                val host = m.groupValues[1].lowercase()
                val tld = m.groupValues[2].lowercase()
                val explicit = host.startsWith("www.") || host.startsWith("http")
                if (explicit || (tld in STRONG_TLDS && host.substringBefore(".").length >= 3 && !host.contains(".."))) {
                    websites += m.value.lowercase().removePrefix("https://").removePrefix("http://").trimEnd('.', '/')
                    hadContact = true
                    " "
                } else m.value
            }

            // Phone / fax numbers
            val phoneResult = extractPhones(t)
            for ((number, type) in phoneResult.first) {
                hadContact = true
                when (type) {
                    PhoneType.MOBILE -> mobiles += number
                    PhoneType.FAX -> faxes += number
                    PhoneType.WORK, PhoneType.HOME -> phones += number
                }
            }
            t = phoneResult.second

            if (hadContact) {
                t = CONTACT_LABELS.replace(t, " ")
                t = SINGLE_LETTER_LABEL.replace(t, " ")
            }
            t = t.replace(Regex("""\s+"""), " ")
            t = t.replace(EDGE_PUNCT, "")
            if (t.count { it.isLetter() } >= 2) candidates += Candidate(index, line, t)
        }

        // A number listed as both mobile and office keeps only the mobile entry
        phones.removeAll(mobiles)

        // ---------------------------------------------------------- classify the remaining lines
        val assigned = HashMap<Int, String>() // candidate position -> role
        val addressParts = ArrayList<MutableList<String>>()
        var lastAddressPos = -10

        for ((pos, c) in candidates.withIndex()) {
            val score = addressScore(c.text)
            val companyStrong = companyStrongScore(c.text)
            val isAddr = score >= 2 ||
                (score >= 1 && companyStrong == 0 && titleScore(c.text) == 0 &&
                    (c.text.any { it.isDigit() } || c.text.contains(',') || pos - lastAddressPos == 1))
            if (isAddr && !(companyStrong > 0 && score < 3 && !PINCODE.containsMatchIn(c.text))) {
                assigned[pos] = "address"
                if (pos - lastAddressPos == 1 && addressParts.isNotEmpty() &&
                    candidates[pos - 1].line.side == c.line.side
                ) {
                    addressParts.last() += c.text
                } else {
                    addressParts += mutableListOf(c.text)
                }
                lastAddressPos = pos
            }
        }

        // Company
        val emailDomains = (emails.map { it.substringAfter('@') } + websites.map { it.removePrefix("www.") })
            .map { it.substringBefore('.') }
            .filter { it.length >= 3 && it !in FREE_MAIL_DOMAINS }
        var companyPos = -1
        var bestCompanyScore = 0.0
        for ((pos, c) in candidates.withIndex()) {
            if (pos in assigned) continue
            val strong = companyStrongScore(c.text)
            val medium = companyMediumScore(c.text)
            var s = strong * 3.0 + medium * 2.0
            val compact = c.text.lowercase().filter { it.isLetterOrDigit() }
            if (emailDomains.any { d -> domainMatches(compact, d) }) s += 4.0
            if (s <= 0) continue
            if (strong == 0 && TAGLINE_HINT.containsMatchIn(c.text)) s -= 3.0
            if (titleStrongScore(c.text) > 0 && strong == 0) s -= 2.0
            s += 2.0 * c.line.height / maxHeight
            if (c.line.side == 0) s += 0.5
            if (s > bestCompanyScore) {
                bestCompanyScore = s
                companyPos = pos
            }
        }
        if (companyPos >= 0 && bestCompanyScore >= 2.0) assigned[companyPos] = "company" else companyPos = -1

        // Job titles
        val titlePositions = ArrayList<Int>()
        for ((pos, c) in candidates.withIndex()) {
            if (pos in assigned) continue
            val words = words(c.text)
            if (words.size > 7) continue
            val strong = titleStrongScore(c.text)
            val any = titleScore(c.text)
            if (strong > 0 || (any >= 2 && words.size <= 4)) {
                titlePositions += pos
            }
        }

        // Name
        val mailLocals = emails.map { it.substringBefore('@').lowercase().filter { ch -> ch.isLetter() } }
            .filter { it.length >= 3 && it !in GENERIC_MAIL_LOCALS }
        var namePos = -1
        var bestNameScore = Double.NEGATIVE_INFINITY
        for ((pos, c) in candidates.withIndex()) {
            if (pos in assigned || pos in titlePositions) continue
            val (_, bare) = splitPrefix(c.text)
            if (!looksLikeName(bare)) continue
            val tokens = words(bare).map { it.lowercase().filter { ch -> ch.isLetter() } }.filter { it.length >= 3 }
            var s = 0.0
            s += 3.0 * tokens.count { tok -> mailLocals.any { it.contains(tok) } }.coerceAtMost(2)
            if (mailLocals.any { local -> tokens.isNotEmpty() && local.length >= 3 && tokens.any { local.startsWith(it.take(3)) } }) s += 1.0
            val nearTitle = titlePositions.minOfOrNull { kotlin.math.abs(it - pos) } ?: 99
            if (nearTitle == 1) s += 2.5 else if (nearTitle == 2) s += 1.0
            s += 2.0 * c.line.height / maxHeight
            val wc = words(bare).size
            if (wc == 1) s -= 1.5
            if (wc in 2..3) s += 1.0
            if (splitPrefix(c.text).first.isNotEmpty()) s += 1.5
            if (c.line.side == 0) s += 0.5
            if (s > bestNameScore) {
                bestNameScore = s
                namePos = pos
            }
        }
        if (namePos >= 0) assigned[namePos] = "name"

        // Prefer titles near the name; keep at most two title lines
        val chosenTitles = titlePositions
            .sortedBy { if (namePos >= 0) kotlin.math.abs(it - namePos) else it }
            .take(2)
            .sorted()
        chosenTitles.forEach { assigned[it] = "title" }

        // Company fallback: a line matching the email/website domain, or the most prominent leftover front line
        if (companyPos < 0) {
            var fallback = -1
            for ((pos, c) in candidates.withIndex()) {
                if (pos in assigned) continue
                val compact = c.text.lowercase().filter { it.isLetterOrDigit() }
                if (emailDomains.any { domainMatches(compact, it) }) { fallback = pos; break }
            }
            if (fallback < 0 && lines.any { it.height > 0 }) {
                fallback = candidates.withIndex()
                    .filter { (pos, c) -> pos !in assigned && c.line.side == 0 && c.line.height >= maxHeight * 0.85 && words(c.text).size <= 6 }
                    .maxByOrNull { it.value.line.height }?.index ?: -1
            }
            if (fallback >= 0) {
                assigned[fallback] = "company"
                companyPos = fallback
            }
        }

        // ---------------------------------------------------------- build the result
        var prefix = ""
        var name = ""
        if (namePos >= 0) {
            val (p, bare) = splitPrefix(candidates[namePos].text)
            prefix = p
            name = smartCase(bare.trim().trimEnd(',', '.'))
        }
        val jobTitle = chosenTitles.joinToString(", ") { smartCase(candidates[it].text) }
        val company = if (companyPos >= 0) candidates[companyPos].text.trim().trimEnd(',') else ""

        val addressBlocks = addressParts.map { part ->
            part.joinToString(", ").replace(Regex("""\s*,\s*,+\s*"""), ", ").replace(Regex("""\s+,"""), ",").trim(',', ' ')
        }
        val address = addressBlocks.firstOrNull() ?: ""

        val extras = ArrayList<String>()
        addressBlocks.drop(1).forEach { extras += "Other address: $it" }
        candidates.forEachIndexed { pos, c -> if (pos !in assigned) extras += c.text }

        // What they do + search keywords (products, services, materials)
        val business = KeywordExtractor.businessLine(extras)
        if (business.isNotEmpty()) extras.remove(business)
        val keywords = KeywordExtractor.keywords(listOf(company, business) + extras)

        return CardFields(
            prefix = prefix,
            name = name,
            jobTitle = jobTitle,
            company = company,
            mobiles = mobiles.toList(),
            phones = phones.toList(),
            faxes = faxes.toList(),
            emails = emails.toList(),
            websites = websites.toList().filterNot { w -> emails.any { e -> e.endsWith("@$w") } || w.isBlank() },
            address = address,
            gstin = gstin,
            business = business,
            keywords = keywords,
            extraInfo = extras.joinToString("\n"),
        )
    }

    // ---------------------------------------------------------------- phones

    /** Returns the phone numbers found in [text] plus the text with those numbers blanked out. */
    fun extractPhones(text: String, defaultLabel: PhoneType? = null): Pair<List<Pair<String, PhoneType>>, String> {
        val found = ArrayList<Pair<String, PhoneType>>()
        val residual = StringBuilder(text)
        var prevEnd = 0
        var lastLabel: PhoneType? = null
        var searchFrom = 0

        while (true) {
            val m = PHONE.find(text, searchFrom) ?: break
            val before = text.substring(prevEnd, m.range.first)
            val label = labelOf(before)
                ?: (if (before.isBlank() || before.trim() == "/" || before.trim() == ",") lastLabel else null)
                ?: defaultLabel
            var end = m.range.last + 1

            val chunks = splitRun(m.value)
            var acceptedAny = false
            var lastDigits: String? = null
            for (chunk in chunks) {
                val n = normalizePhone(chunk, label)
                if (n != null) {
                    found += n
                    acceptedAny = true
                    lastDigits = chunk
                }
            }

            // "0161-2345678/79" -> second number 0161-2345679
            val ext = EXT_SUFFIX.find(text.substring(end))
            if (acceptedAny && ext != null && lastDigits != null) {
                val suffix = ext.groupValues[1]
                if (lastDigits.count { it.isDigit() } > suffix.length) {
                    normalizePhone(replaceLastDigits(lastDigits, suffix), label)?.let { found += it }
                }
                end += ext.range.last + 1
            }

            if (acceptedAny) {
                for (i in m.range.first until end) residual.setCharAt(i, ' ')
                lastLabel = label ?: found.last().second
                prevEnd = end
            }
            searchFrom = end.coerceAtLeast(m.range.first + 1)
            if (searchFrom >= text.length) break
        }
        return found to residual.toString()
    }

    /** Splits "98140 12345 98150 67890" (two numbers OCR'd as one run) into separate numbers. */
    private fun splitRun(raw: String): List<String> {
        val digitCount = raw.count { it.isDigit() }
        val intl = raw.trimStart().startsWith("+") || raw.trimStart().startsWith("00")
        if (digitCount <= 13 || (intl && digitCount <= 15 && !raw.trimStart().removePrefix("+").startsWith("91"))) return listOf(raw)
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (tok in raw.split(Regex("""\s+"""))) {
            if (tok.isEmpty()) continue
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(tok)
            var d = cur.filter { it.isDigit() }
            if (cur.startsWith("+91") || (d.length >= 12 && d.startsWith("91"))) d = d.removePrefix("91")
            if (d.startsWith("0")) d = d.substring(1)
            if (d.length >= 10) {
                out += cur.toString()
                cur = StringBuilder()
            }
        }
        if (cur.count { it.isDigit() } >= 6) out += cur.toString()
        return out
    }

    /** replaceLastDigits("0161-2345678", "79") == "0161-2345679" (keeps the separators). */
    private fun replaceLastDigits(number: String, suffix: String): String {
        val chars = number.toCharArray()
        var k = suffix.length - 1
        var i = chars.size - 1
        while (i >= 0 && k >= 0) {
            if (chars[i].isDigit()) { chars[i] = suffix[k]; k-- }
            i--
        }
        return String(chars)
    }

    private fun normalizePhone(raw: String, label: PhoneType?): Pair<String, PhoneType>? {
        val trimmed = raw.trim()
        val plus = trimmed.startsWith("+")
        var d = trimmed.filter { it.isDigit() }
        val groups = trimmed.split(Regex("""[^\d]+""")).filter { it.isNotEmpty() }
        var national: String
        var leadingZero = false

        if (plus || d.startsWith("00")) {
            if (d.startsWith("00")) d = d.substring(2)
            if (d.startsWith("91") && d.length in 10..13) {
                national = d.substring(2)
                if (national.startsWith("0")) national = national.substring(1)
            } else {
                if (d.length !in 8..15) return null
                return formatForeign(groups, plus) to (label ?: PhoneType.WORK)
            }
        } else if (d.length == 12 && d.startsWith("91") && d[2] in '6'..'9') {
            national = d.substring(2)
        } else if (d.startsWith("0")) {
            national = d.substring(1)
            leadingZero = true
        } else {
            national = d
        }

        val startsMobile = national.isNotEmpty() && national[0] in '6'..'9'
        // "+91 80 4123 4567" / "+91-79-2658 1234": a 2-digit group after +91 is an STD code, not a mobile
        val afterCc = if (groups.firstOrNull() == "91" || groups.firstOrNull() == "0091") groups.drop(1) else groups
        val twoDigitStd = !leadingZero && afterCc.size >= 2 && afterCc[0].length == 2 && national.startsWith(afterCc[0])
        return when {
            national.length == 10 && startsMobile && !leadingZero && !twoDigitStd ->
                if (label == PhoneType.FAX) formatLandline(national, groups) to PhoneType.FAX
                else formatMobile(national) to PhoneType.MOBILE
            national.length == 10 && startsMobile && leadingZero -> {
                // "098140 12345" is a mobile; "079-2658 1234" is an Ahmedabad landline
                val firstGroup = groups.firstOrNull() ?: ""
                val looksLikeStd = groups.size >= 2 && firstGroup.length in 3..5
                if (label == PhoneType.MOBILE || !looksLikeStd) formatMobile(national) to (label?.takeIf { it != PhoneType.WORK } ?: PhoneType.MOBILE)
                else formatLandline(national, groups) to (label?.takeIf { it != PhoneType.MOBILE } ?: PhoneType.WORK)
            }
            national.length in 9..10 ->
                formatLandline(national, groups) to (label?.takeIf { it != PhoneType.MOBILE } ?: PhoneType.WORK)
            national.length in 6..8 && label != null ->
                d to (if (label == PhoneType.MOBILE) PhoneType.WORK else label)
            else -> null
        }
    }

    private fun formatMobile(n: String) = "+91 ${n.substring(0, 5)} ${n.substring(5)}"

    private fun formatLandline(national: String, groups: List<String>): String {
        // Find the STD code group (e.g. "0161" / "161" / "22") right before the subscriber number
        val g = groups.toMutableList()
        if (g.isNotEmpty() && (g[0] == "91" || g[0] == "0091")) g.removeAt(0)
        val std = g.firstOrNull()?.trimStart('0') ?: ""
        if (g.size >= 2 && std.length in 2..4 && national.startsWith(std)) {
            return "+91 $std ${national.substring(std.length)}"
        }
        return "+91 $national"
    }

    private fun formatForeign(groups: List<String>, plus: Boolean): String {
        val joined = groups.joinToString(" ")
        return if (joined.startsWith("00")) "+" + joined.substring(2).trim() else if (plus) "+$joined" else joined
    }

    private val FAX_RX = Regex("""fax|(?<![a-z])f\s*[:.\-]""")
    private val MOBILE_RX = Regex("""mob|mobile|cell|whats\s?app|(?<![a-z])wa(?![a-z])|(?<![a-z])m\s*[:.\-]|(?<![a-z])m(?=\s*\+?\d|\s*$)|(?<![a-z])hp(?![a-z])""")
    private val WORK_RX = Regex("""tel|phone|(?<![a-z])ph(?![a-z])|off|landline|board|direct|(?<![a-z])[tpo]\s*[:.\-]|(?<![a-z])[tp](?=\s*\+?\d|\s*$)""")
    private val HOME_RX = Regex("""(?<![a-z])res(?:i|idence)?(?![a-z])""")

    private fun labelOf(before: String): PhoneType? {
        val t = before.lowercase().takeLast(24)
        if (Regex("""(tel|ph|phone)\s*/\s*fax""").containsMatchIn(t)) return PhoneType.WORK
        var best: PhoneType? = null
        var bestPos = -1
        for ((rx, type) in listOf(FAX_RX to PhoneType.FAX, MOBILE_RX to PhoneType.MOBILE, WORK_RX to PhoneType.WORK, HOME_RX to PhoneType.HOME)) {
            val last = rx.findAll(t).lastOrNull() ?: continue
            if (last.range.last > bestPos) {
                bestPos = last.range.last
                best = type
            }
        }
        return best
    }

    /**
     * Formats one phone string (e.g. from the AI) the same way as numbers read on the phone.
     * [hint] is what the source thinks it is; the number's own structure wins (a 98xxx number is a mobile).
     */
    fun formatPhones(raw: String, hint: PhoneType?): List<Pair<String, PhoneType>> =
        extractPhones(fixDigitLookalikes(cleanLine(raw)), hint).first

    /** Uppercases, fixes common OCR swaps (O/0, I/1, 2/Z) and returns the GSTIN if it has a valid shape, else null. */
    fun cleanGstin(raw: String): String? {
        val compact = raw.uppercase().filter { it.isLetterOrDigit() }
        if (compact.length != 15) return null
        val fixed = fixGstin(compact)
        return if (GST_STRICT.matches(fixed)) fixed else null
    }

    /** GSTIN check digit (15th character) validation. */
    fun gstinChecksumOk(gstin: String): Boolean {
        if (gstin.length != 15) return false
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        var sum = 0
        for (i in 0 until 14) {
            val v = chars.indexOf(gstin[i])
            if (v < 0) return false
            val product = v * (if (i % 2 == 0) 1 else 2)
            sum += product / 36 + product % 36
        }
        val check = chars[(36 - sum % 36) % 36]
        return gstin[14] == check
    }

    // ---------------------------------------------------------------- GSTIN

    private fun extractGstin(text: String): Pair<String, String>? {
        val label = GST_LABEL.find(text)
        if (label != null) {
            val after = text.substring(label.range.last + 1)
            val compact = after.uppercase().filter { it.isLetterOrDigit() }
            if (compact.length >= 15) {
                val fixed = fixGstin(compact.take(15))
                if (GST_STRICT.matches(fixed)) {
                    // blank out the label and the 15 characters after it
                    var consumed = 0
                    var endIdx = label.range.last + 1
                    while (endIdx < text.length && consumed < 15) {
                        if (text[endIdx].isLetterOrDigit()) consumed++
                        endIdx++
                    }
                    return fixed to (text.substring(0, label.range.first) + " " + text.substring(endIdx))
                }
            }
        }
        for (m in GST_LOOSE.findAll(text)) {
            val fixed = fixGstin(m.value.uppercase())
            if (GST_STRICT.matches(fixed)) {
                return fixed to text.replaceRange(m.range, " ")
            }
        }
        return null
    }

    private fun fixGstin(s: String): String {
        val c = s.toCharArray()
        val digitPos = intArrayOf(0, 1, 7, 8, 9, 10)
        val letterPos = intArrayOf(2, 3, 4, 5, 6, 11)
        for (i in digitPos) c[i] = when (c[i]) { 'O', 'D', 'Q' -> '0'; 'I', 'L' -> '1'; 'S' -> '5'; 'B' -> '8'; 'Z' -> '2'; 'G' -> '6'; else -> c[i] }
        for (i in letterPos) c[i] = when (c[i]) { '0' -> 'O'; '1' -> 'I'; '5' -> 'S'; '8' -> 'B'; '2' -> 'Z'; '6' -> 'G'; else -> c[i] }
        if (c[13] == '2') c[13] = 'Z'
        return String(c)
    }

    // ---------------------------------------------------------------- scoring helpers

    private fun words(text: String): List<String> = text.split(Regex("""[\s,]+""")).filter { it.isNotBlank() }

    private fun lowerWords(text: String): List<String> =
        text.lowercase().split(Regex("""[^a-z0-9.&\-]+""")).map { it.trim('.', '-') }.filter { it.isNotEmpty() }

    private fun companyStrongScore(text: String): Int {
        val lw = " " + text.lowercase().replace(Regex("""[.,]"""), " ").replace(Regex("""\s+"""), " ") + " "
        return COMPANY_STRONG.count { k -> lw.contains(" $k ") || (k.startsWith("&") && lw.contains(k.replace(" ", ""))) }
    }

    private fun companyMediumScore(text: String): Int {
        val lw = lowerWords(text)
        return lw.count { it in COMPANY_MEDIUM }.coerceAtMost(3)
    }

    private fun titleScore(text: String): Int = lowerWords(text).count { it in TITLE_WORDS }
    private fun titleStrongScore(text: String): Int = lowerWords(text).count { it in TITLE_STRONG }

    private fun addressScore(text: String): Int {
        var s = 0
        if (PINCODE.containsMatchIn(text)) s += 2
        val lw = lowerWords(text)
        s += lw.count { it in ADDRESS_WORDS }.coerceAtMost(3)
        val low = text.lowercase()
        if (PLACES.any { p -> Regex("""(?<![a-z])${Regex.escape(p)}(?![a-z])""").containsMatchIn(low) }) s += 1
        if (Regex("""\d""").containsMatchIn(text) && text.contains(',')) s += 1
        if (Regex("""(?i)\b(no|plot|h|shop|flat|unit|door|gat|survey|khasra)\s*\.?\s*(no\.?)?\s*[:\-]?\s*\d""").containsMatchIn(text)) s += 1
        return s
    }

    private fun domainMatches(compactLine: String, domain: String): Boolean {
        val d = domain.lowercase().filter { it.isLetterOrDigit() }
        if (d.length < 3 || compactLine.length < 3) return false
        return compactLine.startsWith(d) || d.startsWith(compactLine.take(minOf(compactLine.length, 8))) ||
            (d.length >= 6 && compactLine.contains(d))
    }

    private fun splitPrefix(text: String): Pair<String, String> {
        val t = text.trim()
        val lower = t.lowercase()
        for (p in NAME_PREFIXES) {
            if (lower.startsWith("$p ") || (p.endsWith(".") && lower.startsWith(p) && t.length > p.length)) {
                val rest = t.substring(p.length).trim()
                if (rest.isNotEmpty()) {
                    val shown = when (p.trimEnd('.')) {
                        "ca", "cs", "cma" -> p.uppercase()
                        else -> p.replaceFirstChar { it.uppercase() }.let { if (!it.endsWith(".") && it.length <= 3 && it !in listOf("Shri")) "$it." else it }
                    }
                    return shown to rest
                }
            }
        }
        return "" to t
    }

    private fun looksLikeName(text: String): Boolean {
        val t = text.trim()
        if (t.length !in 3..40) return false
        if (t.any { it.isDigit() }) return false
        if (!Regex("""^[A-Za-z][A-Za-z .'\-]*$""").matches(t)) return false
        val w = words(t)
        if (w.size !in 1..5) return false
        if (w.any { it.length > 18 }) return false
        val lw = w.map { it.lowercase().trim('.') }
        if (lw.any { it in NOT_NAME_WORDS }) return false
        if (lw.count { it in TITLE_WORDS } > 0) return false
        if (lw.any { it in COMPANY_MEDIUM }) return false
        // Each word should be mostly letters, initials like "K." are fine
        return w.all { word -> word.trim('.').length >= 1 }
    }

    // ---------------------------------------------------------------- text helpers

    private fun cleanLine(s: String): String = s
        .replace('|', ' ')
        .replace('–', '-')
        .replace('—', '-')
        .replace('’', '\'')
        .replace(Regex("""[​-‍﻿]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** OCR often reads 0 as O and 1 as l/I inside phone numbers. */
    private fun fixDigitLookalikes(s: String): String = s
        .replace(Regex("""(?<=\d)[Oo](?=[\d\s\-])|(?<=\d[\s\-])[Oo](?=\d)|(?<=\d)[Oo]$"""), "0")
        .replace(Regex("""(?<=\d)[lI|](?=\d)"""), "1")

    /** "PANKAJ BOTHRA" -> "Pankaj Bothra"; leaves mixed-case text alone. */
    fun smartCase(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty()) return s
        val upperRatio = letters.count { it.isUpperCase() }.toDouble() / letters.length
        if (upperRatio < 0.8 && letters.any { it.isLowerCase() } && letters.first().isUpperCase()) return s
        if (upperRatio < 0.8 && letters.any { it.isUpperCase() }) return s
        val keepUpper = setOf("md", "ceo", "cfo", "coo", "cto", "gm", "agm", "dgm", "vp", "hr", "ca", "cs", "it", "qa", "qc", "r&d", "bd", "avp", "svp", "evp", "ii", "iii", "iv")
        return s.split(" ").joinToString(" ") { word ->
            val core = word.lowercase().trim(',', '.', '(', ')')
            when {
                core in keepUpper -> word.uppercase()
                word.length <= 2 && word.endsWith(".") -> word.uppercase()
                else -> word.lowercase().split("-").joinToString("-") { part -> part.replaceFirstChar { it.uppercase() } }
            }
        }
    }
}

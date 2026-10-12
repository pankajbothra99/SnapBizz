package com.bothrametals.cardscanner.parser

/**
 * Works out "what they do" and search keywords from the card text when the AI isn't available.
 * (With AI reading, the AI does this better and this is only a fallback.) Pure Kotlin.
 */
object KeywordExtractor {

    /** Lines like "Manufacturers of Aluminium Alloy Ingots" or "Dealers in Non-Ferrous Scrap". */
    private val BUSINESS_LINE = Regex(
        """(?i)\b(manufacturers?|mfrs?\.?|makers?|producers?|dealers?|deals\s+in|suppliers?|stockists?|exporters?|importers?|traders?|trading\s+in|distributors?|specialists?\s+in|service\s+providers?|services|solutions|consultants?|contractors?|fabricators?|processors?|recyclers?|refiners?|wholesalers?|retailers?|agents?\s+for)\b"""
    )

    /** Word or phrase on the card → keyword to save (spelling variants included). */
    private val VOCAB: List<Pair<Regex, List<String>>> = listOf(
        "alumin(i)?um" to listOf("aluminium", "aluminum"),
        "copper" to listOf("copper"),
        "brass" to listOf("brass"),
        "bronze" to listOf("bronze"),
        "zinc" to listOf("zinc"),
        "lead" to listOf("lead"),
        "nickel" to listOf("nickel"),
        "tin" to listOf("tin"),
        "stainless" to listOf("stainless steel"),
        "steels?" to listOf("steel"),
        "iron" to listOf("iron"),
        "ferro\\s*alloys?" to listOf("ferro alloys"),
        "non[\\s-]*ferrous" to listOf("non-ferrous"),
        "metals?" to listOf("metals"),
        "alloys?" to listOf("alloys"),
        "scrap" to listOf("scrap"),
        "ingots?" to listOf("ingots"),
        "billets?" to listOf("billets"),
        "wire\\s*rods?" to listOf("wire rod"),
        "die[\\s-]*cast(ing|ings)?" to listOf("die casting"),
        "castings?" to listOf("castings"),
        "forgings?" to listOf("forgings"),
        "extrusions?" to listOf("extrusions"),
        "sheets?" to listOf("sheets"),
        "coils?" to listOf("coils"),
        "foils?" to listOf("foil"),
        "wires?" to listOf("wires"),
        "cables?" to listOf("cables"),
        "pipes?" to listOf("pipes"),
        "tubes?" to listOf("tubes"),
        "powders?" to listOf("powder"),
        "dross" to listOf("dross"),
        "recycl(ing|ers?|ed)" to listOf("recycling"),
        "e[\\s-]*waste" to listOf("e-waste"),
        "smelt(ing|ers?)" to listOf("smelting"),
        "refiner(y|ies|s)?" to listOf("refinery"),
        "foundr(y|ies)" to listOf("foundry"),
        "furnaces?" to listOf("furnace"),
        "refractor(y|ies)" to listOf("refractory"),
        "machining|cnc" to listOf("machining"),
        "fabricat(ion|ors?)" to listOf("fabrication"),
        "moulds?|molds?" to listOf("moulds"),
        "dies" to listOf("dies"),
        "tools?|tooling" to listOf("tools"),
        "machiner(y|ies)|machines?" to listOf("machinery"),
        "equipments?" to listOf("equipment"),
        "spares?|spare\\s*parts" to listOf("spare parts"),
        "auto(mobile|motive)?\\s*(parts|components)" to listOf("auto parts"),
        "components?" to listOf("components"),
        "fasteners?|bolts?|nuts" to listOf("fasteners"),
        "bearings?" to listOf("bearings"),
        "valves?" to listOf("valves"),
        "pumps?" to listOf("pumps"),
        "motors?" to listOf("motors"),
        "transformers?" to listOf("transformers"),
        "batter(y|ies)" to listOf("batteries"),
        "solar" to listOf("solar"),
        "electricals?" to listOf("electrical"),
        "electronics?" to listOf("electronics"),
        "chemicals?" to listOf("chemicals"),
        "fluxes|flux" to listOf("flux"),
        "plastics?|polymers?" to listOf("plastics"),
        "packaging" to listOf("packaging"),
        "logistics|transport(ers?|ation)?|freight|shipping|cargo" to listOf("logistics"),
        "custom\\s*house|clearing|cha" to listOf("customs clearing"),
        "exports?|exporters?" to listOf("export"),
        "imports?|importers?" to listOf("import"),
        "trad(ers?|ing)" to listOf("trading"),
        "manufactur(ers?|ing)|mfrs?" to listOf("manufacturer"),
        "suppliers?" to listOf("supplier"),
        "distributors?" to listOf("distributor"),
        "dealers?|deals\\s+in" to listOf("dealer"),
        "consult(ants?|ancy|ing)" to listOf("consultant"),
        "engineer(ing|s)" to listOf("engineering"),
        "construction|builders?|infra(structure)?" to listOf("construction"),
        "testing|laborator(y|ies)|labs?|inspection" to listOf("testing lab"),
        "software|it\\s+services" to listOf("software"),
        "textiles?|garments?|fabrics?" to listOf("textiles"),
        "pharma(ceuticals?)?" to listOf("pharma"),
        "foods?|agro" to listOf("food"),
        "minerals?|mining|ores?" to listOf("minerals"),
        "bauxite" to listOf("bauxite"),
        "alumina" to listOf("alumina"),
        "silicon" to listOf("silicon"),
        "magnesium" to listOf("magnesium"),
        "titanium" to listOf("titanium"),
        "adc\\s*-?\\s*12" to listOf("ADC12"),
        "lm\\s*-?\\s*6" to listOf("LM6"),
        "lm\\s*-?\\s*24" to listOf("LM24"),
        "finance|bank(ing)?|insurance|loans?" to listOf("finance"),
        "chartered\\s+accountants?" to listOf("chartered accountant"),
    ).map { (pattern, kws) -> Regex("""(?i)(?<![a-z])(?:$pattern)(?![a-z])""") to kws }

    /** The line on the card that says what the company does, if there is one. */
    fun businessLine(lines: List<String>): String =
        lines.map { it.trim() }
            .firstOrNull { it.length in 8..160 && BUSINESS_LINE.containsMatchIn(it) && it.count { c -> c.isDigit() } < 6 }
            .orEmpty()

    /** Search keywords found in the given texts (company name, business line, other text on the card). */
    fun keywords(texts: List<String>): List<String> {
        val joined = texts.joinToString(" \n ")
        val out = LinkedHashSet<String>()
        for ((rx, kws) in VOCAB) if (rx.containsMatchIn(joined)) out += kws
        return out.toList()
    }

    /** Cleans keywords from the AI or the user: trimmed, no duplicates (ignoring case), at most [max]. */
    fun clean(list: List<String>, max: Int = 20): List<String> {
        val seen = HashSet<String>()
        return list.flatMap { it.split(',', ';', '|', '\n') }
            .map { it.trim().trim('.', '-', '•', '*').trim() }
            .filter { it.length in 2..40 }
            .filter { seen.add(it.lowercase()) }
            .take(max)
    }
}

package com.bothrametals.cardscanner.data

import com.bothrametals.cardscanner.parser.CardFields

/**
 * Sub-groups inside an event ("Scrap", "Finished products", …). A card can be in several.
 * Pure Kotlin so it can be unit tested on a PC.
 */
object SubGroups {

    /** Shown on Home as a filter: cards in the event that are in no sub-group yet. */
    const val NOT_SORTED = "_none"

    /** Tidies a typed name: trims and joins spaces, max 40 characters. */
    fun cleanName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(40)

    /** The existing sub-group with this name (ignoring case and extra spaces), if any. */
    fun findByName(list: List<SubGroup>, name: String): SubGroup? {
        val n = cleanName(name).lowercase()
        return list.firstOrNull { cleanName(it.name).lowercase() == n }
    }

    /**
     * The names to show for a card's sub-groups.
     * If this phone knows the event, its current names are used (so renames and deletions show at once);
     * otherwise the names saved on the card.
     */
    fun namesFor(card: CardRecord, event: EventGroup?): List<String> {
        if (event == null || event.id != card.eventId) return card.subGroupNames
        val byId = event.subGroups.associateBy { it.id }
        return card.subGroupIds.mapNotNull { byId[it]?.name }
    }

    /** Sub-group ids of a card that still exist in the event. */
    fun liveIds(card: CardRecord, event: EventGroup?): List<String> {
        if (event == null || event.id != card.eventId) return card.subGroupIds
        val ids = event.subGroups.map { it.id }.toSet()
        return card.subGroupIds.filter { it in ids }
    }

    /** Is [card] shown when sub-group [selectedId] is picked ("" = all, [NOT_SORTED] = in none)? */
    fun matches(card: CardRecord, selectedId: String, event: EventGroup?): Boolean = when (selectedId) {
        "" -> true
        NOT_SORTED -> liveIds(card, event).isEmpty()
        else -> selectedId in card.subGroupIds
    }

    /** The card with sub-group [id] switched on or off; the saved names are refreshed too. */
    fun toggle(card: CardRecord, id: String, event: EventGroup): CardRecord {
        val ids = liveIds(card, event)
        val newIds = if (id in ids) ids - id else ids + id
        return withIds(card, newIds, event)
    }

    /** The card with exactly these sub-groups (kept in the event's order), names refreshed. */
    fun withIds(card: CardRecord, ids: List<String>, event: EventGroup): CardRecord {
        val ordered = event.subGroups.filter { it.id in ids }
        return card.copy(subGroupIds = ordered.map { it.id }, subGroupNames = ordered.map { it.name })
    }

    /** Ids of the sub-groups the AI named (matched ignoring case and spaces; names not in the list are dropped). */
    fun idsFromNames(names: List<String>, list: List<SubGroup>): List<String> =
        names.mapNotNull { findByName(list, it)?.id }.distinct()

    /**
     * Guess without AI: a sub-group fits when one of its meaningful words (e.g. "scrap" in "Scrap buyers")
     * appears in the company name, what they do, the keywords or other text on the card.
     */
    fun suggestOffline(f: CardFields, list: List<SubGroup>): List<String> {
        val text = " " + (listOf(f.company, f.business, f.extraInfo) + f.keywords)
            .joinToString(" ").lowercase().replace(Regex("[^a-z0-9]+"), " ") + " "
        return list.filter { g ->
            words(g.name).any { w -> setOf(w, w.removeSuffix("s"), w + "s").any { it.length >= 3 && text.contains(" $it ") } }
        }.map { it.id }
    }

    /** Words too general to decide a sub-group on their own. */
    private val GENERIC = setOf(
        "and", "the", "of", "for", "in", "to", "with", "or", "other", "others", "all", "misc", "general", "new", "old",
        "product", "products", "goods", "item", "items", "material", "materials", "type", "types", "group", "groups",
        "buyer", "buyers", "seller", "sellers", "supplier", "suppliers", "dealer", "dealers", "trader", "traders",
        "customer", "customers", "people", "party", "parties", "company", "companies", "business", "visitor", "visitors",
    )

    private fun words(name: String) = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in GENERIC }

    /** Oldest first, so new sub-groups appear at the end of the row. */
    fun sorted(list: List<SubGroup>): List<SubGroup> = list.sortedWith(compareBy({ it.at }, { it.name.lowercase() }))
}

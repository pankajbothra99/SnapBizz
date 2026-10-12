package com.bothrametals.cardscanner.data

/**
 * An event (exhibition, conference, trip…) that groups the cards scanned there.
 * Shown only to its [members] (and admins). Pure Kotlin.
 */
data class EventGroup(
    val id: String,
    val name: String,
    val place: String = "",
    val createdBy: String = "",
    val members: List<String> = emptyList(),
    val closed: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** Sub-groups inside this event, e.g. "Scrap", "Finished products" (oldest first). */
    val subGroups: List<SubGroup> = emptyList(),
) {
    fun canManage(myEmail: String, isAdmin: Boolean) =
        isAdmin || createdBy.isBlank() || createdBy.equals(myEmail, ignoreCase = true)

    val label: String get() = if (place.isBlank()) name else "$name · $place"
}

/** A sub-group inside an event. [at] = when it was added (keeps the order). */
data class SubGroup(val id: String, val name: String, val at: Long = 0L)

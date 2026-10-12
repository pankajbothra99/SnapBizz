package com.bothrametals.cardscanner.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DateGroupsTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private fun t(s: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).apply { timeZone = ist }.parse(s)!!.time

    @Test
    fun labels() {
        val now = t("2026-10-12 10:30")   // a Monday
        assertEquals("Today", DateGroups.label(t("2026-10-12 00:05"), now, ist))
        assertEquals("Yesterday", DateGroups.label(t("2026-10-11 23:50"), now, ist))
        assertEquals("Yesterday", DateGroups.label(t("2026-10-11 00:00"), now, ist))
        assertEquals("Last 7 days", DateGroups.label(t("2026-10-06 09:00"), now, ist))
        assertEquals("Earlier this month", DateGroups.label(t("2026-10-02 09:00"), now, ist))
        assertEquals("September 2026", DateGroups.label(t("2026-09-30 18:00"), now, ist))
        assertEquals("October 2025", DateGroups.label(t("2025-10-12 10:00"), now, ist))
    }

    @Test
    fun groupsNewestFirst() {
        val now = t("2026-10-12 10:30")
        val items = listOf(t("2026-09-01 10:00"), t("2026-10-12 09:00"), t("2026-10-12 08:00"), t("2026-10-11 12:00"))
        val g = DateGroups.group(items, { it }, now, ist)
        assertEquals(listOf("Today", "Yesterday", "September 2026"), g.map { it.first })
        assertEquals(listOf(t("2026-10-12 09:00"), t("2026-10-12 08:00")), g[0].second)
    }
}

package com.bothrametals.cardscanner.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Groups cards on the home screen under friendly headings:
 * Today, Yesterday, Last 7 days, Earlier this month, then one heading per month ("August 2026").
 * Pure Kotlin so it can be unit tested on a PC.
 */
object DateGroups {

    private const val DAY = 24L * 60 * 60 * 1000

    fun label(time: Long, now: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val today = startOfDay(now, tz)
        val nowCal = Calendar.getInstance(tz).apply { timeInMillis = now }
        val cal = Calendar.getInstance(tz).apply { timeInMillis = time }
        val sameMonth = cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) && cal.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH)
        return when {
            time >= today -> "Today"
            time >= today - DAY -> "Yesterday"
            time >= today - 6 * DAY -> "Last 7 days"
            sameMonth -> "Earlier this month"
            else -> SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).apply { timeZone = tz }.format(cal.time)
        }
    }

    /** Newest first, split into (heading, items) groups in display order. */
    fun <T> group(items: List<T>, time: (T) -> Long, now: Long, tz: TimeZone = TimeZone.getDefault()): List<Pair<String, List<T>>> {
        val out = LinkedHashMap<String, MutableList<T>>()
        items.sortedByDescending(time).forEach { out.getOrPut(label(time(it), now, tz)) { ArrayList() } += it }
        return out.map { it.key to it.value }
    }

    private fun startOfDay(t: Long, tz: TimeZone): Long = Calendar.getInstance(tz).apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

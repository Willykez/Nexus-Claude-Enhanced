package com.nexusforge.app.ui.components

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** "just now", "12 min ago", "3 h ago", "Yesterday", "4 d ago", otherwise the date. */
fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - millis
    return when {
        diff < MINUTE -> "just now"
        diff < HOUR -> "${diff / MINUTE} min ago"
        millis >= startOfDay(now) -> "${diff / HOUR} h ago"
        millis >= startOfDay(now) - DAY -> "Yesterday"
        diff < 7 * DAY -> "${(startOfDay(now) - startOfDay(millis)) / DAY} d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }
}

/** Sidebar / history section heading for an item last touched at [millis]. */
fun dayBucket(millis: Long, now: Long = System.currentTimeMillis()): String = when {
    millis >= startOfDay(now) -> "Today"
    millis >= startOfDay(now) - DAY -> "Yesterday"
    millis >= startOfDay(now) - 6 * DAY -> "Previous 7 days"
    millis >= startOfDay(now) - 29 * DAY -> "Previous 30 days"
    else -> "Older"
}

/** Groups [items] (already newest-first) under [dayBucket] headings, keeping order. */
fun <T> groupByDay(items: List<T>, time: (T) -> Long, now: Long = System.currentTimeMillis()): List<Pair<String, List<T>>> {
    val out = LinkedHashMap<String, MutableList<T>>()
    for (item in items) out.getOrPut(dayBucket(time(item), now)) { mutableListOf() } += item
    return out.map { it.key to it.value }
}

enum class DateRange(val label: String) {
    ANY("All time"), TODAY("Today"), WEEK("7 days"), MONTH("30 days");

    fun contains(millis: Long, now: Long = System.currentTimeMillis()): Boolean = when (this) {
        ANY -> true
        TODAY -> millis >= startOfDay(now)
        WEEK -> millis >= startOfDay(now) - 6 * DAY
        MONTH -> millis >= startOfDay(now) - 29 * DAY
    }
}

private val DATE_FORMATS = listOf(
    "MMM d, yyyy", "MMMM d, yyyy", "MMM yyyy", "MMMM yyyy", "yyyy-MM-dd", "yyyy-MM",
    "M/d/yyyy", "d MMM yyyy", "EEEE", "EEE"
)

/**
 * Lets the workspace search box match a project's last-modified date: relative words ("today",
 * "yesterday", "this week", "this month", "this year") or any part of how the date is written
 * ("nov", "2026-11", "27", "monday", "nov 27, 2026").
 */
fun matchesDateQuery(query: String, millis: Long, now: Long = System.currentTimeMillis()): Boolean {
    val q = query.trim().lowercase(Locale.getDefault())
    if (q.isEmpty()) return false
    when (q) {
        "today" -> return DateRange.TODAY.contains(millis, now)
        "yesterday" -> return millis in (startOfDay(now) - DAY) until startOfDay(now)
        "week", "this week", "last 7 days" -> return DateRange.WEEK.contains(millis, now)
        "month", "this month", "last 30 days" -> return DateRange.MONTH.contains(millis, now)
        "year", "this year" -> {
            val a = Calendar.getInstance().apply { timeInMillis = millis }
            val b = Calendar.getInstance().apply { timeInMillis = now }
            return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
        }
    }
    val date = Date(millis)
    return DATE_FORMATS.any { SimpleDateFormat(it, Locale.getDefault()).format(date).lowercase(Locale.getDefault()).contains(q) }
}

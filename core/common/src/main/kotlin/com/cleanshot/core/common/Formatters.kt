package com.cleanshot.core.common

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Formatters {
    fun size(bytes: Long): String = when {
        abs(bytes) >= 1024L * 1024 -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
        abs(bytes) >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun sizeDelta(before: Long, after: Long): String {
        val d = after - before
        val pct = if (before > 0) d * 100.0 / before else 0.0
        val sign = if (d > 0) "+" else if (d < 0) "−" else ""
        return "$sign${size(abs(d))} (${String.format(Locale.getDefault(), "%+.1f", pct)}%)"
    }

    fun dateTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    fun date(millis: Long): String = DateFormat.getDateInstance(DateFormat.LONG).format(Date(millis))
}

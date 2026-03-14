package com.kitewatch.ui.formatter

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormatter {
    private val displayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    private val shortFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH)

    private val monthYearFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

    /** Returns "15 Mar 2024" */
    fun formatDisplay(date: LocalDate): String = date.format(displayFormatter)

    /** Returns "15/03/24" */
    fun formatShort(date: LocalDate): String = date.format(shortFormatter)

    /** Returns "Mar 2024" */
    fun formatMonthYear(date: LocalDate): String = date.format(monthYearFormatter)
}

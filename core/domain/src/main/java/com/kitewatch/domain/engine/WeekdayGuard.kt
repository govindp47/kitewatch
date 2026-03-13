package com.kitewatch.domain.engine

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Trading day guard (BR-09).
 *
 * Returns `false` on Saturday and Sunday; `true` on all other days.
 * Public holidays are explicitly out of scope — only weekends are excluded.
 */
object WeekdayGuard {
    /**
     * @param date Date to check; defaults to today.
     * @return `true` if [date] is a weekday (Mon–Fri), `false` if it is a weekend.
     */
    fun isTradingDay(date: LocalDate = LocalDate.now()): Boolean =
        date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}

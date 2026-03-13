package com.kitewatch.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekdayGuardTest {
    @Test
    fun `Monday is a trading day`() {
        assertTrue(WeekdayGuard.isTradingDay(LocalDate.of(2024, 3, 4))) // Monday
    }

    @Test
    fun `Tuesday is a trading day`() {
        assertTrue(WeekdayGuard.isTradingDay(LocalDate.of(2024, 3, 5))) // Tuesday
    }

    @Test
    fun `Wednesday is a trading day`() {
        assertTrue(WeekdayGuard.isTradingDay(LocalDate.of(2024, 3, 6))) // Wednesday
    }

    @Test
    fun `Thursday is a trading day`() {
        assertTrue(WeekdayGuard.isTradingDay(LocalDate.of(2024, 3, 7))) // Thursday
    }

    @Test
    fun `Friday is a trading day`() {
        assertTrue(WeekdayGuard.isTradingDay(LocalDate.of(2024, 3, 8))) // Friday
    }

    @Test
    fun `Saturday is not a trading day`() {
        assertFalse(WeekdayGuard.isTradingDay(LocalDate.of(2024, 3, 2))) // Saturday
    }

    @Test
    fun `Sunday is not a trading day`() {
        assertFalse(WeekdayGuard.isTradingDay(LocalDate.of(2024, 3, 3))) // Sunday
    }
}

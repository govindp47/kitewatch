package com.kitewatch.ui.formatter

import com.kitewatch.domain.model.Paisa
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatterTest {
    @Test
    fun format_oneLakh_usesIndianGrouping() {
        // 1,00,000 rupees = 1,00,00,000 paisa (100 paisa per rupee)
        // 1_00_00_000L = 10_000_000L = 10000000 paisa = ₹1,00,000.00
        val result = CurrencyFormatter.format(Paisa(1_00_00_000L))
        assertEquals("₹1,00,000.00", result)
    }

    @Test
    fun format_negative_prefixedWithMinus() {
        // -500 rupees = -50_000 paisa
        val result = CurrencyFormatter.format(Paisa(-50_000L))
        assertEquals("-₹500.00", result)
    }

    @Test
    fun format_zero_showsZero() {
        val result = CurrencyFormatter.format(Paisa(0L))
        assertEquals("₹0.00", result)
    }

    @Test
    fun format_smallAmount_twoDecimalPlaces() {
        // 150 paisa = ₹1.50
        val result = CurrencyFormatter.format(Paisa(150L))
        assertEquals("₹1.50", result)
    }

    @Test
    fun format_tenThousand_noExtraGrouping() {
        // 10,000 rupees = 10_000 * 100 = 1_000_000 paisa
        val result = CurrencyFormatter.format(Paisa(1_000_000L))
        assertEquals("₹10,000.00", result)
    }

    @Test
    fun format_crore_usesIndianGrouping() {
        // 1 crore rupees = 1,00,00,000.00 = 1_00_00_000 * 100 paisa
        val result = CurrencyFormatter.format(Paisa(1_00_00_000_00L))
        assertEquals("₹1,00,00,000.00", result)
    }
}

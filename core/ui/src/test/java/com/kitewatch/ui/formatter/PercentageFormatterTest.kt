package com.kitewatch.ui.formatter

import org.junit.Assert.assertEquals
import org.junit.Test

class PercentageFormatterTest {
    @Test
    fun format_500bps_fivePercent() {
        assertEquals("5.00%", PercentageFormatter.format(500))
    }

    @Test
    fun format_25bps_quarterPercent() {
        assertEquals("0.25%", PercentageFormatter.format(25))
    }

    @Test
    fun format_negative_negativeSign() {
        assertEquals("-2.50%", PercentageFormatter.format(-250))
    }

    @Test
    fun formatWithSign_positive_plusPrefix() {
        assertEquals("+5.00%", PercentageFormatter.formatWithSign(500))
    }

    @Test
    fun formatWithSign_negative_minusPrefix() {
        assertEquals("-2.50%", PercentageFormatter.formatWithSign(-250))
    }

    @Test
    fun formatWithSign_zero_plusPrefix() {
        assertEquals("+0.00%", PercentageFormatter.formatWithSign(0))
    }
}

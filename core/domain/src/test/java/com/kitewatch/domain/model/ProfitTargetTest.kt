package com.kitewatch.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfitTargetTest {
    // -------------------------------------------------------------------------
    // Percentage
    // -------------------------------------------------------------------------

    @Test
    fun `5 percent on 1 lakh rupees produces 5000 rupees`() {
        // ₹1,00,000 = 10_000_000 paisa; 5% = 500 bps → ₹5,000 = 500_000 paisa
        val invested = Paisa.fromRupees(100_000.0)
        val target = ProfitTarget.Percentage(basisPoints = 500)
        assertEquals(Paisa(500_000L), target.computeTargetProfit(invested))
    }

    @Test
    fun `Percentage displayValue formats correctly`() {
        assertEquals("5.00%", ProfitTarget.Percentage(500).displayValue)
        assertEquals("2.50%", ProfitTarget.Percentage(250).displayValue)
        assertEquals("0.50%", ProfitTarget.Percentage(50).displayValue)
        assertEquals("10.00%", ProfitTarget.Percentage(1000).displayValue)
    }

    @Test
    fun `Percentage zero bps produces zero profit`() {
        val target = ProfitTarget.Percentage(basisPoints = 0)
        assertEquals(Paisa.ZERO, target.computeTargetProfit(Paisa(1_000_000L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Percentage negative basisPoints throws`() {
        ProfitTarget.Percentage(basisPoints = -1)
    }

    // -------------------------------------------------------------------------
    // Absolute
    // -------------------------------------------------------------------------

    @Test
    fun `Absolute always returns fixed amount regardless of invested`() {
        val fixedProfit = Paisa(50_000L)
        val target = ProfitTarget.Absolute(amount = fixedProfit)
        assertEquals(fixedProfit, target.computeTargetProfit(Paisa(1_000_000L)))
        assertEquals(fixedProfit, target.computeTargetProfit(Paisa(500_000L)))
    }

    @Test
    fun `Absolute zero amount is valid`() {
        val target = ProfitTarget.Absolute(amount = Paisa.ZERO)
        assertEquals(Paisa.ZERO, target.computeTargetProfit(Paisa(1_000_000L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Absolute negative amount throws`() {
        ProfitTarget.Absolute(amount = Paisa(-1L))
    }
}

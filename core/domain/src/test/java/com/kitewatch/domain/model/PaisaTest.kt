package com.kitewatch.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PaisaTest {
    // -------------------------------------------------------------------------
    // applyBasisPoints
    // -------------------------------------------------------------------------

    @Test
    fun `applyBasisPoints 500 on 1_000_000 returns 50_000`() {
        assertEquals(Paisa(50_000L), Paisa(1_000_000L).applyBasisPoints(500))
    }

    @Test
    fun `applyBasisPoints rounds half-up at midpoint`() {
        // 1 paisa * 5000 bps = 0.5 paisa → rounds up to 1
        assertEquals(Paisa(1L), Paisa(1L).applyBasisPoints(5000))
    }

    @Test
    fun `applyBasisPoints zero bps returns zero`() {
        assertEquals(Paisa.ZERO, Paisa(1_000_000L).applyBasisPoints(0))
    }

    @Test
    fun `applyBasisPoints on ZERO returns ZERO`() {
        assertEquals(Paisa.ZERO, Paisa.ZERO.applyBasisPoints(500))
    }

    // -------------------------------------------------------------------------
    // toRupees / fromRupees round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `toRupees converts paisa to rupees with two decimal places`() {
        assertEquals(BigDecimal("123.45"), Paisa(12_345L).toRupees())
    }

    @Test
    fun `fromRupees BigDecimal round-trip`() {
        val rupees = BigDecimal("999.99")
        assertEquals(rupees, Paisa.fromRupees(rupees).toRupees())
    }

    @Test
    fun `fromRupees Double round-trip`() {
        assertEquals(Paisa(10_000L), Paisa.fromRupees(100.0))
    }

    @Test
    fun `ZERO toRupees is 0_00`() {
        assertEquals(BigDecimal("0.00"), Paisa.ZERO.toRupees())
    }

    // -------------------------------------------------------------------------
    // Division guard
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `div by zero Int throws IllegalArgumentException`() {
        Paisa(1_000L) / 0
    }

    @Test(expected = IllegalArgumentException::class)
    fun `div by zero Long throws IllegalArgumentException`() {
        Paisa(1_000L) / 0L
    }

    // -------------------------------------------------------------------------
    // Arithmetic operators
    // -------------------------------------------------------------------------

    @Test
    fun `subtraction can produce negative result`() {
        val result = Paisa(100L) - Paisa(200L)
        assertEquals(Paisa(-100L), result)
        assertTrue(result.isNegative())
        assertFalse(result.isPositive())
    }

    @Test
    fun `unaryMinus negates value`() {
        assertEquals(Paisa(-500L), -Paisa(500L))
    }

    @Test
    fun `plus adds two Paisa values`() {
        assertEquals(Paisa(300L), Paisa(100L) + Paisa(200L))
    }

    @Test
    fun `times Int scales correctly`() {
        assertEquals(Paisa(600L), Paisa(200L) * 3)
    }

    @Test
    fun `times Long scales correctly`() {
        assertEquals(Paisa(600L), Paisa(200L) * 3L)
    }

    @Test
    fun `div produces truncated result`() {
        assertEquals(Paisa(3L), Paisa(10L) / 3)
    }

    // -------------------------------------------------------------------------
    // Utility functions
    // -------------------------------------------------------------------------

    @Test
    fun `abs of negative returns positive`() {
        assertEquals(Paisa(100L), Paisa(-100L).abs())
    }

    @Test
    fun `isZero on ZERO is true`() {
        assertTrue(Paisa.ZERO.isZero())
    }

    @Test
    fun `compareTo orders correctly`() {
        assertTrue(Paisa(100L) > Paisa(50L))
        assertTrue(Paisa(50L) < Paisa(100L))
        assertEquals(0, Paisa(100L).compareTo(Paisa(100L)))
    }
}

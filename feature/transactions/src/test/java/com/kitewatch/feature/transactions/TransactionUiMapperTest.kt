package com.kitewatch.feature.transactions

import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.Transaction
import com.kitewatch.domain.model.TransactionSource
import com.kitewatch.domain.model.TransactionType
import com.kitewatch.feature.transactions.mapper.toUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransactionUiMapperTest {
    private fun makeTransaction(type: TransactionType) =
        Transaction(
            transactionId = 1L,
            type = type,
            referenceId = null,
            stockCode = if (type == TransactionType.EQUITY_BUY || type == TransactionType.EQUITY_SELL) "INFY" else null,
            amount = Paisa(1_000_00L),
            transactionDate = LocalDate.of(2025, 3, 15),
            description = "Test",
            source = TransactionSource.SYNC,
        )

    // ── Credit types (isCredit = true) ──────────────────────────────────────

    @Test
    fun `EQUITY_SELL maps to isCredit true`() {
        assertTrue(makeTransaction(TransactionType.EQUITY_SELL).toUiModel().isCredit)
    }

    @Test
    fun `FUND_DEPOSIT maps to isCredit true`() {
        assertTrue(makeTransaction(TransactionType.FUND_DEPOSIT).toUiModel().isCredit)
    }

    @Test
    fun `MISC_ADJUSTMENT maps to isCredit true`() {
        assertTrue(makeTransaction(TransactionType.MISC_ADJUSTMENT).toUiModel().isCredit)
    }

    // ── Debit types (isCredit = false) ──────────────────────────────────────

    @Test
    fun `EQUITY_BUY maps to isCredit false`() {
        assertFalse(makeTransaction(TransactionType.EQUITY_BUY).toUiModel().isCredit)
    }

    @Test
    fun `FUND_WITHDRAWAL maps to isCredit false`() {
        assertFalse(makeTransaction(TransactionType.FUND_WITHDRAWAL).toUiModel().isCredit)
    }

    @Test
    fun `STT_CHARGE maps to isCredit false`() {
        assertFalse(makeTransaction(TransactionType.STT_CHARGE).toUiModel().isCredit)
    }

    @Test
    fun `GST_CHARGE maps to isCredit false`() {
        assertFalse(makeTransaction(TransactionType.GST_CHARGE).toUiModel().isCredit)
    }

    @Test
    fun `EXCHANGE_CHARGE maps to isCredit false`() {
        assertFalse(makeTransaction(TransactionType.EXCHANGE_CHARGE).toUiModel().isCredit)
    }

    @Test
    fun `BROKERAGE_CHARGE maps to isCredit false`() {
        assertFalse(makeTransaction(TransactionType.BROKERAGE_CHARGE).toUiModel().isCredit)
    }

    // ── Field mapping ────────────────────────────────────────────────────────

    @Test
    fun `transactionId preserved`() {
        assertEquals(1L, makeTransaction(TransactionType.EQUITY_BUY).toUiModel().transactionId)
    }

    @Test
    fun `stockCode preserved for equity types`() {
        assertNotNull(makeTransaction(TransactionType.EQUITY_BUY).toUiModel().stockCode)
        assertEquals("INFY", makeTransaction(TransactionType.EQUITY_BUY).toUiModel().stockCode)
    }

    @Test
    fun `stockCode null for fund types`() {
        assertNull(makeTransaction(TransactionType.FUND_DEPOSIT).toUiModel().stockCode)
    }

    @Test
    fun `date formatted as display string`() {
        val model = makeTransaction(TransactionType.EQUITY_BUY).toUiModel()
        assertEquals("15 Mar 2025", model.date)
    }
}

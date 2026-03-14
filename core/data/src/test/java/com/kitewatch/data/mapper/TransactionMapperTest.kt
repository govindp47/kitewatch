package com.kitewatch.data.mapper

import com.kitewatch.database.entity.TransactionEntity
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.TransactionSource
import com.kitewatch.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TransactionMapperTest {
    private val entity =
        TransactionEntity(
            id = 1L,
            type = "EQUITY_BUY",
            subType = null,
            referenceId = "111111111",
            referenceType = "ORDER",
            stockCode = "INFY",
            amountPaisa = -1500000L,
            runningFundBalancePaisa = 5000000L,
            description = "Buy 10 INFY @ ₹1,500.00",
            transactionDate = "2024-01-15",
            source = "SYSTEM",
            createdAt = 1_700_000_000_000L,
        )

    @Test
    fun `entity toDomain maps all fields correctly`() {
        val domain = entity.toDomain()
        assertEquals(1L, domain.transactionId)
        assertEquals(TransactionType.EQUITY_BUY, domain.type)
        assertEquals("111111111", domain.referenceId)
        assertEquals("INFY", domain.stockCode)
        assertEquals(Paisa(-1500000L), domain.amount)
        assertEquals(LocalDate.of(2024, 1, 15), domain.transactionDate)
        assertEquals("Buy 10 INFY @ ₹1,500.00", domain.description)
        assertEquals(TransactionSource.SYNC, domain.source)
    }

    @Test
    fun `SYSTEM source maps to SYNC`() {
        assertEquals(TransactionSource.SYNC, entity.copy(source = "SYSTEM").toDomain().source)
    }

    @Test
    fun `MANUAL source maps to MANUAL`() {
        assertEquals(TransactionSource.MANUAL, entity.copy(source = "MANUAL").toDomain().source)
    }

    @Test
    fun `RECONCILIATION source maps to MANUAL`() {
        assertEquals(TransactionSource.MANUAL, entity.copy(source = "RECONCILIATION").toDomain().source)
    }

    @Test
    fun `GMAIL source maps to GMAIL_SCAN`() {
        assertEquals(TransactionSource.GMAIL_SCAN, entity.copy(source = "GMAIL").toDomain().source)
    }

    @Test
    fun `CSV_IMPORT source round-trips`() {
        assertEquals(TransactionSource.CSV_IMPORT, entity.copy(source = "CSV_IMPORT").toDomain().source)
    }

    @Test
    fun `domain toEntity source SYNC maps to SYSTEM`() {
        val domain = entity.toDomain().copy(source = TransactionSource.SYNC)
        assertEquals("SYSTEM", domain.toEntity().source)
    }

    @Test
    fun `domain toEntity source GMAIL_SCAN maps to GMAIL`() {
        val domain = entity.toDomain().copy(source = TransactionSource.GMAIL_SCAN)
        assertEquals("GMAIL", domain.toEntity().source)
    }

    @Test
    fun `round-trip preserves type and amount`() {
        val roundTripped = entity.toDomain().toEntity()
        assertEquals(entity.type, roundTripped.type)
        assertEquals(entity.amountPaisa, roundTripped.amountPaisa)
        assertEquals(entity.transactionDate, roundTripped.transactionDate)
        assertEquals(entity.referenceId, roundTripped.referenceId)
        assertEquals(entity.stockCode, roundTripped.stockCode)
        assertEquals(entity.description, roundTripped.description)
    }

    @Test
    fun `null stockCode preserved`() {
        val e = entity.copy(stockCode = null)
        assertNull(e.toDomain().stockCode)
        assertNull(e.toDomain().toEntity().stockCode)
    }

    @Test
    fun `null referenceId preserved`() {
        val e = entity.copy(referenceId = null)
        assertNull(e.toDomain().referenceId)
        assertNull(e.toDomain().toEntity().referenceId)
    }

    @Test
    fun `all transaction types parse without error`() {
        val types =
            listOf(
                "EQUITY_BUY",
                "EQUITY_SELL",
                "STT_CHARGE",
                "EXCHANGE_CHARGE",
                "SEBI_CHARGE",
                "GST_CHARGE",
                "STAMP_DUTY_CHARGE",
                "DP_CHARGE",
                "BROKERAGE_CHARGE",
                "FUND_DEPOSIT",
                "FUND_WITHDRAWAL",
                "MISC_ADJUSTMENT",
            )
        types.forEach { type ->
            val domain = entity.copy(type = type).toDomain()
            assertEquals(type, domain.type.name)
        }
    }
}

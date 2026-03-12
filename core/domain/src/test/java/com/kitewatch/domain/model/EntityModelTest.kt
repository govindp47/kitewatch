package com.kitewatch.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EntityModelTest {
    // -------------------------------------------------------------------------
    // Order — BR-04
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `Order zero quantity throws`() {
        Order(
            orderId = 1L,
            zerodhaOrderId = "Z001",
            stockCode = "INFY",
            stockName = "Infosys",
            orderType = OrderType.BUY,
            quantity = 0,
            price = Paisa(100_00L),
            totalValue = Paisa(0L),
            tradeDate = LocalDate.of(2024, 1, 15),
            exchange = Exchange.NSE,
            settlementId = null,
            source = OrderSource.SYNC,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Order zero price throws`() {
        Order(
            orderId = 1L,
            zerodhaOrderId = "Z001",
            stockCode = "INFY",
            stockName = "Infosys",
            orderType = OrderType.BUY,
            quantity = 10,
            price = Paisa(0L),
            totalValue = Paisa(0L),
            tradeDate = LocalDate.of(2024, 1, 15),
            exchange = Exchange.NSE,
            settlementId = null,
            source = OrderSource.SYNC,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Order negative price throws`() {
        Order(
            orderId = 1L,
            zerodhaOrderId = "Z001",
            stockCode = "INFY",
            stockName = "Infosys",
            orderType = OrderType.SELL,
            quantity = 5,
            price = Paisa(-100L),
            totalValue = Paisa(-500L),
            tradeDate = LocalDate.of(2024, 1, 15),
            exchange = Exchange.NSE,
            settlementId = null,
            source = OrderSource.SYNC,
        )
    }

    @Test
    fun `Order valid construction`() {
        val order =
            Order(
                orderId = 1L,
                zerodhaOrderId = "Z001",
                stockCode = "INFY",
                stockName = "Infosys",
                orderType = OrderType.BUY,
                quantity = 10,
                price = Paisa(150_000L),
                totalValue = Paisa(1_500_000L),
                tradeDate = LocalDate.of(2024, 1, 15),
                exchange = Exchange.NSE,
                settlementId = "2024015",
                source = OrderSource.SYNC,
            )
        assertEquals(10, order.quantity)
        assertEquals(Paisa(150_000L), order.price)
    }

    // -------------------------------------------------------------------------
    // Holding — BR-03
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `Holding negative quantity throws`() {
        Holding(
            holdingId = 1L,
            stockCode = "INFY",
            stockName = "Infosys",
            quantity = -1,
            avgBuyPrice = Paisa(150_000L),
            investedAmount = Paisa(1_500_000L),
            totalBuyCharges = Paisa(500L),
            profitTarget = ProfitTarget.Percentage(500),
            targetSellPrice = Paisa(160_000L),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
    }

    @Test
    fun `Holding zero quantity is valid (sold out)`() {
        val holding =
            Holding(
                holdingId = 1L,
                stockCode = "INFY",
                stockName = "Infosys",
                quantity = 0,
                avgBuyPrice = Paisa.ZERO,
                investedAmount = Paisa.ZERO,
                totalBuyCharges = Paisa.ZERO,
                profitTarget = ProfitTarget.Percentage(500),
                targetSellPrice = Paisa.ZERO,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        assertEquals(0, holding.quantity)
    }

    @Test
    fun `Holding positive quantity is valid`() {
        val holding =
            Holding(
                holdingId = 1L,
                stockCode = "INFY",
                stockName = "Infosys",
                quantity = 10,
                avgBuyPrice = Paisa(150_000L),
                investedAmount = Paisa(1_500_000L),
                totalBuyCharges = Paisa(500L),
                profitTarget = ProfitTarget.Percentage(500),
                targetSellPrice = Paisa(160_000L),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        assertEquals(10, holding.quantity)
    }

    // -------------------------------------------------------------------------
    // FundEntry — BR-05
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `FundEntry zero amount throws`() {
        FundEntry(
            entryId = 1L,
            entryType = FundEntryType.DEPOSIT,
            amount = Paisa(0L),
            entryDate = LocalDate.of(2024, 1, 15),
            note = null,
            gmailMessageId = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `FundEntry negative amount throws`() {
        FundEntry(
            entryId = 1L,
            entryType = FundEntryType.DEPOSIT,
            amount = Paisa(-100L),
            entryDate = LocalDate.of(2024, 1, 15),
            note = null,
            gmailMessageId = null,
        )
    }

    @Test
    fun `FundEntry positive amount is valid`() {
        val entry =
            FundEntry(
                entryId = 1L,
                entryType = FundEntryType.DEPOSIT,
                amount = Paisa(5_000_00L),
                entryDate = LocalDate.of(2024, 1, 15),
                note = "Monthly savings",
                gmailMessageId = "msg-abc123",
            )
        assertTrue(entry.amount.isPositive())
    }

    // -------------------------------------------------------------------------
    // ChargeBreakdown.total()
    // -------------------------------------------------------------------------

    @Test
    fun `ChargeBreakdown total sums all components`() {
        val breakdown =
            ChargeBreakdown(
                stt = Paisa(1000L),
                exchangeTxn = Paisa(297L),
                sebiCharges = Paisa(10L),
                stampDuty = Paisa(150L),
                gst = Paisa(55L),
            )
        val expected = Paisa(1000L + 297L + 10L + 150L + 55L)
        assertEquals(expected, breakdown.total())
    }

    @Test
    fun `ChargeBreakdown total with all zeros is zero`() {
        val breakdown =
            ChargeBreakdown(
                stt = Paisa.ZERO,
                exchangeTxn = Paisa.ZERO,
                sebiCharges = Paisa.ZERO,
                stampDuty = Paisa.ZERO,
                gst = Paisa.ZERO,
            )
        assertEquals(Paisa.ZERO, breakdown.total())
    }

    // -------------------------------------------------------------------------
    // SyncResult sealed exhaustiveness
    // -------------------------------------------------------------------------

    @Test
    fun `SyncResult when expression is exhaustive`() {
        val results: List<SyncResult> =
            listOf(
                SyncResult.Success(newOrderCount = 5, updatedGttCount = 2),
                SyncResult.NoNewOrders,
                SyncResult.Skipped(reason = "Weekend"),
                SyncResult.Partial(succeeded = 3, failed = 1),
            )

        for (result in results) {
            val label =
                when (result) {
                    is SyncResult.Success -> "success"
                    is SyncResult.NoNewOrders -> "none"
                    is SyncResult.Skipped -> "skipped"
                    is SyncResult.Partial -> "partial"
                }
            assertTrue(label.isNotEmpty())
        }
    }

    @Test
    fun `SyncResult Success carries order and gtt counts`() {
        val result = SyncResult.Success(newOrderCount = 3, updatedGttCount = 1)
        assertEquals(3, result.newOrderCount)
        assertEquals(1, result.updatedGttCount)
    }
}

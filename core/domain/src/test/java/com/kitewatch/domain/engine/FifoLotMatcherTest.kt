package com.kitewatch.domain.engine

import com.kitewatch.domain.model.Paisa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Edge-case test suite for [FifoLotMatcher.match] (T-026).
 *
 * Covers all 10 required scenarios plus achieves 100% branch coverage on [FifoLotMatcher.match].
 */
class FifoLotMatcherTest {
    private val jan1 = LocalDate.of(2024, 1, 1)
    private val jan2 = LocalDate.of(2024, 1, 2)
    private val jan3 = LocalDate.of(2024, 1, 3)

    private fun lot(
        orderId: Long,
        qty: Int,
        pricePerUnit: Long,
        date: LocalDate,
    ) = BuyLot(
        orderId = orderId,
        quantity = qty,
        pricePerUnit = Paisa(pricePerUnit),
        totalValue = Paisa(pricePerUnit * qty),
        tradeDate = date,
    )

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Single lot, full sell
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `EC1 - single lot full sell - matchedCostBasis equals lot totalValue, remaining empty`() {
        val lotA = lot(orderId = 1L, qty = 10, pricePerUnit = 100L, date = jan1) // totalValue = 1000

        val result = FifoLotMatcher.match(listOf(lotA), sellQuantity = 10)

        assertEquals(1, result.matchedLots.size)
        val match = result.matchedLots[0]
        assertEquals(1L, match.orderId)
        assertEquals(10, match.matchedQty)
        assertEquals(Paisa(1000L), match.costBasisForMatchedQty) // (1000 * 10) / 10

        assertEquals(Paisa(1000L), result.matchedCostBasis)
        assertTrue(result.remainingLots.isEmpty())
        assertEquals(0, result.overSellQuantity)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Single lot, partial sell
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `EC2 - single lot partial sell - proportional cost basis, correct remaining quantity`() {
        val lotA = lot(orderId = 1L, qty = 10, pricePerUnit = 100L, date = jan1) // totalValue = 1000

        val result = FifoLotMatcher.match(listOf(lotA), sellQuantity = 4)

        assertEquals(1, result.matchedLots.size)
        val match = result.matchedLots[0]
        assertEquals(1L, match.orderId)
        assertEquals(4, match.matchedQty)
        assertEquals(Paisa(400L), match.costBasisForMatchedQty) // (1000 * 4) / 10

        assertEquals(Paisa(400L), result.matchedCostBasis)

        assertEquals(1, result.remainingLots.size)
        val remaining = result.remainingLots[0]
        assertEquals(1L, remaining.orderId)
        assertEquals(6, remaining.quantity)
        assertEquals(Paisa(600L), remaining.totalValue) // 1000 - 400

        assertEquals(0, result.overSellQuantity)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Two lots same price, sell spanning both: lot 1 fully consumed, lot 2 partially consumed
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `EC3 - two lots same price sell spanning both - lot1 fully consumed lot2 partially consumed`() {
        val lotA = lot(orderId = 1L, qty = 10, pricePerUnit = 100L, date = jan1) // totalValue = 1000
        val lotB = lot(orderId = 2L, qty = 10, pricePerUnit = 100L, date = jan2) // totalValue = 1000

        val result = FifoLotMatcher.match(listOf(lotA, lotB), sellQuantity = 15)

        assertEquals(2, result.matchedLots.size)

        val matchA = result.matchedLots[0]
        assertEquals(1L, matchA.orderId)
        assertEquals(10, matchA.matchedQty)
        assertEquals(Paisa(1000L), matchA.costBasisForMatchedQty) // (1000 * 10) / 10

        val matchB = result.matchedLots[1]
        assertEquals(2L, matchB.orderId)
        assertEquals(5, matchB.matchedQty)
        assertEquals(Paisa(500L), matchB.costBasisForMatchedQty) // (1000 * 5) / 10

        assertEquals(Paisa(1500L), result.matchedCostBasis)

        assertEquals(1, result.remainingLots.size)
        val remaining = result.remainingLots[0]
        assertEquals(2L, remaining.orderId)
        assertEquals(5, remaining.quantity)
        assertEquals(Paisa(500L), remaining.totalValue) // 1000 - 500

        assertEquals(0, result.overSellQuantity)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Two lots different prices, sell spanning both: oldest lot price used first
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `EC4 - two lots different prices - oldest lot consumed first regardless of input order`() {
        val olderLot = lot(orderId = 1L, qty = 5, pricePerUnit = 100L, date = jan1) // totalValue = 500
        val newerLot = lot(orderId = 2L, qty = 5, pricePerUnit = 200L, date = jan2) // totalValue = 1000

        // Provide in reverse order — matcher must sort by tradeDate ASC
        val result = FifoLotMatcher.match(listOf(newerLot, olderLot), sellQuantity = 7)

        // Lot 1 (older, 100/share) fully consumed; lot 2 (newer, 200/share) partially consumed
        assertEquals(2, result.matchedLots.size)

        val matchOld = result.matchedLots[0]
        assertEquals(1L, matchOld.orderId) // older lot consumed first
        assertEquals(5, matchOld.matchedQty)
        assertEquals(Paisa(500L), matchOld.costBasisForMatchedQty) // (500 * 5) / 5

        val matchNew = result.matchedLots[1]
        assertEquals(2L, matchNew.orderId)
        assertEquals(2, matchNew.matchedQty)
        assertEquals(Paisa(400L), matchNew.costBasisForMatchedQty) // (1000 * 2) / 5

        assertEquals(Paisa(900L), result.matchedCostBasis) // 500 + 400

        assertEquals(1, result.remainingLots.size)
        val remaining = result.remainingLots[0]
        assertEquals(2L, remaining.orderId)
        assertEquals(3, remaining.quantity)
        assertEquals(Paisa(600L), remaining.totalValue) // 1000 - 400

        assertEquals(0, result.overSellQuantity)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Three lots, sell all: remaining empty, overSellQuantity = 0
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `EC5 - three lots sell all - remaining empty, overSellQuantity zero`() {
        val lotA = lot(orderId = 1L, qty = 5, pricePerUnit = 100L, date = jan1) // totalValue = 500
        val lotB = lot(orderId = 2L, qty = 5, pricePerUnit = 150L, date = jan2) // totalValue = 750
        val lotC = lot(orderId = 3L, qty = 5, pricePerUnit = 200L, date = jan3) // totalValue = 1000

        val result = FifoLotMatcher.match(listOf(lotA, lotB, lotC), sellQuantity = 15)

        assertEquals(3, result.matchedLots.size)
        assertEquals(Paisa(500L), result.matchedLots[0].costBasisForMatchedQty)
        assertEquals(Paisa(750L), result.matchedLots[1].costBasisForMatchedQty)
        assertEquals(Paisa(1000L), result.matchedLots[2].costBasisForMatchedQty)

        assertEquals(Paisa(2250L), result.matchedCostBasis) // 500 + 750 + 1000
        assertTrue(result.remainingLots.isEmpty())
        assertEquals(0, result.overSellQuantity)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. Sell > total buy quantity: overSellQuantity > 0, remaining empty
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `EC6 - sell exceeds available inventory - overSellQuantity recorded, remaining empty`() {
        val lotA = lot(orderId = 1L, qty = 5, pricePerUnit = 100L, date = jan1) // totalValue = 500

        val result = FifoLotMatcher.match(listOf(lotA), sellQuantity = 8)

        assertEquals(1, result.matchedLots.size)
        assertEquals(5, result.matchedLots[0].matchedQty)
        assertEquals(Paisa(500L), result.matchedCostBasis)
        assertTrue(result.remainingLots.isEmpty())
        assertEquals(3, result.overSellQuantity) // 8 - 5
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. sellQuantity = 0: no matches, original lots unchanged
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `EC7 - sell quantity zero - no matches, all lots returned unchanged`() {
        val lotA = lot(orderId = 1L, qty = 10, pricePerUnit = 100L, date = jan1)
        val lotB = lot(orderId = 2L, qty = 5, pricePerUnit = 200L, date = jan2)

        val result = FifoLotMatcher.match(listOf(lotA, lotB), sellQuantity = 0)

        assertTrue(result.matchedLots.isEmpty())
        assertEquals(Paisa.ZERO, result.matchedCostBasis)
        assertEquals(2, result.remainingLots.size)
        assertEquals(1L, result.remainingLots[0].orderId) // sorted by date
        assertEquals(2L, result.remainingLots[1].orderId)
        assertEquals(0, result.overSellQuantity)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 8. Zero buy lots, any sell: overSellQuantity = sellQuantity
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `EC8 - zero buy lots with sell - overSellQuantity equals sell quantity`() {
        val result = FifoLotMatcher.match(emptyList(), sellQuantity = 5)

        assertTrue(result.matchedLots.isEmpty())
        assertEquals(Paisa.ZERO, result.matchedCostBasis)
        assertTrue(result.remainingLots.isEmpty())
        assertEquals(5, result.overSellQuantity)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 9. Multiple rounds: sell → buy more → sell again → correct state
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `EC9 - multiple sequential sell rounds with buy in between - correct running state`() {
        // Round 1: Lot A = 10 shares @ 100/share = 1000, sell 7
        val lotA = lot(orderId = 1L, qty = 10, pricePerUnit = 100L, date = jan1)
        val round1 = FifoLotMatcher.match(listOf(lotA), sellQuantity = 7)

        assertEquals(1, round1.matchedLots.size)
        assertEquals(7, round1.matchedLots[0].matchedQty)
        assertEquals(Paisa(700L), round1.matchedCostBasis) // (1000 * 7) / 10
        assertEquals(1, round1.remainingLots.size)
        assertEquals(3, round1.remainingLots[0].quantity)
        assertEquals(Paisa(300L), round1.remainingLots[0].totalValue) // 1000 - 700

        // New buy: Lot B = 5 shares @ 100/share = 500, date jan2
        val lotB = lot(orderId = 2L, qty = 5, pricePerUnit = 100L, date = jan2)
        val lotsAfterBuy = round1.remainingLots + lotB // [lotA residual (3), lotB (5)]

        // Round 2: sell 6 from [lotA-residual(3, tv=300), lotB(5, tv=500)]
        val round2 = FifoLotMatcher.match(lotsAfterBuy, sellQuantity = 6)

        assertEquals(2, round2.matchedLots.size)

        // LotA residual fully consumed (3 shares, tv=300)
        val matchResidualA = round2.matchedLots[0]
        assertEquals(1L, matchResidualA.orderId)
        assertEquals(3, matchResidualA.matchedQty)
        assertEquals(Paisa(300L), matchResidualA.costBasisForMatchedQty) // (300 * 3) / 3

        // LotB partially consumed (3 of 5, tv=500)
        val matchB = round2.matchedLots[1]
        assertEquals(2L, matchB.orderId)
        assertEquals(3, matchB.matchedQty)
        assertEquals(Paisa(300L), matchB.costBasisForMatchedQty) // (500 * 3) / 5

        assertEquals(Paisa(600L), round2.matchedCostBasis)

        // Remaining: 2 shares of LotB
        assertEquals(1, round2.remainingLots.size)
        assertEquals(2L, round2.remainingLots[0].orderId)
        assertEquals(2, round2.remainingLots[0].quantity)
        assertEquals(Paisa(200L), round2.remainingLots[0].totalValue) // 500 - 300

        assertEquals(0, round2.overSellQuantity)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 10. Re-buy after full exit: no contamination from prior lots
    //     Sell all 10 shares, buy 5 new; holdings = 5 at new price only.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `EC10 - rebuy after full exit - new holdings reflect only new lot, no contamination`() {
        // Phase 1: full exit from lot A
        val lotA = lot(orderId = 1L, qty = 10, pricePerUnit = 100L, date = jan1) // tv = 1000
        val exitResult = FifoLotMatcher.match(listOf(lotA), sellQuantity = 10)

        assertTrue(exitResult.remainingLots.isEmpty())
        assertEquals(0, exitResult.overSellQuantity)

        // Phase 2: new buy (different price) — no old lots in the pool
        val newLot = lot(orderId = 2L, qty = 5, pricePerUnit = 500L, date = jan3) // tv = 2500
        val newPool = exitResult.remainingLots + newLot // [] + [newLot] = [newLot]

        // Sell 2 from new pool
        val rebuyResult = FifoLotMatcher.match(newPool, sellQuantity = 2)

        // Cost basis must be at new price (500/share), not old price (100/share)
        assertEquals(1, rebuyResult.matchedLots.size)
        assertEquals(2L, rebuyResult.matchedLots[0].orderId)
        assertEquals(2, rebuyResult.matchedLots[0].matchedQty)
        assertEquals(Paisa(1000L), rebuyResult.matchedLots[0].costBasisForMatchedQty) // (2500 * 2) / 5

        assertEquals(Paisa(1000L), rebuyResult.matchedCostBasis)

        // Remaining: 3 shares of new lot at new price
        assertEquals(1, rebuyResult.remainingLots.size)
        assertEquals(2L, rebuyResult.remainingLots[0].orderId)
        assertEquals(3, rebuyResult.remainingLots[0].quantity)
        assertEquals(Paisa(1500L), rebuyResult.remainingLots[0].totalValue) // 2500 - 1000

        assertEquals(0, rebuyResult.overSellQuantity)
    }
}

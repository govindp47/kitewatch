package com.kitewatch.domain.engine

import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import com.kitewatch.domain.repository.RemoteHolding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HoldingsVerifierTest {
    // ─── Test 1: Identical CNC lists → Verified ────────────────────────────────

    @Test
    fun `Verified returned when remote CNC and local quantities match`() {
        val result =
            HoldingsVerifier.verify(
                remoteHoldings = listOf(remote("INFY", 10), remote("TCS", 5)),
                localHoldings = listOf(local("INFY", 10), local("TCS", 5)),
            )
        assertTrue(result is HoldingsVerificationResult.Verified)
    }

    // ─── Test 2: Quantity differs → QuantityMismatch ───────────────────────────

    @Test
    fun `QuantityMismatch diff returned when remote and local qty differ`() {
        val result =
            HoldingsVerifier.verify(
                remoteHoldings = listOf(remote("INFY", 10)),
                localHoldings = listOf(local("INFY", 5)),
            )
        assertTrue(result is HoldingsVerificationResult.Mismatch)
        val diffs = (result as HoldingsVerificationResult.Mismatch).diffs
        assertEquals(1, diffs.size)
        val diff = diffs[0] as HoldingDiff.QuantityMismatch
        assertEquals("INFY", diff.stockCode)
        assertEquals(10, diff.remoteQty)
        assertEquals(5, diff.localQty)
    }

    // ─── Test 3: Remote only → MissingLocal ────────────────────────────────────

    @Test
    fun `MissingLocal diff returned when stock present remotely but absent locally`() {
        val result =
            HoldingsVerifier.verify(
                remoteHoldings = listOf(remote("WIPRO", 20)),
                localHoldings = emptyList(),
            )
        assertTrue(result is HoldingsVerificationResult.Mismatch)
        val diff = (result as HoldingsVerificationResult.Mismatch).diffs[0]
        assertTrue(diff is HoldingDiff.MissingLocal)
        assertEquals("WIPRO", (diff as HoldingDiff.MissingLocal).stockCode)
        assertEquals(20, diff.remoteQty)
    }

    // ─── Test 4: Local only → MissingRemote ────────────────────────────────────

    @Test
    fun `MissingRemote diff returned when stock has local qty but absent remotely`() {
        val result =
            HoldingsVerifier.verify(
                remoteHoldings = emptyList(),
                localHoldings = listOf(local("RELIANCE", 3)),
            )
        assertTrue(result is HoldingsVerificationResult.Mismatch)
        val diff = (result as HoldingsVerificationResult.Mismatch).diffs[0]
        assertTrue(diff is HoldingDiff.MissingRemote)
        assertEquals("RELIANCE", (diff as HoldingDiff.MissingRemote).stockCode)
        assertEquals(3, diff.localQty)
    }

    // ─── Test 5: Non-CNC remote holdings are ignored ───────────────────────────

    @Test
    fun `Non-CNC remote products are filtered out and do not cause diffs`() {
        val result =
            HoldingsVerifier.verify(
                remoteHoldings =
                    listOf(
                        RemoteHolding("INFY", 10, "CNC"),
                        RemoteHolding("NIFTY-FUT", 1, "NRML"), // F&O — must be ignored
                        RemoteHolding("INFY-MIS", 5, "MIS"), // intraday — must be ignored
                    ),
                localHoldings = listOf(local("INFY", 10)),
            )
        assertTrue(result is HoldingsVerificationResult.Verified)
    }

    // ─── Test 6: Zero-qty local holdings are treated as absent ─────────────────

    @Test
    fun `Local holdings with qty 0 are treated as absent`() {
        val result =
            HoldingsVerifier.verify(
                remoteHoldings = emptyList(),
                localHoldings = listOf(local("HDFC", quantity = 0)),
            )
        // qty=0 local is filtered out → no mismatch
        assertTrue(result is HoldingsVerificationResult.Verified)
    }

    // ─── Test 7: Both empty → Verified ─────────────────────────────────────────

    @Test
    fun `Verified returned when both remote and local lists are empty`() {
        val result = HoldingsVerifier.verify(emptyList(), emptyList())
        assertTrue(result is HoldingsVerificationResult.Verified)
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun remote(
        stockCode: String,
        qty: Int,
        product: String = "CNC",
    ) = RemoteHolding(tradingSymbol = stockCode, quantity = qty, product = product)

    private fun local(
        stockCode: String,
        quantity: Int,
    ) = Holding(
        holdingId = 1L,
        stockCode = stockCode,
        stockName = "$stockCode Ltd",
        quantity = quantity,
        avgBuyPrice = Paisa(100_00L),
        investedAmount = Paisa(quantity * 100_00L),
        totalBuyCharges = Paisa.ZERO,
        profitTarget = ProfitTarget.Percentage(500),
        targetSellPrice = Paisa(105_00L),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}

package com.kitewatch.domain.engine

import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GttAutomationEngineTest {
    // ─── Fixtures ────────────────────────────────────────────────────────────

    private val standardRates =
        ChargeRateSnapshot(
            brokerageDeliveryMilliBps = 0,
            sttBuyMilliBps = 10_000,
            sttSellMilliBps = 10_000,
            exchangeNseMilliBps = 297,
            exchangeBseMilliBps = 375,
            gstMilliBps = 1_800_000,
            sebiChargePerCrorePaisa = Paisa(1_000),
            stampDutyBuyMilliBps = 1_500,
            dpChargesPerScriptPaisa = Paisa(1_580),
            fetchedAt = Instant.EPOCH,
        )

    private val fivePctTarget = ProfitTarget.Percentage(500)

    /** Create a minimal ComputedHolding with no charges and no lots (sufficient for engine tests). */
    private fun holding(
        stockCode: String,
        quantity: Int,
        investedAmount: Paisa = Paisa(quantity * 100_00L), // ₹100/share default
    ) = ComputedHolding(
        stockCode = stockCode,
        quantity = quantity,
        avgBuyPrice = if (quantity > 0) investedAmount / quantity else Paisa.ZERO,
        investedAmount = investedAmount,
        remainingLots = emptyList(),
        totalBuyCharges = Paisa.ZERO,
    )

    /** Create a GttRecord with [triggerPrice] recorded as the last known Zerodha price. */
    @Suppress("LongParameterList") // Test helper mirrors GttRecord's 6-field creation; no natural grouping.
    private fun gtt(
        stockCode: String,
        gttId: Long = 1L,
        triggerPrice: Paisa,
        quantity: Int,
        isAppManaged: Boolean = true,
        status: GttStatus = GttStatus.ACTIVE,
    ) = GttRecord(
        gttId = gttId,
        zerodhaGttId = "zerodha-$gttId",
        stockCode = stockCode,
        triggerPrice = triggerPrice,
        quantity = quantity,
        status = status,
        isAppManaged = isAppManaged,
        lastSyncedAt = Instant.EPOCH,
    )

    /** Compute the app's target price for a given holding and profit target. */
    private fun computeTargetPrice(
        h: ComputedHolding,
        target: ProfitTarget = fivePctTarget,
    ) = TargetPriceCalculator.compute(
        avgBuyPrice = h.avgBuyPrice,
        quantity = h.quantity,
        profitTarget = target,
        investedAmount = h.investedAmount,
        buyCharges = h.totalBuyCharges,
        chargeRates = standardRates,
    )

    // ─── Test 1: CreateGtt — active holding with no existing GTT ─────────────

    @Test
    fun `CreateGtt emitted when holding is active and no GTT exists`() {
        val h = holding("INFY", quantity = 10)
        val expectedPrice = computeTargetPrice(h)

        val actions =
            GttAutomationEngine.evaluate(
                holdings = listOf(h),
                existingGtts = emptyMap(),
                chargeRates = standardRates,
                profitTargets = mapOf("INFY" to fivePctTarget),
            )

        assertEquals(1, actions.size)
        val action = actions.single() as GttAction.CreateGtt
        assertEquals("INFY", action.stockCode)
        assertEquals(10, action.quantity)
        assertEquals(expectedPrice, action.targetPrice)
    }

    // ─── Test 2: NoAction — app-managed GTT, price within 1 paisa ────────────

    @Test
    fun `NoAction emitted when app-managed GTT price matches computed price exactly`() {
        val h = holding("RELIANCE", quantity = 5)
        val appPrice = computeTargetPrice(h)
        val existingGtt = gtt("RELIANCE", triggerPrice = appPrice, quantity = 5)

        val actions =
            GttAutomationEngine.evaluate(
                holdings = listOf(h),
                existingGtts = mapOf("RELIANCE" to existingGtt),
                chargeRates = standardRates,
                profitTargets = mapOf("RELIANCE" to fivePctTarget),
            )

        assertEquals(1, actions.size)
        assertTrue(actions.single() is GttAction.NoAction)
        assertEquals("RELIANCE", (actions.single() as GttAction.NoAction).stockCode)
    }

    // ─── Test 3: UpdateGtt — app-managed GTT, price differs by > 1 paisa ────

    @Test
    fun `UpdateGtt emitted when app-managed GTT has stale price`() {
        val h = holding("TCS", quantity = 3)
        val appPrice = computeTargetPrice(h)
        // Stale price — 1000 paisa off (₹10 lower than required)
        val stalePaisa = Paisa(appPrice.value - 1_000)
        val existingGtt = gtt("TCS", gttId = 42L, triggerPrice = stalePaisa, quantity = 3)

        val actions =
            GttAutomationEngine.evaluate(
                holdings = listOf(h),
                existingGtts = mapOf("TCS" to existingGtt),
                chargeRates = standardRates,
                profitTargets = mapOf("TCS" to fivePctTarget),
            )

        assertEquals(1, actions.size)
        val action = actions.single() as GttAction.UpdateGtt
        assertEquals(42L, action.gttId)
        assertEquals(3, action.newQuantity)
        assertEquals(appPrice, action.newTargetPrice)
    }

    // ─── Test 4: FlagManualOverride — GTT not app-managed ────────────────────

    @Test
    fun `FlagManualOverride emitted when GTT isAppManaged is false`() {
        val h = holding("HDFC", quantity = 8)
        val appPrice = computeTargetPrice(h)
        val userSetPrice = Paisa(appPrice.value + 50_000L) // user set a different price
        val existingGtt =
            gtt(
                "HDFC",
                gttId = 99L,
                triggerPrice = userSetPrice,
                quantity = 8,
                isAppManaged = false,
            )

        val actions =
            GttAutomationEngine.evaluate(
                holdings = listOf(h),
                existingGtts = mapOf("HDFC" to existingGtt),
                chargeRates = standardRates,
                profitTargets = mapOf("HDFC" to fivePctTarget),
            )

        assertEquals(1, actions.size)
        val action = actions.single() as GttAction.FlagManualOverride
        assertEquals(99L, action.gttId)
        assertEquals(appPrice, action.appTargetPrice)
        assertEquals(userSetPrice, action.zerodhaActualPrice)
    }

    // ─── Test 5: ArchiveGtt — quantity=0 with active GTT ─────────────────────

    @Test
    fun `ArchiveGtt emitted when holding quantity is 0 and active GTT exists`() {
        val h = holding("WIPRO", quantity = 0, investedAmount = Paisa.ZERO)
        val existingGtt = gtt("WIPRO", gttId = 7L, triggerPrice = Paisa(150_000), quantity = 10)

        val actions =
            GttAutomationEngine.evaluate(
                holdings = listOf(h),
                existingGtts = mapOf("WIPRO" to existingGtt),
                chargeRates = standardRates,
                profitTargets = emptyMap(),
            )

        assertEquals(1, actions.size)
        val action = actions.single() as GttAction.ArchiveGtt
        assertEquals(7L, action.gttId)
    }

    // ─── Test 6: No action for quantity=0 with no GTT ────────────────────────

    @Test
    fun `No action emitted when holding quantity is 0 and no GTT exists`() {
        val h = holding("AXIS", quantity = 0, investedAmount = Paisa.ZERO)

        val actions =
            GttAutomationEngine.evaluate(
                holdings = listOf(h),
                existingGtts = emptyMap(),
                chargeRates = standardRates,
                profitTargets = emptyMap(),
            )

        assertTrue(actions.isEmpty())
    }

    // ─── Test 7: Idempotency — same state → same actions ─────────────────────

    @Test
    fun `evaluate is idempotent - same inputs produce same outputs`() {
        val h = holding("BAJAJ", quantity = 20)

        val firstRun =
            GttAutomationEngine.evaluate(
                holdings = listOf(h),
                existingGtts = emptyMap(),
                chargeRates = standardRates,
                profitTargets = mapOf("BAJAJ" to fivePctTarget),
            )

        val secondRun =
            GttAutomationEngine.evaluate(
                holdings = listOf(h),
                existingGtts = emptyMap(),
                chargeRates = standardRates,
                profitTargets = mapOf("BAJAJ" to fivePctTarget),
            )

        assertEquals(firstRun, secondRun)
        assertEquals(1, firstRun.size)
        assertTrue(firstRun.single() is GttAction.CreateGtt)
    }

    // ─── Test 8: Multiple stocks — mix of all action types ───────────────────

    @Test
    fun `evaluate returns correct mixed actions for multiple stocks`() {
        val hCreate = holding("STOCK_A", quantity = 5)
        val hNoAction = holding("STOCK_B", quantity = 3)
        val hUpdate = holding("STOCK_C", quantity = 4)
        val hOverride = holding("STOCK_D", quantity = 6)
        val hArchive = holding("STOCK_E", quantity = 0, investedAmount = Paisa.ZERO)
        val hNone = holding("STOCK_F", quantity = 0, investedAmount = Paisa.ZERO)

        val priceB = computeTargetPrice(hNoAction)
        val priceC = computeTargetPrice(hUpdate)
        val priceD = computeTargetPrice(hOverride)

        val existingGtts =
            mapOf(
                "STOCK_B" to gtt("STOCK_B", gttId = 2L, triggerPrice = priceB, quantity = 3),
                "STOCK_C" to gtt("STOCK_C", gttId = 3L, triggerPrice = Paisa(priceC.value - 5_000), quantity = 4),
                "STOCK_D" to gtt("STOCK_D", gttId = 4L, triggerPrice = priceD, quantity = 6, isAppManaged = false),
                "STOCK_E" to gtt("STOCK_E", gttId = 5L, triggerPrice = Paisa(200_000), quantity = 10),
            )

        val profitTargets =
            mapOf(
                "STOCK_A" to fivePctTarget,
                "STOCK_B" to fivePctTarget,
                "STOCK_C" to fivePctTarget,
                "STOCK_D" to fivePctTarget,
            )

        val actions =
            GttAutomationEngine.evaluate(
                holdings = listOf(hCreate, hNoAction, hUpdate, hOverride, hArchive, hNone),
                existingGtts = existingGtts,
                chargeRates = standardRates,
                profitTargets = profitTargets,
            )

        // STOCK_F has qty=0 and no GTT → no action (total = 5 actions, not 6)
        assertEquals(5, actions.size)

        val byStock: Map<String, GttAction> =
            actions.associateBy { action ->
                when (action) {
                    is GttAction.CreateGtt -> action.stockCode
                    is GttAction.NoAction -> action.stockCode
                    is GttAction.UpdateGtt -> existingGtts.entries.first { it.value.gttId == action.gttId }.key
                    is GttAction.FlagManualOverride -> existingGtts.entries.first { it.value.gttId == action.gttId }.key
                    is GttAction.ArchiveGtt -> existingGtts.entries.first { it.value.gttId == action.gttId }.key
                }
            }

        assertTrue(byStock["STOCK_A"] is GttAction.CreateGtt)
        assertTrue(byStock["STOCK_B"] is GttAction.NoAction)
        assertTrue(byStock["STOCK_C"] is GttAction.UpdateGtt)
        assertTrue(byStock["STOCK_D"] is GttAction.FlagManualOverride)
        assertTrue(byStock["STOCK_E"] is GttAction.ArchiveGtt)
    }

    // ─── Test 9: Tolerance boundary ──────────────────────────────────────────

    @Test
    fun `NoAction when GTT price differs by exactly 1 paisa, UpdateGtt when differs by 2`() {
        val h = holding("SBIN", quantity = 10)
        val appPrice = computeTargetPrice(h)

        // Diff = 1 paisa → NoAction
        val gttOnePaisa = gtt("SBIN", gttId = 1L, triggerPrice = Paisa(appPrice.value + 1L), quantity = 10)
        val actionsOne =
            GttAutomationEngine.evaluate(
                holdings = listOf(h),
                existingGtts = mapOf("SBIN" to gttOnePaisa),
                chargeRates = standardRates,
                profitTargets = mapOf("SBIN" to fivePctTarget),
            )
        assertEquals(1, actionsOne.size)
        assertTrue("Expected NoAction for 1 paisa diff", actionsOne.single() is GttAction.NoAction)

        // Diff = 2 paisa → UpdateGtt
        val gttTwoPaisa = gtt("SBIN", gttId = 2L, triggerPrice = Paisa(appPrice.value + 2L), quantity = 10)
        val actionsTwo =
            GttAutomationEngine.evaluate(
                holdings = listOf(h),
                existingGtts = mapOf("SBIN" to gttTwoPaisa),
                chargeRates = standardRates,
                profitTargets = mapOf("SBIN" to fivePctTarget),
            )
        assertEquals(1, actionsTwo.size)
        assertTrue("Expected UpdateGtt for 2 paisa diff", actionsTwo.single() is GttAction.UpdateGtt)
    }

    // ─── Additional coverage: ARCHIVED GTT is not considered for archiving ───

    @Test
    fun `No action for quantity=0 holding when existing GTT is already ARCHIVED`() {
        val h = holding("MARUTI", quantity = 0, investedAmount = Paisa.ZERO)
        val archivedGtt =
            gtt(
                "MARUTI",
                gttId = 11L,
                triggerPrice = Paisa(300_000),
                quantity = 5,
                status = GttStatus.ARCHIVED,
            )

        val actions =
            GttAutomationEngine.evaluate(
                holdings = listOf(h),
                existingGtts = mapOf("MARUTI" to archivedGtt),
                chargeRates = standardRates,
                profitTargets = emptyMap(),
            )

        assertTrue(actions.isEmpty())
    }
}

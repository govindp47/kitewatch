package com.kitewatch.data.mapper

import com.kitewatch.database.entity.ChargeRateEntity
import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.Paisa
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ChargeRateMapperTest {
    private val fetchedMillis = 1_700_000_000_000L

    /** Standard Zerodha delivery charge rates as milli-bps / paisa. */
    private val standardEntities =
        listOf(
            chargeEntity("BROKERAGE_DELIVERY", 0, "BASIS_POINTS"),
            chargeEntity("STT_BUY", 10_000, "BASIS_POINTS"),
            chargeEntity("STT_SELL", 10_000, "BASIS_POINTS"),
            chargeEntity("EXCHANGE_NSE", 297, "BASIS_POINTS"),
            chargeEntity("EXCHANGE_BSE", 375, "BASIS_POINTS"),
            chargeEntity("GST", 1_800_000, "BASIS_POINTS"),
            chargeEntity("SEBI", 1000, "PAISA_PER_UNIT"),
            chargeEntity("STAMP_DUTY", 1_500, "BASIS_POINTS"),
            chargeEntity("DP_CHARGES_PER_SCRIPT", 1580, "PAISA_FLAT"),
        )

    private val expectedSnapshot =
        ChargeRateSnapshot(
            brokerageDeliveryMilliBps = 0,
            sttBuyMilliBps = 10_000,
            sttSellMilliBps = 10_000,
            exchangeNseMilliBps = 297,
            exchangeBseMilliBps = 375,
            gstMilliBps = 1_800_000,
            sebiChargePerCrorePaisa = Paisa(1000L),
            stampDutyBuyMilliBps = 1_500,
            dpChargesPerScriptPaisa = Paisa(1580L),
            fetchedAt = Instant.ofEpochMilli(fetchedMillis),
        )

    @Test
    fun `entity list maps to correct ChargeRateSnapshot`() {
        assertEquals(expectedSnapshot, standardEntities.toChargeRateSnapshot())
    }

    @Test
    fun `snapshot toEntities produces 9 rows`() {
        val entities = expectedSnapshot.toEntities("2024-01-01")
        assertEquals(9, entities.size)
    }

    @Test
    fun `round-trip snapshot-entities-snapshot preserves all fields`() {
        val roundTripped = expectedSnapshot.toEntities("2024-01-01").toChargeRateSnapshot()
        assertEquals(expectedSnapshot.brokerageDeliveryMilliBps, roundTripped.brokerageDeliveryMilliBps)
        assertEquals(expectedSnapshot.sttBuyMilliBps, roundTripped.sttBuyMilliBps)
        assertEquals(expectedSnapshot.sttSellMilliBps, roundTripped.sttSellMilliBps)
        assertEquals(expectedSnapshot.exchangeNseMilliBps, roundTripped.exchangeNseMilliBps)
        assertEquals(expectedSnapshot.exchangeBseMilliBps, roundTripped.exchangeBseMilliBps)
        assertEquals(expectedSnapshot.gstMilliBps, roundTripped.gstMilliBps)
        assertEquals(expectedSnapshot.sebiChargePerCrorePaisa, roundTripped.sebiChargePerCrorePaisa)
        assertEquals(expectedSnapshot.stampDutyBuyMilliBps, roundTripped.stampDutyBuyMilliBps)
        assertEquals(expectedSnapshot.dpChargesPerScriptPaisa, roundTripped.dpChargesPerScriptPaisa)
        assertEquals(expectedSnapshot.fetchedAt, roundTripped.fetchedAt)
    }

    @Test
    fun `toEntities sets effectiveFrom on all rows`() {
        val entities = expectedSnapshot.toEntities("2024-03-01")
        entities.forEach { assertEquals("2024-03-01", it.effectiveFrom) }
    }

    @Test
    fun `toEntities sets isCurrent=1 on all rows`() {
        val entities = expectedSnapshot.toEntities("2024-01-01")
        entities.forEach { assertEquals(1, it.isCurrent) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing charge type throws IllegalArgumentException`() {
        standardEntities.drop(1).toChargeRateSnapshot()
    }

    @Test
    fun `fetchedAt is max of all entity fetchedAt values`() {
        val entities =
            standardEntities.mapIndexed { index, entity ->
                entity.copy(fetchedAt = fetchedMillis + index * 1000)
            }
        val snapshot = entities.toChargeRateSnapshot()
        assertEquals(Instant.ofEpochMilli(fetchedMillis + (entities.size - 1) * 1000), snapshot.fetchedAt)
    }

    private fun chargeEntity(
        type: String,
        value: Int,
        unit: String,
    ) = ChargeRateEntity(
        rateType = type,
        rateValue = value,
        rateUnit = unit,
        effectiveFrom = "2024-01-01",
        fetchedAt = fetchedMillis,
        isCurrent = 1,
    )
}

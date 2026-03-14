package com.kitewatch.data.mapper

import com.kitewatch.database.entity.HoldingEntity
import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HoldingMapperTest {
    private val createdMillis = 1_700_000_000_000L
    private val updatedMillis = 1_700_100_000_000L

    private val percentageEntity =
        HoldingEntity(
            id = 1L,
            stockCode = "INFY",
            stockName = "Infosys Ltd",
            quantity = 10,
            avgBuyPricePaisa = 150000L,
            investedAmountPaisa = 1500000L,
            totalBuyChargesPaisa = 3000L,
            profitTargetType = "PERCENTAGE",
            profitTargetValue = 500,
            targetSellPricePaisa = 157500L,
            createdAt = createdMillis,
            updatedAt = updatedMillis,
        )

    private val absoluteEntity =
        percentageEntity.copy(
            profitTargetType = "ABSOLUTE",
            profitTargetValue = 50000,
        )

    @Test
    fun `PERCENTAGE profit target maps to ProfitTarget_Percentage`() {
        val domain = percentageEntity.toDomain()
        val target = domain.profitTarget
        assertTrue(target is ProfitTarget.Percentage)
        assertEquals(500, (target as ProfitTarget.Percentage).basisPoints)
    }

    @Test
    fun `ABSOLUTE profit target maps to ProfitTarget_Absolute`() {
        val domain = absoluteEntity.toDomain()
        val target = domain.profitTarget
        assertTrue(target is ProfitTarget.Absolute)
        assertEquals(Paisa(50000L), (target as ProfitTarget.Absolute).amount)
    }

    @Test
    fun `entity toDomain maps timestamps to Instant`() {
        val domain = percentageEntity.toDomain()
        assertEquals(Instant.ofEpochMilli(createdMillis), domain.createdAt)
        assertEquals(Instant.ofEpochMilli(updatedMillis), domain.updatedAt)
    }

    @Test
    fun `round-trip PERCENTAGE preserves all fields`() {
        val entity = percentageEntity
        val roundTripped = entity.toDomain().toEntity()
        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.stockCode, roundTripped.stockCode)
        assertEquals(entity.quantity, roundTripped.quantity)
        assertEquals(entity.avgBuyPricePaisa, roundTripped.avgBuyPricePaisa)
        assertEquals(entity.investedAmountPaisa, roundTripped.investedAmountPaisa)
        assertEquals(entity.totalBuyChargesPaisa, roundTripped.totalBuyChargesPaisa)
        assertEquals(entity.profitTargetType, roundTripped.profitTargetType)
        assertEquals(entity.profitTargetValue, roundTripped.profitTargetValue)
        assertEquals(entity.targetSellPricePaisa, roundTripped.targetSellPricePaisa)
        assertEquals(entity.createdAt, roundTripped.createdAt)
        assertEquals(entity.updatedAt, roundTripped.updatedAt)
    }

    @Test
    fun `round-trip ABSOLUTE preserves profitTargetValue`() {
        val roundTripped = absoluteEntity.toDomain().toEntity()
        assertEquals("ABSOLUTE", roundTripped.profitTargetType)
        assertEquals(50000, roundTripped.profitTargetValue)
    }

    @Test
    fun `domain toEntity encodes PERCENTAGE correctly`() {
        val domain =
            Holding(
                holdingId = 2L,
                stockCode = "TCS",
                stockName = "TCS Ltd",
                quantity = 5,
                avgBuyPrice = Paisa(300000L),
                investedAmount = Paisa(1500000L),
                totalBuyCharges = Paisa(2500L),
                profitTarget = ProfitTarget.Percentage(basisPoints = 750),
                targetSellPrice = Paisa(311250L),
                createdAt = Instant.ofEpochMilli(createdMillis),
                updatedAt = Instant.ofEpochMilli(updatedMillis),
            )
        val entity = domain.toEntity()
        assertEquals("PERCENTAGE", entity.profitTargetType)
        assertEquals(750, entity.profitTargetValue)
    }
}

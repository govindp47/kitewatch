package com.kitewatch.data.mapper

import com.kitewatch.database.entity.HoldingEntity
import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import java.time.Instant

fun HoldingEntity.toDomain(): Holding =
    Holding(
        holdingId = id,
        stockCode = stockCode,
        stockName = stockName,
        quantity = quantity,
        avgBuyPrice = Paisa(avgBuyPricePaisa),
        investedAmount = Paisa(investedAmountPaisa),
        totalBuyCharges = Paisa(totalBuyChargesPaisa),
        profitTarget = decodeProfitTarget(profitTargetType, profitTargetValue),
        targetSellPrice = Paisa(targetSellPricePaisa),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

fun Holding.toEntity(): HoldingEntity =
    HoldingEntity(
        id = holdingId,
        stockCode = stockCode,
        stockName = stockName,
        quantity = quantity,
        avgBuyPricePaisa = avgBuyPrice.value,
        investedAmountPaisa = investedAmount.value,
        totalBuyChargesPaisa = totalBuyCharges.value,
        profitTargetType = encodeProfitTargetType(profitTarget),
        profitTargetValue = encodeProfitTargetValue(profitTarget),
        targetSellPricePaisa = targetSellPrice.value,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )

private fun decodeProfitTarget(
    type: String,
    value: Int,
): ProfitTarget =
    when (type) {
        "PERCENTAGE" -> ProfitTarget.Percentage(basisPoints = value)
        "ABSOLUTE" -> ProfitTarget.Absolute(amount = Paisa(value.toLong()))
        else -> error("Unknown profit_target_type: $type")
    }

private fun encodeProfitTargetType(target: ProfitTarget): String =
    when (target) {
        is ProfitTarget.Percentage -> "PERCENTAGE"
        is ProfitTarget.Absolute -> "ABSOLUTE"
    }

private fun encodeProfitTargetValue(target: ProfitTarget): Int =
    when (target) {
        is ProfitTarget.Percentage -> target.basisPoints
        is ProfitTarget.Absolute -> target.amount.value.toInt()
    }

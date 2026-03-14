package com.kitewatch.feature.holdings.mapper

import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.domain.model.Holding
import com.kitewatch.feature.holdings.model.GttStatusUiModel
import com.kitewatch.feature.holdings.model.HoldingUiModel
import com.kitewatch.ui.formatter.CurrencyFormatter

internal fun Holding.toUiModel(
    gttRecord: GttRecord? = null,
    isExpanded: Boolean = false,
): HoldingUiModel =
    HoldingUiModel(
        stockCode = stockCode,
        stockName = stockName,
        quantity = quantity.toString(),
        avgBuyPrice = CurrencyFormatter.format(avgBuyPrice),
        targetSellPrice = CurrencyFormatter.format(targetSellPrice),
        profitTargetDisplay = profitTarget.displayValue,
        investedAmount = CurrencyFormatter.format(investedAmount),
        totalBuyCharges = CurrencyFormatter.format(totalBuyCharges),
        estimatedCurrentValue = null,
        isExpanded = isExpanded,
        linkedGttStatus = gttRecord?.toUiModel(),
    )

private fun GttRecord.toUiModel(): GttStatusUiModel =
    GttStatusUiModel(
        statusLabel = status.toLabel(),
        triggerPrice = CurrencyFormatter.format(triggerPrice),
        isManualOverride = status == GttStatus.MANUAL_OVERRIDE_DETECTED,
    )

private fun GttStatus.toLabel(): String =
    when (this) {
        GttStatus.PENDING_CREATION -> "Pending creation"
        GttStatus.ACTIVE -> "Active"
        GttStatus.TRIGGERED -> "Triggered"
        GttStatus.MANUAL_OVERRIDE_DETECTED -> "Manual override"
        GttStatus.PENDING_UPDATE -> "Pending update"
        GttStatus.ARCHIVED -> "Archived"
    }

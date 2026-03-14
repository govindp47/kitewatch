package com.kitewatch.feature.holdings.model

data class HoldingUiModel(
    val stockCode: String,
    val stockName: String,
    val quantity: String,
    val avgBuyPrice: String,
    val targetSellPrice: String,
    val profitTargetDisplay: String,
    val investedAmount: String,
    val totalBuyCharges: String,
    /** Always null — no live price feed in this app. */
    val estimatedCurrentValue: String? = null,
    val isExpanded: Boolean = false,
    val linkedGttStatus: GttStatusUiModel? = null,
)

data class GttStatusUiModel(
    val statusLabel: String,
    val triggerPrice: String,
    val isManualOverride: Boolean,
)

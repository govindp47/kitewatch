package com.kitewatch.feature.gtt.model

data class GttUiModel(
    val gttId: Long,
    val stockCode: String,
    val triggerPrice: String,
    val quantity: String,
    val statusLabel: String,
    val isManualOverride: Boolean,
    val lastSynced: String,
)

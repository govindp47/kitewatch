package com.kitewatch.feature.portfolio.model

import java.time.LocalDate

data class PnlUiModel(
    val realizedPnl: String,
    val totalCharges: String,
    val pnlPercentage: String,
    val totalInvestedValue: String,
    val chargeBreakdownFormatted: ChargeBreakdownUiModel,
    val isProfit: Boolean,
)

data class ChargeBreakdownUiModel(
    val stt: String,
    val exchangeTxn: String,
    val sebiCharges: String,
    val stampDuty: String,
    val gst: String,
    val total: String,
)

data class ChartDataPoint(
    val date: LocalDate,
    val cumulativePnl: String,
    val rawPaisaValue: Long,
)

data class AlertUiModel(
    val alertId: Long,
    val message: String,
    val severity: AlertSeverityUi,
)

enum class AlertSeverityUi { CRITICAL, WARNING, INFO }

data class SyncStatusUiModel(
    val lastSyncTime: String?,
    val lastSyncFailed: Boolean,
)

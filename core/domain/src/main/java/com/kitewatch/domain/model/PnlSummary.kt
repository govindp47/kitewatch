package com.kitewatch.domain.model

import java.time.LocalDate

data class PnlSummary(
    val realizedPnl: Paisa,
    val totalSellValue: Paisa,
    val totalBuyCostOfSoldLots: Paisa,
    val totalCharges: Paisa,
    val chargeBreakdown: ChargeBreakdown,
    val dateRange: ClosedRange<LocalDate>,
)

package com.kitewatch.feature.portfolio.mapper

import com.kitewatch.domain.model.PnlSummary
import com.kitewatch.domain.repository.AlertSeverity
import com.kitewatch.domain.repository.PersistentAlert
import com.kitewatch.feature.portfolio.model.AlertSeverityUi
import com.kitewatch.feature.portfolio.model.AlertUiModel
import com.kitewatch.feature.portfolio.model.ChargeBreakdownUiModel
import com.kitewatch.feature.portfolio.model.PnlUiModel
import com.kitewatch.ui.formatter.CurrencyFormatter
import com.kitewatch.ui.formatter.PercentageFormatter

internal fun PnlSummary.toUiModel(): PnlUiModel {
    val isProfit = realizedPnl.isPositive() || realizedPnl.isZero()
    // P&L percentage: (realizedPnl / totalBuyCostOfSoldLots) expressed in basis points
    val pnlBps =
        if (totalBuyCostOfSoldLots.isZero()) {
            0
        } else {
            ((realizedPnl.value * 10_000L) / totalBuyCostOfSoldLots.value).toInt()
        }

    return PnlUiModel(
        realizedPnl = CurrencyFormatter.format(realizedPnl),
        totalCharges = CurrencyFormatter.format(totalCharges),
        pnlPercentage = PercentageFormatter.formatWithSign(pnlBps),
        totalInvestedValue = CurrencyFormatter.format(totalBuyCostOfSoldLots),
        chargeBreakdownFormatted =
            ChargeBreakdownUiModel(
                stt = CurrencyFormatter.format(chargeBreakdown.stt),
                exchangeTxn = CurrencyFormatter.format(chargeBreakdown.exchangeTxn),
                sebiCharges = CurrencyFormatter.format(chargeBreakdown.sebiCharges),
                stampDuty = CurrencyFormatter.format(chargeBreakdown.stampDuty),
                gst = CurrencyFormatter.format(chargeBreakdown.gst),
                total = CurrencyFormatter.format(chargeBreakdown.total()),
            ),
        isProfit = isProfit,
    )
}

internal fun PersistentAlert.toUiModel(): AlertUiModel =
    AlertUiModel(
        alertId = id,
        message = payload,
        severity =
            when (severity) {
                AlertSeverity.CRITICAL -> AlertSeverityUi.CRITICAL
                AlertSeverity.WARNING -> AlertSeverityUi.WARNING
                AlertSeverity.INFO -> AlertSeverityUi.INFO
            },
    )

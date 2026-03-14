package com.kitewatch.feature.portfolio.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitewatch.feature.portfolio.model.ChargeBreakdownUiModel
import com.kitewatch.feature.portfolio.model.PnlUiModel

@Composable
internal fun PnlSummaryCard(
    pnlUiModel: PnlUiModel,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Realized P&L",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pnlUiModel.realizedPnl,
                style = MaterialTheme.typography.headlineMedium,
                color =
                    if (pnlUiModel.isProfit) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
            Text(
                text = pnlUiModel.pnlPercentage,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (pnlUiModel.isProfit) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                LabeledValue(label = "Invested", value = pnlUiModel.totalInvestedValue)
                LabeledValue(label = "Charges", value = pnlUiModel.totalCharges)
            }
            Spacer(modifier = Modifier.height(12.dp))
            ChargeBreakdownSection(breakdown = pnlUiModel.chargeBreakdownFormatted)
        }
    }
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ChargeBreakdownSection(
    breakdown: ChargeBreakdownUiModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Charge Breakdown",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        listOf(
            "STT" to breakdown.stt,
            "Exchange Txn" to breakdown.exchangeTxn,
            "SEBI Charges" to breakdown.sebiCharges,
            "Stamp Duty" to breakdown.stampDuty,
            "GST" to breakdown.gst,
        ).forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

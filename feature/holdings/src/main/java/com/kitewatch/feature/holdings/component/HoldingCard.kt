package com.kitewatch.feature.holdings.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kitewatch.feature.holdings.model.GttStatusUiModel
import com.kitewatch.feature.holdings.model.HoldingUiModel

private val ManualOverrideAmber = Color(0xFFFBBC04)

@Composable
internal fun HoldingCard(
    holding: HoldingUiModel,
    onToggleExpand: () -> Unit,
    onEditTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Collapsed header (always visible) ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = holding.stockCode,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = holding.stockName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Qty: ${holding.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Avg: ${holding.avgBuyPrice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Target: ${holding.targetSellPrice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                holding.linkedGttStatus?.let { gtt ->
                    GttStatusBadge(gtt)
                }
            }

            // ── Expanded detail ────────────────────────────────────────────────
            if (holding.isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                DetailRow(label = "Invested", value = holding.investedAmount)
                DetailRow(label = "Buy charges", value = holding.totalBuyCharges)
                DetailRow(label = "Profit target", value = holding.profitTargetDisplay)

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Edit target",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = onEditTarget) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit profit target",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun GttStatusBadge(gtt: GttStatusUiModel) {
    val tint =
        when {
            gtt.isManualOverride -> ManualOverrideAmber
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        }
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (gtt.isManualOverride) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = ManualOverrideAmber,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = gtt.statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}

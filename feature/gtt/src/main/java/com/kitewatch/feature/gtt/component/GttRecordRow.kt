package com.kitewatch.feature.gtt.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kitewatch.feature.gtt.model.GttUiModel

private val AmberColor = Color(0xFFFBBC04)
private val ActiveGreen = Color(0xFF34A853)

@Composable
internal fun GttRecordRow(
    record: GttUiModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            // Left: stock + status
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.stockCode,
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    StatusDot(isOverride = record.isManualOverride)
                    Text(
                        text = record.statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (record.isManualOverride) {
                                AmberColor
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            },
                    )
                }
                if (record.isManualOverride) {
                    Text(
                        text = "Modified outside KiteWatch — review required",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberColor,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = "Last synced: ${record.lastSynced}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Right: trigger price + qty
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text(
                    text = record.triggerPrice,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Qty: ${record.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp)
    }
}

@Composable
private fun StatusDot(
    isOverride: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(8.dp)
                .background(
                    color = if (isOverride) AmberColor else ActiveGreen,
                    shape = CircleShape,
                ),
    )
}

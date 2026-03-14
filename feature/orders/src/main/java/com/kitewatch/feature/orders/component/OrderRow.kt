package com.kitewatch.feature.orders.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kitewatch.feature.orders.model.OrderUiModel

private val BuyGreen = Color(0xFF34A853)
private val SellRed = Color(0xFFEA4335)

@Composable
internal fun OrderRow(
    order: OrderUiModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: date + stock
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    text = order.stockCode,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = order.stockName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            // Centre: type badge
            TypeBadge(
                label = order.type,
                isBuy = order.isBuy,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // Right: qty × price, value
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${order.quantity} × ${order.price}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = order.totalValue,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Charges: ${order.charges}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp)
    }
}

@Composable
private fun TypeBadge(
    label: String,
    isBuy: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (isBuy) BuyGreen else SellRed
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

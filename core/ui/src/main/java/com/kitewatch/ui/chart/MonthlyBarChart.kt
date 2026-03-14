package com.kitewatch.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.kitewatch.domain.model.Paisa

/**
 * Vertical bar chart showing monthly P&L.
 *
 * @param dataPoints list of (month label, P&L value) pairs.
 * Positive bars are drawn in green; negative bars in red.
 */
@Composable
fun MonthlyBarChart(
    dataPoints: List<Pair<String, Paisa>>,
    modifier: Modifier = Modifier,
) {
    if (dataPoints.isEmpty()) return

    val positiveColor = MaterialTheme.colorScheme.secondary
    val negativeColor = MaterialTheme.colorScheme.tertiary
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp),
    ) {
        if (size.width == 0f || size.height == 0f) return@Canvas

        val values = dataPoints.map { it.second.value.toFloat() }
        val maxVal = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val minVal = values.minOrNull()?.coerceAtMost(-1f) ?: -1f
        val range = maxVal - minVal

        val barCount = dataPoints.size
        val totalGap = size.width * 0.3f
        val gapWidth = if (barCount > 1) totalGap / (barCount + 1) else 8.dp.toPx()
        val barWidth = (size.width - totalGap) / barCount

        val zeroY = size.height - ((0f - minVal) / range * size.height)

        // Baseline
        drawLine(
            color = baselineColor,
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1.dp.toPx(),
        )

        dataPoints.forEachIndexed { i, (_, paisa) ->
            val value = paisa.value.toFloat()
            val barLeft = gapWidth + i * (barWidth + gapWidth)
            val barY = size.height - ((value - minVal) / range * size.height)

            val top = minOf(barY, zeroY)
            val bottom = maxOf(barY, zeroY)
            val barHeight = (bottom - top).coerceAtLeast(1f)

            drawRect(
                color = if (value >= 0f) positiveColor else negativeColor,
                topLeft = Offset(barLeft, top),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

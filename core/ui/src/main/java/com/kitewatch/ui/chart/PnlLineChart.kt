package com.kitewatch.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kitewatch.domain.model.Paisa
import java.time.LocalDate

/**
 * Cumulative P&L line chart.
 *
 * Draws a line connecting [dataPoints] ordered by date.
 * Area above the zero line is filled with a green tint; area below with a red tint.
 * A dashed zero baseline is drawn horizontally.
 */
@Composable
fun PnlLineChart(
    dataPoints: List<Pair<LocalDate, Paisa>>,
    modifier: Modifier = Modifier,
) {
    if (dataPoints.isEmpty()) return

    val lineColor = MaterialTheme.colorScheme.primary
    val positiveColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
    val negativeColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
    val baselineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp),
    ) {
        if (size.width == 0f || size.height == 0f) return@Canvas

        val values = dataPoints.map { it.second.value.toFloat() }
        val maxVal = values.max().coerceAtLeast(1f)
        val minVal = values.min().coerceAtMost(-1f)
        val range = maxVal - minVal

        fun xAt(index: Int): Float =
            if (dataPoints.size == 1) {
                size.width / 2f
            } else {
                index.toFloat() / (dataPoints.size - 1) * size.width
            }

        fun yAt(value: Float): Float = size.height - ((value - minVal) / range * size.height)

        val zeroY = yAt(0f).coerceIn(0f, size.height)

        // --- Draw zero baseline ---
        drawLine(
            color = baselineColor,
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1.dp.toPx(),
            pathEffect =
                androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(8.dp.toPx(), 4.dp.toPx()),
                    0f,
                ),
        )

        // --- Build line path ---
        val linePath =
            Path().apply {
                values.forEachIndexed { i, v ->
                    val x = xAt(i)
                    val y = yAt(v)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }

        // --- Positive fill (above zero) ---
        val positiveFill =
            Path().apply {
                values.forEachIndexed { i, v ->
                    val x = xAt(i)
                    val y = yAt(v).coerceAtMost(zeroY)
                    if (i == 0) moveTo(x, zeroY) else Unit
                    lineTo(x, y)
                }
                lineTo(xAt(values.lastIndex), zeroY)
                close()
            }

        drawPath(positiveFill, color = positiveColor)

        // --- Negative fill (below zero) ---
        val negativeFill =
            Path().apply {
                values.forEachIndexed { i, v ->
                    val x = xAt(i)
                    val y = yAt(v).coerceAtLeast(zeroY)
                    if (i == 0) moveTo(x, zeroY) else Unit
                    lineTo(x, y)
                }
                lineTo(xAt(values.lastIndex), zeroY)
                close()
            }

        drawPath(negativeFill, color = negativeColor)

        // --- Draw line ---
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

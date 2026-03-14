package com.kitewatch.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitewatch.domain.model.Paisa
import com.kitewatch.ui.formatter.CurrencyFormatter
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pie chart with two segments: realized P&L and total charges.
 *
 * Tapping a segment highlights it (increases its outer radius slightly).
 * The centre label shows the total value using [CurrencyFormatter].
 */
@Composable
fun PnlPieChart(
    realizedPnl: Paisa,
    totalCharges: Paisa,
    modifier: Modifier = Modifier,
) {
    val pnlColor = MaterialTheme.colorScheme.secondary
    val chargesColor = MaterialTheme.colorScheme.tertiary
    val selectedStrokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val labelColor = MaterialTheme.colorScheme.onSurface

    val textMeasurer = rememberTextMeasurer()
    var selectedSegment by remember { mutableIntStateOf(-1) }

    val total =
        (realizedPnl.value + totalCharges.value).toFloat().let {
            if (it == 0f) 1f else it
        }
    val pnlFraction = realizedPnl.value.toFloat() / total
    val chargesFraction = totalCharges.value.toFloat() / total

    // Each segment: (startAngle, sweepAngle, color)
    val segments =
        listOf(
            Triple(-90f, pnlFraction * 360f, pnlColor),
            Triple(-90f + pnlFraction * 360f, chargesFraction * 360f, chargesColor),
        )

    val totalLabel = CurrencyFormatter.format(Paisa(realizedPnl.value + totalCharges.value))

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = tapOffset.x - cx
                        val dy = tapOffset.y - cy
                        val dist = sqrt(dx * dx + dy * dy)
                        val radius = minOf(size.width, size.height) / 2f * 0.85f
                        if (dist <= radius) {
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                            if (angle < 0) angle += 360f
                            var cumulative = 0f
                            var hit = -1
                            segments.forEachIndexed { i, (_, sweep, _) ->
                                val end = cumulative + sweep
                                if (angle in cumulative..end) hit = i
                                cumulative = end
                            }
                            selectedSegment = if (selectedSegment == hit) -1 else hit
                        }
                    }
                },
    ) {
        if (size.width == 0f || size.height == 0f) return@Canvas

        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseRadius = minOf(cx, cy) * 0.85f
        val selectedExtra = 8.dp.toPx()
        val strokeWidth = 2.dp.toPx()

        segments.forEachIndexed { i, (startAngle, sweepAngle, color) ->
            val r = if (selectedSegment == i) baseRadius + selectedExtra else baseRadius
            val topLeft = Offset(cx - r, cy - r)
            val arcSize = Size(r * 2, r * 2)

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = topLeft,
                size = arcSize,
            )

            if (selectedSegment == i) {
                drawArc(
                    color = selectedStrokeColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
            }
        }

        // Centre label
        val measuredText =
            textMeasurer.measure(
                text = totalLabel,
                style = TextStyle(color = labelColor, fontSize = 12.sp),
            )
        drawText(
            textLayoutResult = measuredText,
            topLeft =
                Offset(
                    cx - measuredText.size.width / 2f,
                    cy - measuredText.size.height / 2f,
                ),
        )
    }
}

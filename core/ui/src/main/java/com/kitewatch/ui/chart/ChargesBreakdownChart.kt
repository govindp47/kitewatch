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
 * Stacked horizontal bar chart showing charge type proportions.
 *
 * @param segments list of (label, value) pairs representing each charge type.
 * Each segment is a distinct colour slice in the horizontal bar.
 */
@Composable
fun ChargesBreakdownChart(
    segments: List<Pair<String, Paisa>>,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return

    val segmentColors =
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
        )

    val total = segments.sumOf { it.second.value }.toFloat().coerceAtLeast(1f)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp),
    ) {
        if (size.width == 0f || size.height == 0f) return@Canvas

        val barHeight = (size.height * 0.5f).coerceAtLeast(8.dp.toPx())
        val barTop = (size.height - barHeight) / 2f

        var xOffset = 0f
        segments.forEachIndexed { i, (_, paisa) ->
            val fraction = paisa.value.toFloat() / total
            val segWidth = size.width * fraction
            drawRect(
                color = segmentColors[i % segmentColors.size],
                topLeft = Offset(xOffset, barTop),
                size = Size(segWidth, barHeight),
            )
            xOffset += segWidth
        }
    }
}

package com.kitewatch.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.kitewatch.ui.formatter.PercentageFormatter

@Composable
fun PercentageText(
    basisPoints: Int,
    modifier: Modifier = Modifier,
    showSign: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val color =
        when {
            basisPoints < 0 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }
    val text =
        if (showSign) {
            PercentageFormatter.formatWithSign(basisPoints)
        } else {
            PercentageFormatter.format(basisPoints)
        }
    Text(
        text = text,
        style = textStyle,
        color = color,
        modifier = modifier,
    )
}

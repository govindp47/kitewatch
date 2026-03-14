package com.kitewatch.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.kitewatch.domain.model.Paisa
import com.kitewatch.ui.formatter.CurrencyFormatter

@Composable
fun CurrencyText(
    paisa: Paisa,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val color =
        if (paisa.isNegative()) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Text(
        text = CurrencyFormatter.format(paisa),
        style = textStyle,
        color = color,
        modifier = modifier,
    )
}

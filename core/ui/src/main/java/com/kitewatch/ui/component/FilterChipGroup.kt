package com.kitewatch.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> FilterChipGroup(
    options: List<T>,
    selectedOption: T,
    onSelect: (T) -> Unit,
    labelFor: (T) -> String,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(options) { option ->
            FilterChip(
                selected = option == selectedOption,
                onClick = { onSelect(option) },
                label = { Text(text = labelFor(option)) },
            )
        }
    }
}

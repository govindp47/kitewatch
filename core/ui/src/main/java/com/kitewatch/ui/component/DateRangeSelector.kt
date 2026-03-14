package com.kitewatch.ui.component

import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeSelector(
    selectedRange: DateRangePreset,
    onRangeSelected: (DateRangePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    FilterChipGroup(
        options = DateRangePreset.entries,
        selectedOption = selectedRange,
        onSelect = { preset ->
            if (preset == DateRangePreset.CUSTOM) {
                showDatePicker = true
            } else {
                onRangeSelected(preset)
            }
        },
        labelFor = { preset ->
            when (preset) {
                DateRangePreset.TODAY -> "Today"
                DateRangePreset.THIS_WEEK -> "This Week"
                DateRangePreset.THIS_MONTH -> "This Month"
                DateRangePreset.THIS_YEAR -> "This Year"
                DateRangePreset.ALL_TIME -> "All Time"
                DateRangePreset.CUSTOM -> "Custom"
            }
        },
        modifier = modifier,
    )

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        onRangeSelected(DateRangePreset.CUSTOM)
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DateRangePicker(state = dateRangePickerState)
        }
    }
}

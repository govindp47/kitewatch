package com.kitewatch.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ConfirmationDialog(
    state: ConfirmationDialogState,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = state.onCancel,
        title = { Text(text = state.title) },
        text = { Text(text = state.message) },
        confirmButton = {
            TextButton(onClick = state.onConfirm) {
                Text(text = state.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = state.onCancel) {
                Text(text = state.cancelLabel)
            }
        },
    )
}

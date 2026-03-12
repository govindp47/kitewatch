package com.kitewatch.ui.component

data class ConfirmationDialogState(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val cancelLabel: String,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
)

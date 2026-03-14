package com.kitewatch.ui.component

data class ChecklistItem(
    val label: String,
    val isComplete: Boolean,
    val actionLabel: String = "Set up",
    val onAction: () -> Unit,
)

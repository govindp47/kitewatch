package com.kitewatch.feature.orders

import com.kitewatch.ui.component.DateRangePreset

sealed interface OrdersIntent {
    data object SyncNow : OrdersIntent

    data class SelectDateRange(
        val preset: DateRangePreset,
    ) : OrdersIntent
}

data class OrdersState(
    val isSyncing: Boolean = false,
    val selectedRange: DateRangePreset = DateRangePreset.ALL_TIME,
    val error: String? = null,
)

sealed interface OrdersSideEffect {
    data class ShowSnackbar(
        val message: String,
    ) : OrdersSideEffect
}

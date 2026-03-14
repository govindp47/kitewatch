package com.kitewatch.ui.component

import androidx.compose.ui.Modifier

data class PaginatedLazyColumnOptions<T>(
    val modifier: Modifier = Modifier,
    val key: ((T) -> Any)? = null,
)

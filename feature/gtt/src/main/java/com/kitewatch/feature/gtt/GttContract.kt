package com.kitewatch.feature.gtt

import com.kitewatch.feature.gtt.model.GttUiModel

sealed interface GttIntent

data class GttState(
    val isLoading: Boolean = true,
    val records: List<GttUiModel> = emptyList(),
    val unacknowledgedOverrides: Int = 0,
)

package com.kitewatch.feature.holdings

import com.kitewatch.domain.model.ProfitTarget
import com.kitewatch.feature.holdings.model.HoldingUiModel

sealed interface HoldingsIntent {
    data class ToggleExpand(
        val stockCode: String,
    ) : HoldingsIntent

    data class EditProfitTarget(
        val stockCode: String,
    ) : HoldingsIntent

    data class SaveProfitTarget(
        val stockCode: String,
        val newTarget: ProfitTarget,
    ) : HoldingsIntent

    data class DismissEditSheet(
        val stockCode: String,
    ) : HoldingsIntent

    data object NavigateToGtt : HoldingsIntent
}

data class HoldingsState(
    val isLoading: Boolean = true,
    val holdings: List<HoldingUiModel> = emptyList(),
    val editingStockCode: String? = null,
    val error: String? = null,
)

sealed interface HoldingsSideEffect {
    data object NavigateToGtt : HoldingsSideEffect

    data class ShowSnackbar(
        val message: String,
    ) : HoldingsSideEffect
}

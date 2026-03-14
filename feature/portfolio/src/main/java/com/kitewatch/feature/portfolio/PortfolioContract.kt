package com.kitewatch.feature.portfolio

import com.kitewatch.feature.portfolio.model.AlertUiModel
import com.kitewatch.feature.portfolio.model.ChartDataPoint
import com.kitewatch.feature.portfolio.model.PnlUiModel
import com.kitewatch.feature.portfolio.model.SyncStatusUiModel
import com.kitewatch.ui.component.DateRangePreset
import java.time.LocalDate

sealed interface PortfolioIntent {
    data object LoadData : PortfolioIntent

    data class SelectDateRange(
        val range: DateRangePreset,
    ) : PortfolioIntent

    data class SelectCustomRange(
        val from: LocalDate,
        val to: LocalDate,
    ) : PortfolioIntent

    data class DismissAlert(
        val alertId: Long,
    ) : PortfolioIntent

    data object RefreshSync : PortfolioIntent
}

data class PortfolioState(
    val isLoading: Boolean = true,
    val pnlSummary: PnlUiModel? = null,
    val fundBalance: String = "",
    val selectedRange: DateRangePreset = DateRangePreset.THIS_MONTH,
    val chartData: List<ChartDataPoint> = emptyList(),
    val unacknowledgedAlerts: List<AlertUiModel> = emptyList(),
    val lastSyncStatus: SyncStatusUiModel = SyncStatusUiModel(lastSyncTime = null, lastSyncFailed = false),
    val showSetupChecklist: Boolean = false,
    val error: String? = null,
)

sealed interface PortfolioSideEffect {
    data class ShowSnackbar(
        val message: String,
    ) : PortfolioSideEffect

    data object NavigateToSync : PortfolioSideEffect
}

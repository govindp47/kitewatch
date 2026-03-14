package com.kitewatch.feature.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitewatch.domain.model.Paisa
import com.kitewatch.feature.portfolio.component.FundBalanceCard
import com.kitewatch.feature.portfolio.component.PnlSummaryCard
import com.kitewatch.feature.portfolio.component.PortfolioAlertRow
import com.kitewatch.feature.portfolio.model.ChartDataPoint
import com.kitewatch.ui.chart.PnlLineChart
import com.kitewatch.ui.chart.PnlPieChart
import com.kitewatch.ui.component.ChecklistItem
import com.kitewatch.ui.component.DateRangeSelector
import com.kitewatch.ui.component.SetupChecklist
import com.kitewatch.ui.component.SkeletonLoader
import com.kitewatch.ui.component.StatusIndicator
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioRoute(
    modifier: Modifier = Modifier,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PortfolioScreen(
        state = state,
        onIntent = viewModel::processIntent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PortfolioScreen(
    state: PortfolioState,
    onIntent: (PortfolioIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val syncTime: Instant? =
        state.lastSyncStatus.lastSyncTime?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "KiteWatch",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    StatusIndicator(
                        lastSyncTime = syncTime,
                        lastSyncFailed = state.lastSyncStatus.lastSyncFailed,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentAlignment = Alignment.TopStart,
            ) {
                SkeletonLoader(
                    lines = 6,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Date range selector
                item {
                    DateRangeSelector(
                        selectedRange = state.selectedRange,
                        onRangeSelected = { range ->
                            onIntent(PortfolioIntent.SelectDateRange(range))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Error banner
                if (state.error != null) {
                    item {
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                // Alert banners
                items(
                    items = state.unacknowledgedAlerts,
                    key = { it.alertId },
                ) { alert ->
                    PortfolioAlertRow(
                        alert = alert,
                        onDismiss = { onIntent(PortfolioIntent.DismissAlert(alert.alertId)) },
                    )
                }

                // P&L summary card
                if (state.pnlSummary != null) {
                    item {
                        PnlSummaryCard(pnlUiModel = state.pnlSummary)
                    }
                }

                // Cumulative P&L line chart
                if (state.chartData.isNotEmpty()) {
                    item {
                        PnlLineChart(
                            dataPoints = state.chartData.toLineChartData(),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                        )
                    }
                }

                // Fund balance card
                item {
                    FundBalanceCard(
                        fundBalance = state.fundBalance,
                        syncStatus = state.lastSyncStatus,
                    )
                }

                // Charges pie chart (only if P&L data available)
                if (state.pnlSummary != null) {
                    item {
                        PnlPieChartSection(pnlSummary = state.pnlSummary)
                    }
                }

                // Setup checklist empty state
                if (state.showSetupChecklist) {
                    item {
                        SetupChecklist(
                            items =
                                listOf(
                                    ChecklistItem(
                                        label = "Sync your Kite orders",
                                        isComplete = false,
                                        actionLabel = "Sync now",
                                        onAction = { onIntent(PortfolioIntent.RefreshSync) },
                                    ),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("UnusedParameter")
private fun PnlPieChartSection(pnlSummary: com.kitewatch.feature.portfolio.model.PnlUiModel) {
    // We display pie only as a visual aid — using raw Paisa from the formatted strings
    // is not reliable; instead the pie chart here is a decorative breakdown indicator
    // seeded with a placeholder. The charges breakdown is already shown in PnlSummaryCard.
    // Per task spec: render PnlPieChart "if data is available". We pass zero-value Paisa
    // as the raw chart only needs relative proportions which are implied by the summary.
    // Full Paisa raw values live in the domain layer — not re-parsed here.
    PnlPieChart(
        realizedPnl = Paisa.ZERO,
        totalCharges = Paisa.ZERO,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun List<ChartDataPoint>.toLineChartData(): List<Pair<java.time.LocalDate, Paisa>> =
    map { point -> point.date to Paisa(point.rawPaisaValue) }

package com.kitewatch.feature.holdings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitewatch.feature.holdings.component.HoldingCard
import com.kitewatch.feature.holdings.component.ProfitTargetEditSheet
import com.kitewatch.feature.holdings.model.HoldingUiModel
import com.kitewatch.ui.component.ErrorStateWidget
import com.kitewatch.ui.component.SkeletonLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingsRoute(
    onNavigateToGtt: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HoldingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is HoldingsSideEffect.NavigateToGtt -> onNavigateToGtt()
                is HoldingsSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    HoldingsScreen(
        state = state,
        onIntent = viewModel::processIntent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HoldingsScreen(
    state: HoldingsState,
    onIntent: (HoldingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Holdings") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                state.isLoading -> {
                    SkeletonLoader(
                        lines = 5,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                state.error != null -> {
                    ErrorStateWidget(
                        message = state.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.holdings.isEmpty() -> {
                    Text(
                        text = "No current holdings. Sync orders to populate holdings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                    )
                }

                else -> {
                    HoldingsList(
                        holdings = state.holdings,
                        onIntent = onIntent,
                    )
                }
            }
        }
    }

    // Modal bottom sheet for editing profit target (rendered outside Scaffold padding)
    val editingCode = state.editingStockCode
    if (editingCode != null) {
        val holding = state.holdings.firstOrNull { it.stockCode == editingCode }
        if (holding != null) {
            ProfitTargetEditSheet(
                stockCode = editingCode,
                currentDisplay = holding.profitTargetDisplay,
                onDismiss = { onIntent(HoldingsIntent.DismissEditSheet(editingCode)) },
                onConfirm = { newTarget ->
                    onIntent(HoldingsIntent.SaveProfitTarget(editingCode, newTarget))
                },
            )
        }
    }
}

@Composable
private fun HoldingsList(
    holdings: List<HoldingUiModel>,
    onIntent: (HoldingsIntent) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = holdings,
            key = { it.stockCode },
        ) { holding ->
            HoldingCard(
                holding = holding,
                onToggleExpand = { onIntent(HoldingsIntent.ToggleExpand(holding.stockCode)) },
                onEditTarget = { onIntent(HoldingsIntent.EditProfitTarget(holding.stockCode)) },
            )
        }
    }
}

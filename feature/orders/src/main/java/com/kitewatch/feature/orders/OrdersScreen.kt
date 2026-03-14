package com.kitewatch.feature.orders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.kitewatch.feature.orders.component.OrderRow
import com.kitewatch.feature.orders.model.OrderUiModel
import com.kitewatch.ui.component.DateRangePreset
import com.kitewatch.ui.component.FilterChipGroup
import com.kitewatch.ui.component.PaginatedLazyColumn
import com.kitewatch.ui.component.PaginatedLazyColumnOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersRoute(
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagingItems = viewModel.ordersPaged.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is OrdersSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    OrdersScreen(
        state = state,
        pagingItems = pagingItems,
        onIntent = viewModel::processIntent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrdersScreen(
    state: OrdersState,
    pagingItems: LazyPagingItems<OrderUiModel>,
    onIntent: (OrdersIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Orders") },
                actions = {
                    if (state.isSyncing) {
                        CircularProgressIndicator()
                    } else {
                        IconButton(onClick = { onIntent(OrdersIntent.SyncNow) }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Sync orders",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            FilterChipGroup(
                options = DateRangePreset.entries.filter { it != DateRangePreset.CUSTOM },
                selectedOption = state.selectedRange,
                onSelect = { onIntent(OrdersIntent.SelectDateRange(it)) },
                labelFor = { it.toLabel() },
            )

            PaginatedLazyColumn(
                pagingItems = pagingItems,
                itemContent = { order -> OrderRow(order = order) },
                emptyState = {
                    Text("No orders recorded. Sync today's orders or import historical data.")
                },
                errorState = { t ->
                    Text("Error loading orders: ${t.message}")
                },
                options = PaginatedLazyColumnOptions(key = { it.orderId }),
            )
        }
    }
}

private fun DateRangePreset.toLabel(): String =
    when (this) {
        DateRangePreset.TODAY -> "Today"
        DateRangePreset.THIS_WEEK -> "This week"
        DateRangePreset.THIS_MONTH -> "This month"
        DateRangePreset.THIS_YEAR -> "This year"
        DateRangePreset.ALL_TIME -> "All time"
        DateRangePreset.CUSTOM -> "Custom"
    }

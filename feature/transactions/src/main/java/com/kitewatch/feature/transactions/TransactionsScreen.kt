package com.kitewatch.feature.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.kitewatch.feature.transactions.component.TransactionRow
import com.kitewatch.feature.transactions.model.TransactionUiModel
import com.kitewatch.ui.component.FilterChipGroup
import com.kitewatch.ui.component.PaginatedLazyColumn
import com.kitewatch.ui.component.PaginatedLazyColumnOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsRoute(
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagingItems = viewModel.transactionsPaged.collectAsLazyPagingItems()

    TransactionsScreen(
        state = state,
        pagingItems = pagingItems,
        onIntent = viewModel::processIntent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionsScreen(
    state: TransactionsState,
    pagingItems: LazyPagingItems<TransactionUiModel>,
    onIntent: (TransactionsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedFilter = TransactionFilter.entries.find { it.type == state.selectedType } ?: TransactionFilter.ALL

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Transactions") })
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            FilterChipGroup(
                options = TransactionFilter.entries,
                selectedOption = selectedFilter,
                onSelect = { filter -> onIntent(TransactionsIntent.FilterByType(filter.type)) },
                labelFor = { it.toLabel() },
            )

            PaginatedLazyColumn(
                pagingItems = pagingItems,
                itemContent = { txn -> TransactionRow(transaction = txn) },
                emptyState = {
                    Text("No transactions recorded.")
                },
                errorState = { t ->
                    Text("Error loading transactions: ${t.message}")
                },
                options = PaginatedLazyColumnOptions(key = { it.transactionId }),
            )
        }
    }
}

private fun TransactionFilter.toLabel(): String =
    when (this) {
        TransactionFilter.ALL -> "All"
        TransactionFilter.BUY -> "Buy"
        TransactionFilter.SELL -> "Sell"
        TransactionFilter.FUND_CREDIT -> "Fund Credit"
        TransactionFilter.FUND_DEBIT -> "Fund Debit"
    }

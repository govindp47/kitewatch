package com.kitewatch.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey

@Composable
fun <T : Any> PaginatedLazyColumn(
    pagingItems: LazyPagingItems<T>,
    itemContent: @Composable (T) -> Unit,
    emptyState: @Composable () -> Unit,
    errorState: @Composable (Throwable) -> Unit,
    options: PaginatedLazyColumnOptions<T> = PaginatedLazyColumnOptions(),
) {
    val isInitialLoading = pagingItems.loadState.refresh is LoadState.Loading
    val isInitialError = pagingItems.loadState.refresh is LoadState.Error
    val isEmpty = !isInitialLoading && !isInitialError && pagingItems.itemCount == 0

    when {
        isInitialLoading -> {
            Box(modifier = options.modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        isInitialError -> {
            val error = (pagingItems.loadState.refresh as LoadState.Error).error
            Box(modifier = options.modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                errorState(error)
            }
        }

        isEmpty -> {
            Box(modifier = options.modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                emptyState()
            }
        }

        else -> {
            LazyColumn(modifier = options.modifier) {
                items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey(options.key),
                ) { index ->
                    val item = pagingItems[index]
                    if (item != null) {
                        itemContent(item)
                    }
                }

                // Append loading indicator
                if (pagingItems.loadState.append is LoadState.Loading) {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

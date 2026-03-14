package com.kitewatch.feature.gtt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import com.kitewatch.feature.gtt.component.GttRecordRow
import com.kitewatch.feature.gtt.model.GttUiModel
import com.kitewatch.ui.component.SkeletonLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GttRoute(
    modifier: Modifier = Modifier,
    viewModel: GttViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GttScreen(state = state, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GttScreen(
    state: GttState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("GTT Orders") },
                actions = {
                    if (state.unacknowledgedOverrides > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ) {
                                    Text(state.unacknowledgedOverrides.toString())
                                }
                            },
                            modifier = Modifier.padding(end = 16.dp),
                        ) {}
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                SkeletonLoader(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }

            state.records.isEmpty() -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No active GTT orders.")
                }
            }

            else -> {
                GttList(
                    records = state.records,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun GttList(
    records: List<GttUiModel>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(records, key = { it.gttId }) { record ->
            GttRecordRow(record = record)
        }
    }
}

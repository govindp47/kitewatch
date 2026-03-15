package com.kitewatch.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.kitewatch.feature.settings.component.CsvErrorRow

@Composable
fun CsvImportRoute(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CsvImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CsvImportScreen(
        state = state,
        onIntent = viewModel::processIntent,
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CsvImportScreen(
    state: CsvImportState,
    onIntent: (CsvImportIntent) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri != null) onIntent(CsvImportIntent.SelectFile(uri))
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Import CSV") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate up",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->

        // Full-screen progress overlay blocks interaction during import
        if (state.isImporting) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Select a CSV file exported from Kite (Trade Book or Orders) or a custom KiteWatch CSV.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { filePicker.launch("text/csv") },
                    enabled = !state.isImporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Select CSV File")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Success result
            if (state.result is CsvImportUiResult.Success) {
                item {
                    CsvImportSuccessCard(result = state.result)
                }
            }

            // Validation failure result
            if (state.result is CsvImportUiResult.Failure) {
                item {
                    Text(
                        text = "Import Failed — ${state.result.errors.size} error(s) found",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        text = "No orders were imported. Fix the errors below and try again.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                }
                items(
                    items = state.result.errors,
                    key = { e -> "${e.rowNumber}:${e.field}" },
                ) { error ->
                    CsvErrorRow(error = error)
                    HorizontalDivider()
                }
            }

            // Generic (non-validation) error
            if (state.error != null) {
                item {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CsvImportSuccessCard(
    result: CsvImportUiResult.Success,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Import Successful",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Orders imported: ${result.newOrderCount}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Duplicates skipped: ${result.skippedDuplicateCount}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (result.estimatedChargesCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        "⚠ ${result.estimatedChargesCount} order(s) used estimated charge rates" +
                            " — refresh charge rates for accurate figures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

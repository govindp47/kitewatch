package com.kitewatch.feature.portfolio.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitewatch.feature.portfolio.model.SyncStatusUiModel

@Composable
internal fun FundBalanceCard(
    fundBalance: String,
    syncStatus: SyncStatusUiModel,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Available Funds",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = fundBalance.ifEmpty { "—" },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (syncStatus.lastSyncTime != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reconciled: ${syncStatus.lastSyncTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            if (syncStatus.lastSyncFailed) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last sync failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

package com.kitewatch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.temporal.ChronoUnit

private val GreenIndicator = Color(0xFF34A853)
private val AmberIndicator = Color(0xFFFBBC04)
private val RedIndicator = Color(0xFFEA4335)

@Composable
fun StatusIndicator(
    lastSyncTime: Instant?,
    lastSyncFailed: Boolean,
    modifier: Modifier = Modifier,
) {
    val status =
        when {
            lastSyncFailed -> SyncStatus.FAILED
            lastSyncTime == null -> SyncStatus.STALE
            ChronoUnit.HOURS.between(lastSyncTime, Instant.now()) > 24 -> SyncStatus.STALE
            else -> SyncStatus.SYNCED
        }

    val dotColor =
        when (status) {
            SyncStatus.SYNCED -> GreenIndicator
            SyncStatus.STALE -> AmberIndicator
            SyncStatus.FAILED -> RedIndicator
        }

    val label =
        when (status) {
            SyncStatus.SYNCED -> "Synced"
            SyncStatus.STALE -> "Stale"
            SyncStatus.FAILED -> "Sync failed"
        }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(color = dotColor, shape = CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

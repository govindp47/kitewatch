package com.kitewatch.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import com.kitewatch.feature.settings.component.BackupHistoryRow
import com.kitewatch.feature.settings.component.SettingsSectionHeader
import com.kitewatch.ui.component.ConfirmationDialog
import com.kitewatch.ui.component.ConfirmationDialogState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreRoute(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is BackupRestoreSideEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    BackupRestoreScreen(
        state = state,
        onIntent = viewModel::processIntent,
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupRestoreScreen(
    state: BackupRestoreState,
    onIntent: (BackupRestoreIntent) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    // Blocking progress overlay during backup or restore
    val isBlocking = state.isBackingUp || state.isRestoring

    // File picker launcher for local restore
    val localFilePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) onIntent(BackupRestoreIntent.RestoreFromLocal(uri))
        }

    // Pre-restore confirmation dialog
    val pendingSource = state.pendingRestoreSource
    val preview = state.pendingBackupPreview
    if (pendingSource != null) {
        val dialogMessage =
            if (preview != null) {
                "Account: ${preview.accountId}\nCreated: ${preview.createdAt}\n\n" +
                    "A safety backup will be created before restoring. " +
                    "All current data will be replaced."
            } else {
                "All current data will be replaced. A safety backup will be created first."
            }
        ConfirmationDialog(
            state =
                ConfirmationDialogState(
                    title = "Restore backup?",
                    message = dialogMessage,
                    confirmLabel = "Restore",
                    cancelLabel = "Cancel",
                    onConfirm = { onIntent(BackupRestoreIntent.ConfirmRestore(pendingSource)) },
                    onCancel = { onIntent(BackupRestoreIntent.CancelRestore) },
                ),
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // ── Backup ───────────────────────────────────────────────────────
                item { SettingsSectionHeader(title = "Create Backup") }
                item {
                    Button(
                        onClick = { onIntent(BackupRestoreIntent.CreateBackup(BackupDestination.DRIVE)) },
                        enabled = !isBlocking,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text("Back up to Google Drive")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onIntent(BackupRestoreIntent.CreateBackup(BackupDestination.LOCAL)) },
                        enabled = !isBlocking,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text("Save local backup")
                    }
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                // ── Restore ──────────────────────────────────────────────────────
                item { SettingsSectionHeader(title = "Restore") }
                item {
                    OutlinedButton(
                        onClick = {
                            localFilePicker.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                        enabled = !isBlocking,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text("Restore from local file…")
                    }
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                // ── Drive backup history ─────────────────────────────────────────
                item { SettingsSectionHeader(title = "Drive Backups") }

                if (state.driveBackups.isEmpty() && !state.isLoadingDriveBackups) {
                    item {
                        Text(
                            text = "No Drive backups found.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                items(state.driveBackups, key = { it.fileId }) { entry ->
                    BackupHistoryRow(
                        entry = entry,
                        onRestoreClick = { fileId ->
                            onIntent(BackupRestoreIntent.RestoreFromDrive(fileId))
                        },
                    )
                }

                // ── Error ────────────────────────────────────────────────────────
                val error = state.error
                if (error != null) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = error,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Blocking progress indicator
            if (isBlocking) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

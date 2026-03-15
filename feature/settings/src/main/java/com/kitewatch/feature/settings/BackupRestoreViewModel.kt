package com.kitewatch.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.infra.backup.BackupFileWriter
import com.kitewatch.infra.backup.model.BackupResult
import com.kitewatch.infra.backup.usecase.CreateBackupUseCase
import com.kitewatch.infra.backup.usecase.RestoreBackupUseCase
import com.kitewatch.infra.backup.usecase.RestoreResult
import com.kitewatch.infra.backup.usecase.RestoreSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import com.kitewatch.infra.backup.model.BackupDestination as InfraBackupDestination

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class BackupRestoreState(
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val driveBackups: List<DriveBackupEntry> = emptyList(),
    val isLoadingDriveBackups: Boolean = false,
    /** Non-null when the confirmation dialog should be shown. */
    val pendingRestoreSource: RestoreSource? = null,
    /** Metadata parsed from the pending backup file — shown in the confirmation dialog. */
    val pendingBackupPreview: BackupPreview? = null,
    val lastBackupHistory: List<BackupHistoryEntry> = emptyList(),
    val error: String? = null,
)

/** A Drive file listed in backup history. */
data class DriveBackupEntry(
    val fileId: String,
    val fileName: String,
    val createdAt: String,
    val accountId: String,
    val fileSizeBytes: Long,
)

/** A local backup record shown in the history list. */
data class BackupHistoryEntry(
    val label: String,
    val createdAt: String,
    val filePath: String,
)

/** Metadata extracted from the backup header — shown in the pre-restore confirmation dialog. */
data class BackupPreview(
    val accountId: String,
    val createdAt: String,
    val schemaVersion: Int,
)

// ---------------------------------------------------------------------------
// Side effects
// ---------------------------------------------------------------------------

sealed class BackupRestoreSideEffect {
    data class ShowSnackbar(
        val message: String,
    ) : BackupRestoreSideEffect()
}

// ---------------------------------------------------------------------------
// Intents
// ---------------------------------------------------------------------------

sealed class BackupRestoreIntent {
    /** Trigger a manual backup to [destination]. */
    data class CreateBackup(
        val destination: BackupDestination,
    ) : BackupRestoreIntent()

    /** Refresh the list of Drive backup files. */
    object ListDriveBackups : BackupRestoreIntent()

    /** User picked a Drive file — show confirmation dialog. */
    data class RestoreFromDrive(
        val fileId: String,
    ) : BackupRestoreIntent()

    /** User picked a local file URI — copy to temp and show confirmation dialog. */
    data class RestoreFromLocal(
        val uri: Uri,
    ) : BackupRestoreIntent()

    /** User confirmed the restore in the dialog — run the actual restore. */
    data class ConfirmRestore(
        val source: RestoreSource,
    ) : BackupRestoreIntent()

    /** User dismissed the confirmation dialog. */
    object CancelRestore : BackupRestoreIntent()

    /** Clear the current error banner. */
    object DismissError : BackupRestoreIntent()
}

enum class BackupDestination { LOCAL, DRIVE }

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class BackupRestoreViewModel
    @Inject
    constructor(
        private val createBackupUseCase: CreateBackupUseCase,
        private val restoreBackupUseCase: RestoreBackupUseCase,
        private val accountBindingRepository: AccountBindingRepository,
        private val backupDriveRepository: BackupDriveRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _state = MutableStateFlow(BackupRestoreState())
        val state: StateFlow<BackupRestoreState> = _state.asStateFlow()

        private val _sideEffect = Channel<BackupRestoreSideEffect>(Channel.BUFFERED)
        val sideEffect: Flow<BackupRestoreSideEffect> = _sideEffect.receiveAsFlow()

        init {
            processIntent(BackupRestoreIntent.ListDriveBackups)
        }

        fun processIntent(intent: BackupRestoreIntent) {
            when (intent) {
                is BackupRestoreIntent.CreateBackup -> createBackup(intent.destination)
                is BackupRestoreIntent.ListDriveBackups -> listDriveBackups()
                is BackupRestoreIntent.RestoreFromDrive -> prepareRestoreFromDrive(intent.fileId)
                is BackupRestoreIntent.RestoreFromLocal -> prepareRestoreFromLocal(intent.uri)
                is BackupRestoreIntent.ConfirmRestore -> confirmRestore(intent.source)
                is BackupRestoreIntent.CancelRestore -> cancelRestore()
                is BackupRestoreIntent.DismissError -> _state.update { it.copy(error = null) }
            }
        }

        // ── Create backup ─────────────────────────────────────────────────────────

        private fun createBackup(destination: BackupDestination) {
            viewModelScope.launch {
                _state.update { it.copy(isBackingUp = true, error = null) }
                runCatching {
                    val accountId =
                        accountBindingRepository.getBinding()?.userId
                            ?: error("No account bound — cannot create backup")
                    val dest =
                        when (destination) {
                            BackupDestination.LOCAL -> InfraBackupDestination.LOCAL
                            BackupDestination.DRIVE -> InfraBackupDestination.GOOGLE_DRIVE
                        }
                    createBackupUseCase.execute(accountId = accountId, destination = dest)
                }.onSuccess { result ->
                    _state.update { it.copy(isBackingUp = false) }
                    val label =
                        when (result) {
                            is BackupResult.Success -> "Backup saved to Drive"
                            is BackupResult.LocalFallback -> "Backup saved locally (Drive unavailable)"
                        }
                    _sideEffect.send(BackupRestoreSideEffect.ShowSnackbar(label))
                    if (destination == BackupDestination.DRIVE) {
                        listDriveBackups()
                    }
                }.onFailure { t ->
                    _state.update { it.copy(isBackingUp = false, error = t.message) }
                }
            }
        }

        // ── List Drive backups ────────────────────────────────────────────────────

        private fun listDriveBackups() {
            viewModelScope.launch {
                _state.update { it.copy(isLoadingDriveBackups = true) }
                runCatching { backupDriveRepository.listBackups() }
                    .onSuccess { list ->
                        _state.update { it.copy(driveBackups = list, isLoadingDriveBackups = false) }
                    }.onFailure {
                        _state.update { it.copy(isLoadingDriveBackups = false) }
                    }
            }
        }

        // ── Prepare restore (Drive) ───────────────────────────────────────────────

        private fun prepareRestoreFromDrive(fileId: String) {
            viewModelScope.launch {
                runCatching {
                    val bytes = backupDriveRepository.downloadBackupBytes(fileId)
                    parsePreview(bytes)
                }.onSuccess { preview ->
                    _state.update {
                        it.copy(
                            pendingRestoreSource = RestoreSource.Drive(fileId),
                            pendingBackupPreview = preview,
                        )
                    }
                }.onFailure { t ->
                    _state.update { it.copy(error = "Cannot read backup: ${t.message}") }
                }
            }
        }

        // ── Prepare restore (local URI) ───────────────────────────────────────────

        private fun prepareRestoreFromLocal(uri: Uri) {
            viewModelScope.launch {
                runCatching {
                    val bytes =
                        context.contentResolver.openInputStream(uri)?.readBytes()
                            ?: error("Cannot open file")
                    val tempFile = File.createTempFile("restore_preview", ".kwbackup", context.cacheDir)
                    tempFile.writeBytes(bytes)
                    parsePreview(bytes) to tempFile.absolutePath
                }.onSuccess { (preview, path) ->
                    _state.update {
                        it.copy(
                            pendingRestoreSource = RestoreSource.Local(path),
                            pendingBackupPreview = preview,
                        )
                    }
                }.onFailure { t ->
                    _state.update { it.copy(error = "Cannot read backup: ${t.message}") }
                }
            }
        }

        // ── Confirm restore ───────────────────────────────────────────────────────

        private fun confirmRestore(source: RestoreSource) {
            _state.update { it.copy(pendingRestoreSource = null, pendingBackupPreview = null) }
            viewModelScope.launch {
                _state.update { it.copy(isRestoring = true, error = null) }
                // Pre-restore safety backup (best-effort — warn but do not block)
                val accountId = accountBindingRepository.getBinding()?.userId
                val safetyBackupWarning =
                    runCatching {
                        if (accountId != null) {
                            createBackupUseCase.execute(
                                accountId = accountId,
                                destination = InfraBackupDestination.LOCAL,
                            )
                        }
                    }.exceptionOrNull()?.message

                runCatching {
                    restoreBackupUseCase.execute(source = source, accountId = accountId)
                }.onSuccess { result ->
                    val suffix = if (safetyBackupWarning != null) " (pre-restore backup failed)" else ""
                    _state.update { it.copy(isRestoring = false) }
                    _sideEffect.send(
                        BackupRestoreSideEffect.ShowSnackbar(
                            "Restore complete — ${(result as RestoreResult.Success).recordCount} records$suffix",
                        ),
                    )
                }.onFailure { t ->
                    _state.update { it.copy(isRestoring = false, error = t.message) }
                }
            }
        }

        // ── Cancel restore ────────────────────────────────────────────────────────

        private fun cancelRestore() {
            _state.update { it.copy(pendingRestoreSource = null, pendingBackupPreview = null) }
        }

        // ── Helper ────────────────────────────────────────────────────────────────

        private fun parsePreview(bytes: ByteArray): BackupPreview {
            val header =
                BackupFileWriter.parseHeader(bytes)
                    ?: error("File too short to contain a valid header")
            if (!header.isMagicValid()) error("Not a .kwbackup file")
            return BackupPreview(
                accountId = header.accountId,
                createdAt = header.createdAt,
                schemaVersion = header.schemaVersion,
            )
        }
    }

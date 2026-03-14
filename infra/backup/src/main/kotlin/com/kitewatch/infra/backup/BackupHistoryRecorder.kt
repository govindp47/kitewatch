package com.kitewatch.infra.backup

import com.kitewatch.infra.backup.model.BackupDestination

/**
 * Records the outcome of each backup attempt so the UI and scheduled jobs can
 * inspect history. The production implementation (wired in T-065) will write to
 * the backup_history table. This no-op default lets [CreateBackupUseCase]
 * compile and run tests without a Room dependency on that table.
 */
interface BackupHistoryRecorder {
    suspend fun record(
        fileName: String,
        fileSizeBytes: Long,
        destination: BackupDestination,
        status: String,
        errorMessage: String? = null,
    )
}

/** No-op implementation used in tests and as a default until T-065 wires the DAO. */
class NoOpBackupHistoryRecorder : BackupHistoryRecorder {
    override suspend fun record(
        fileName: String,
        fileSizeBytes: Long,
        destination: BackupDestination,
        status: String,
        errorMessage: String?,
    ) = Unit
}

package com.kitewatch.infra.backup.model

sealed class BackupResult {
    data class Success(
        val fileName: String,
        val fileSizeBytes: Long,
        val destination: BackupDestination,
    ) : BackupResult()

    data class LocalFallback(
        val fileName: String,
        val fileSizeBytes: Long,
        val driveError: String?,
    ) : BackupResult()
}

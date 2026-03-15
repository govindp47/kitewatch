package com.kitewatch.infra.backup.datasource

/**
 * Remote data source for Google Drive backup file operations.
 * Implemented in T-064. This stub allows [CreateBackupUseCase] and
 * [RestoreBackupUseCase] to depend on the interface without a direct
 * `:core-network` dependency from `:infra-backup`.
 */
interface GoogleDriveRemoteDataSource {
    /**
     * Uploads [fileBytes] to Drive with the given [fileName].
     * Returns the Drive file ID on success.
     * Throws on failure (network error, auth revoked, quota exceeded, etc.).
     */
    suspend fun upload(
        fileName: String,
        fileBytes: ByteArray,
    ): String

    /**
     * Downloads the file identified by [fileId] from Drive and returns its bytes.
     * Throws on failure.
     */
    suspend fun downloadBackup(fileId: String): ByteArray
}

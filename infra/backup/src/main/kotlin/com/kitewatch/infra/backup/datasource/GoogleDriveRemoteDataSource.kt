package com.kitewatch.infra.backup.datasource

/**
 * Remote data source for Google Drive backup uploads.
 * Implemented in T-064. This stub allows [CreateBackupUseCase] to depend on the
 * interface and fall back gracefully when Drive is unavailable.
 */
interface GoogleDriveRemoteDataSource {
    /**
     * Uploads [fileBytes] to Drive with the given [fileName].
     * Returns the Drive file ID on success.
     * Throws an exception on failure (network error, auth revoked, quota exceeded, etc.).
     */
    suspend fun upload(
        fileName: String,
        fileBytes: ByteArray,
    ): String
}

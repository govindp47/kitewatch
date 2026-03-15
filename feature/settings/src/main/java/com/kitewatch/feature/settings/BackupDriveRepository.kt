package com.kitewatch.feature.settings

/**
 * Provides Drive backup listing and download for the Backup & Restore UI.
 * The production implementation (wired in :app) delegates to the Drive API.
 */
interface BackupDriveRepository {
    /** Returns all `.kwbackup` files stored in the app's Drive folder, newest first. */
    suspend fun listBackups(): List<DriveBackupEntry>

    /** Downloads the raw bytes of the Drive file identified by [fileId]. */
    suspend fun downloadBackupBytes(fileId: String): ByteArray
}

package com.kitewatch.domain.error

sealed class AppError {
    // -------------------------------------------------------------------------
    // Network
    // -------------------------------------------------------------------------

    sealed class NetworkError : AppError() {
        data object Timeout : NetworkError()

        data object NoConnection : NetworkError()

        data class HttpError(
            val code: Int,
            val message: String,
        ) : NetworkError()

        data object RateLimited : NetworkError()

        data class Unexpected(
            val cause: Throwable?,
        ) : NetworkError()
    }

    // -------------------------------------------------------------------------
    // Database
    // -------------------------------------------------------------------------

    sealed class DatabaseError : AppError() {
        data class MigrationFailed(
            val fromVersion: Int,
            val toVersion: Int,
        ) : DatabaseError()

        data object CorruptionDetected : DatabaseError()

        data class QueryFailed(
            val cause: Throwable?,
        ) : DatabaseError()

        data object IntegrityViolation : DatabaseError()
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    sealed class AuthError : AppError() {
        data object NotAuthenticated : AuthError()

        data object TokenExpired : AuthError()

        data object TokenStorageFailed : AuthError()

        data object InvalidApiKey : AuthError()

        data object SessionRevoked : AuthError()
    }

    // -------------------------------------------------------------------------
    // Domain
    // -------------------------------------------------------------------------

    sealed class DomainError : AppError() {
        /** BR-01: Account already bound to this installation. */
        data object AlreadyBound : DomainError()

        /** BR-02: Backup/import account ID does not match bound account. */
        data class AccountMismatch(
            val expected: String,
            val found: String,
        ) : DomainError()

        /** BR-06: Charge rates absent when calculation is requested. */
        data object ChargeRatesMissing : DomainError()

        /** BR-07: Remote holdings do not match local holdings before order persistence. */
        data class HoldingsMismatch(
            val diffs: List<String>,
        ) : DomainError()

        /** BR-08: GTT verification failed after placement/update. */
        data object GttVerificationFailed : DomainError()

        data class GttCreationFailed(
            val stockCode: String,
        ) : DomainError()

        data class GttUpdateFailed(
            val stockCode: String,
        ) : DomainError()

        /** Sell quantity exceeds available buy lots for a stock. */
        data class OverSell(
            val stockCode: String,
            val overSellQty: Int,
        ) : DomainError()

        /** Holdings fetch failed during order sync. */
        data object HoldingsFetchFailed : DomainError()
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    sealed class ValidationError : AppError() {
        data class InvalidCsvFormat(
            val details: String,
        ) : ValidationError()

        data class CsvHeaderMismatch(
            val expected: List<String>,
            val found: List<String>,
        ) : ValidationError()

        data class InvalidRow(
            val rowNumber: Int,
            val reason: String,
        ) : ValidationError()

        data class DuplicateEntry(
            val key: String,
        ) : ValidationError()

        data object NegativeAmount : ValidationError()

        data object ZeroQuantity : ValidationError()
    }

    // -------------------------------------------------------------------------
    // Backup
    // -------------------------------------------------------------------------

    sealed class BackupError : AppError() {
        data object DriveUploadFailed : BackupError()

        data object DriveDownloadFailed : BackupError()

        data object IntegrityCheckFailed : BackupError()

        data class SchemaMismatch(
            val backupVersion: Int,
            val currentVersion: Int,
        ) : BackupError()

        data object CorruptedFile : BackupError()

        data object AccountMismatch : BackupError()
    }
}

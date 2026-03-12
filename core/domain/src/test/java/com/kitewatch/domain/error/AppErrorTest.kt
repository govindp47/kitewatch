package com.kitewatch.domain.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorTest {
    /**
     * Exhaustive when-expression over AppError.
     * This test will not compile if any branch of the sealed hierarchy is missing,
     * enforcing that all error categories are handled at call sites.
     */
    @Test
    fun `when expression on AppError is exhaustive`() {
        val errors: List<AppError> =
            listOf(
                AppError.NetworkError.Timeout,
                AppError.NetworkError.NoConnection,
                AppError.NetworkError.HttpError(404, "Not Found"),
                AppError.NetworkError.RateLimited,
                AppError.NetworkError.Unexpected(null),
                AppError.DatabaseError.MigrationFailed(1, 2),
                AppError.DatabaseError.CorruptionDetected,
                AppError.DatabaseError.QueryFailed(null),
                AppError.DatabaseError.IntegrityViolation,
                AppError.AuthError.NotAuthenticated,
                AppError.AuthError.TokenExpired,
                AppError.AuthError.TokenStorageFailed,
                AppError.AuthError.InvalidApiKey,
                AppError.AuthError.SessionRevoked,
                AppError.DomainError.AlreadyBound,
                AppError.DomainError.AccountMismatch("u1", "u2"),
                AppError.DomainError.ChargeRatesMissing,
                AppError.DomainError.HoldingsMismatch(listOf("INFY")),
                AppError.DomainError.GttVerificationFailed,
                AppError.DomainError.GttCreationFailed("INFY"),
                AppError.DomainError.GttUpdateFailed("INFY"),
                AppError.DomainError.OverSell("INFY", 5),
                AppError.DomainError.HoldingsFetchFailed,
                AppError.ValidationError.InvalidCsvFormat("bad header"),
                AppError.ValidationError.CsvHeaderMismatch(listOf("A"), listOf("B")),
                AppError.ValidationError.InvalidRow(3, "negative qty"),
                AppError.ValidationError.DuplicateEntry("ORDER-123"),
                AppError.ValidationError.NegativeAmount,
                AppError.ValidationError.ZeroQuantity,
                AppError.BackupError.DriveUploadFailed,
                AppError.BackupError.DriveDownloadFailed,
                AppError.BackupError.IntegrityCheckFailed,
                AppError.BackupError.SchemaMismatch(1, 2),
                AppError.BackupError.CorruptedFile,
                AppError.BackupError.AccountMismatch,
            )

        for (error in errors) {
            val category =
                when (error) {
                    is AppError.NetworkError -> "network"
                    is AppError.DatabaseError -> "database"
                    is AppError.AuthError -> "auth"
                    is AppError.DomainError -> "domain"
                    is AppError.ValidationError -> "validation"
                    is AppError.BackupError -> "backup"
                }
            assertTrue(category.isNotEmpty())
        }
    }

    @Test
    fun `NetworkError HttpError carries code and message`() {
        val error = AppError.NetworkError.HttpError(429, "Too Many Requests")
        assertEquals(429, error.code)
        assertEquals("Too Many Requests", error.message)
    }

    @Test
    fun `DomainError AccountMismatch carries expected and found`() {
        val error = AppError.DomainError.AccountMismatch("user-A", "user-B")
        assertEquals("user-A", error.expected)
        assertEquals("user-B", error.found)
    }

    @Test
    fun `DomainError HoldingsMismatch carries diff list`() {
        val diffs = listOf("INFY: local=10 remote=12", "HDFC: local=5 remote=0")
        val error = AppError.DomainError.HoldingsMismatch(diffs)
        assertEquals(diffs, error.diffs)
    }

    @Test
    fun `BackupError SchemaMismatch carries version numbers`() {
        val error = AppError.BackupError.SchemaMismatch(backupVersion = 1, currentVersion = 3)
        assertEquals(1, error.backupVersion)
        assertEquals(3, error.currentVersion)
    }
}

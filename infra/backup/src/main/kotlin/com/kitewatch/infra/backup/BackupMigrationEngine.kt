package com.kitewatch.infra.backup

import com.kitewatch.infra.backup.proto.KiteWatchBackup

/**
 * Applies sequential schema migrations to a [KiteWatchBackup] protobuf message.
 *
 * When a backup is created on schema version N and restored on schema version M (N < M),
 * this engine applies migrations N→N+1, N+1→N+2, … M-1→M in order.
 *
 * Current state: schema version 1 — no migrations exist yet. The framework is in place
 * so that future tasks can add migrations without touching [RestoreBackupUseCase].
 */
object BackupMigrationEngine {
    /**
     * Migrates [data] from [fromVersion] to [toVersion] by applying individual
     * version steps sequentially. Returns [data] unchanged when [fromVersion] == [toVersion].
     *
     * @throws UnsupportedBackupMigrationException if a required migration step is not implemented.
     */
    fun migrate(
        data: KiteWatchBackup,
        fromVersion: Int,
        toVersion: Int,
    ): KiteWatchBackup {
        var migrated = data
        for (version in fromVersion until toVersion) {
            migrated = applyStep(migrated, version, version + 1)
        }
        return migrated
    }

    private fun applyStep(
        ignored: KiteWatchBackup,
        from: Int,
        to: Int,
    ): KiteWatchBackup =
        when (from to to) {
            // Add future migration steps here, e.g.:
            // 1 to 2 -> migrateV1ToV2(ignored)
            else -> throw UnsupportedBackupMigrationException(from, to)
        }
}

/** Thrown when [BackupMigrationEngine] has no migration path between two schema versions. */
class UnsupportedBackupMigrationException(
    from: Int,
    to: Int,
) : Exception("No backup migration defined from schema v$from to v$to")

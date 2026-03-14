package com.kitewatch.infra.backup.model

/**
 * Domain representation of user preferences for backup/restore purposes.
 * Mirrors the UserPreferences proto message fields.
 */
data class UserPreferences(
    val themeMode: String = "SYSTEM",
    val orderSyncTimes: List<String> = emptyList(),
    val reconciliationTime: String = "20:00",
    val chargeRateRefreshDays: Int = 30,
    val reconciliationTolerancePaisa: Long = 100L,
    val gmailEnabled: Boolean = false,
    val scheduledBackupEnabled: Boolean = false,
    val backupIntervalDays: Int = 7,
    val backupDestination: String = "GOOGLE_DRIVE",
    val lockTimeoutMinutes: Int = 5,
    val defaultProfitTargetType: String = "PERCENTAGE",
    val defaultProfitTargetValue: Int = 500,
)

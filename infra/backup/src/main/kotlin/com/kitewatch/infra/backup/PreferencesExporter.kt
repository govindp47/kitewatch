package com.kitewatch.infra.backup

import com.kitewatch.infra.backup.model.UserPreferences

/**
 * Exposes user preferences for backup assembly.
 * The production implementation (wired in a later task) reads from DataStore.
 * A test double can return a fixed [UserPreferences] instance.
 */
interface PreferencesExporter {
    suspend fun exportAll(): UserPreferences
}

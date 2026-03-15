package com.kitewatch.infra.backup

import com.kitewatch.infra.backup.model.UserPreferences

/**
 * Writes user preferences back to their persistent store during a backup restore.
 * The production implementation (wired in a later task) writes to DataStore.
 * A test double can capture the imported [UserPreferences] for assertion.
 */
interface PreferencesRestorer {
    suspend fun importAll(preferences: UserPreferences)
}

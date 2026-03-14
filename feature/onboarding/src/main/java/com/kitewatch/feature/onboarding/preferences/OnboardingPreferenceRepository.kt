package com.kitewatch.feature.onboarding.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists onboarding completion state via DataStore<Preferences>.
 *
 * This is non-sensitive data — stored in the plain (unencrypted) DataStore
 * per the security model (DataStore holds non-sensitive preferences only).
 */
@Singleton
class OnboardingPreferenceRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val isOnboardingComplete: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[ONBOARDING_COMPLETE_KEY] ?: false
            }

        suspend fun setOnboardingComplete(complete: Boolean) {
            dataStore.edit { it[ONBOARDING_COMPLETE_KEY] = complete }
        }

        companion object {
            private val ONBOARDING_COMPLETE_KEY = booleanPreferencesKey("onboarding_complete")
        }
    }

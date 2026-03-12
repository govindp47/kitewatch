package com.kitewatch.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferenceRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val isDarkTheme: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[DARK_THEME_KEY] ?: false
            }

        suspend fun setDarkTheme(dark: Boolean) {
            dataStore.edit { it[DARK_THEME_KEY] = dark }
        }

        companion object {
            private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
        }
    }

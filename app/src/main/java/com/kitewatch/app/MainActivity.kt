package com.kitewatch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitewatch.app.navigation.AppNavGraph
import com.kitewatch.data.preferences.ThemePreferenceRepository
import com.kitewatch.ui.theme.KiteWatchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var themePreferenceRepository: ThemePreferenceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDarkTheme by themePreferenceRepository.isDarkTheme
                .collectAsStateWithLifecycle(initialValue = false)

            KiteWatchTheme(darkTheme = isDarkTheme) {
                AppNavGraph()
            }
        }
    }
}

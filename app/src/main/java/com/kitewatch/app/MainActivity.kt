package com.kitewatch.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitewatch.app.navigation.AppNavGraph
import com.kitewatch.data.preferences.ThemePreferenceRepository
import com.kitewatch.feature.auth.AppLockScreen
import com.kitewatch.infra.auth.BiometricAuthManager
import com.kitewatch.infra.auth.LockState
import com.kitewatch.ui.theme.KiteWatchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var themePreferenceRepository: ThemePreferenceRepository

    @Inject
    lateinit var biometricAuthManager: BiometricAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots on all screens (single-activity architecture)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        setContent {
            val isDarkTheme by themePreferenceRepository.isDarkTheme
                .collectAsStateWithLifecycle(initialValue = false)
            val lockState by biometricAuthManager.lockState
                .collectAsStateWithLifecycle()

            KiteWatchTheme(darkTheme = isDarkTheme) {
                when (lockState) {
                    LockState.Locked -> AppLockScreen(biometricAuthManager = biometricAuthManager)
                    LockState.Unlocked -> AppNavGraph()
                }
            }
        }
    }
}

package com.kitewatch.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitewatch.app.navigation.MainNavHost
import com.kitewatch.app.navigation.OnboardingNavHost
import com.kitewatch.data.preferences.ThemePreferenceRepository
import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.feature.auth.AppLockScreen
import com.kitewatch.feature.onboarding.preferences.OnboardingPreferenceRepository
import com.kitewatch.infra.auth.BiometricAuthManager
import com.kitewatch.infra.auth.LockState
import com.kitewatch.infra.auth.SessionManager
import com.kitewatch.ui.theme.KiteWatchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var themePreferenceRepository: ThemePreferenceRepository

    @Inject
    lateinit var biometricAuthManager: BiometricAuthManager

    @Inject
    lateinit var onboardingPreferenceRepository: OnboardingPreferenceRepository

    @Inject
    lateinit var accountBindingRepository: AccountBindingRepository

    @Inject
    lateinit var sessionManager: SessionManager

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
            val isOnboardingComplete by onboardingPreferenceRepository.isOnboardingComplete
                .collectAsStateWithLifecycle(initialValue = false)

            // isBound() is a suspend fun — evaluate once at startup, then re-evaluated
            // on session expiry (SessionManager clears the binding and emits
            // reAuthRequiredFlow; we react by re-checking isBound so routing switches
            // back to OnboardingNavHost for the Zerodha re-login step).
            var isBound by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                isBound = accountBindingRepository.isBound()
                sessionManager.reAuthRequiredFlow.collect {
                    isBound = accountBindingRepository.isBound()
                }
            }

            KiteWatchTheme(darkTheme = isDarkTheme) {
                when (lockState) {
                    LockState.Locked -> AppLockScreen(biometricAuthManager = biometricAuthManager)
                    LockState.Unlocked -> {
                        if (!isOnboardingComplete || !isBound) {
                            OnboardingNavHost(
                                biometricAuthManager = biometricAuthManager,
                                onOnboardingComplete = { isBound = true },
                            )
                        } else {
                            MainNavHost(biometricAuthManager = biometricAuthManager)
                        }
                    }
                }
            }
        }
    }
}

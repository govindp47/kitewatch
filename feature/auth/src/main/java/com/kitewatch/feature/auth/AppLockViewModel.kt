package com.kitewatch.feature.auth

import androidx.lifecycle.ViewModel
import com.kitewatch.infra.auth.BiometricAuthManager
import com.kitewatch.infra.auth.LockState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Exposes [lockState] from [BiometricAuthManager] to the Compose UI.
 *
 * The ViewModel holds no coroutine jobs of its own — it is a thin bridge
 * between the singleton [BiometricAuthManager] (infra layer) and [AppLockScreen]
 * (feature layer).
 */
@HiltViewModel
class AppLockViewModel
    @Inject
    constructor(
        private val biometricAuthManager: BiometricAuthManager,
    ) : ViewModel() {
        val lockState: StateFlow<LockState> = biometricAuthManager.lockState

        fun onAuthenticationSuccess() {
            biometricAuthManager.onAuthenticationSuccess()
        }
    }

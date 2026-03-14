package com.kitewatch.infra.auth

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes process lifecycle events to enforce the app-lock timeout.
 *
 * Register this observer with [androidx.lifecycle.ProcessLifecycleOwner] in
 * `KiteWatchApplication.onCreate()`:
 * ```kotlin
 * ProcessLifecycleOwner.get().lifecycle.addObserver(appLockStateManager)
 * ```
 *
 * When the app transitions to background ([onStop]), the current timestamp is
 * recorded. When the app returns to foreground ([onStart]), the elapsed time is
 * compared against [lockTimeoutMinutes]. If the timeout has elapsed (or the app
 * was never authenticated), [BiometricAuthManager.setLocked] is called.
 */
@Singleton
class AppLockStateManager
    @Inject
    constructor(
        private val biometricAuthManager: BiometricAuthManager,
    ) : DefaultLifecycleObserver {
        private var backgroundedAt: Instant? = null

        override fun onStop(owner: LifecycleOwner) {
            backgroundedAt = Instant.now()
        }

        override fun onStart(owner: LifecycleOwner) {
            checkLockTimeout()
        }

        private fun checkLockTimeout() {
            val lastAuth = biometricAuthManager.lastAuthTime
            if (lastAuth == null) {
                // Never authenticated in this session → ensure locked
                biometricAuthManager.setLocked()
                return
            }

            val wentBackground = backgroundedAt ?: return
            val elapsed = Duration.between(wentBackground, Instant.now())
            if (elapsed.toMinutes() >= DEFAULT_LOCK_TIMEOUT_MINUTES) {
                biometricAuthManager.lockNow()
            }
        }

        companion object {
            const val DEFAULT_LOCK_TIMEOUT_MINUTES = 5L
        }
    }

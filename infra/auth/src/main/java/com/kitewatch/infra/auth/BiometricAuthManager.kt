package com.kitewatch.infra.auth

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import androidx.biometric.BiometricManager as AndroidBiometricManager

sealed interface LockState {
    data object Locked : LockState

    data object Unlocked : LockState
}

sealed interface BiometricCapability {
    data object Available : BiometricCapability

    data object NoHardware : BiometricCapability

    data object HardwareUnavailable : BiometricCapability

    data object NoneEnrolled : BiometricCapability

    data object Unknown : BiometricCapability
}

/**
 * Manages biometric authentication state and BiometricPrompt creation.
 *
 * Owns [lockState] which drives the app-lock gate in MainActivity. Authentication
 * success is recorded via [onAuthenticationSuccess]; the lock check on app-resume
 * is delegated to [AppLockStateManager].
 *
 * [authenticate] must be called from the main thread with a [FragmentActivity] reference.
 */
@Singleton
class BiometricAuthManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val _lockState = MutableStateFlow<LockState>(LockState.Locked)
        val lockState: StateFlow<LockState> = _lockState.asStateFlow()

        internal var lastAuthTime: Instant? = null
            private set

        fun canAuthenticate(): BiometricCapability {
            val manager = AndroidBiometricManager.from(context)
            val authenticators =
                AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG or
                    AndroidBiometricManager.Authenticators.DEVICE_CREDENTIAL
            return when (manager.canAuthenticate(authenticators)) {
                AndroidBiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.Available
                AndroidBiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.NoHardware
                AndroidBiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapability.HardwareUnavailable
                AndroidBiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NoneEnrolled
                else -> BiometricCapability.Unknown
            }
        }

        fun createPromptInfo(): BiometricPrompt.PromptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle("Unlock KiteWatch")
                .setSubtitle("Authenticate to access your portfolio")
                .setAllowedAuthenticators(
                    AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG or
                        AndroidBiometricManager.Authenticators.DEVICE_CREDENTIAL,
                ).build()

        /**
         * Shows the biometric prompt attached to [activity].
         *
         * [onPermanentFailure] is called when no further retries are possible (too many
         * attempts or hardware error). The caller should surface a fallback message.
         */
        fun authenticate(
            activity: FragmentActivity,
            onPermanentFailure: (String) -> Unit,
        ) {
            val executor = Executors.newSingleThreadExecutor()
            val prompt =
                BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onAuthenticationSuccess()
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            // USER_CANCELED / NEGATIVE_BUTTON are transient — ignore them
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                            ) {
                                onPermanentFailure(errString.toString())
                            }
                        }

                        override fun onAuthenticationFailed() {
                            // Individual failed attempt — BiometricPrompt handles retry UI
                        }
                    },
                )
            prompt.authenticate(createPromptInfo())
        }

        fun onAuthenticationSuccess() {
            lastAuthTime = Instant.now()
            _lockState.value = LockState.Unlocked
        }

        fun lockNow() {
            lastAuthTime = null
            _lockState.value = LockState.Locked
        }

        internal fun setLocked() {
            _lockState.value = LockState.Locked
        }
    }

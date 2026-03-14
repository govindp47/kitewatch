package com.kitewatch.feature.onboarding.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.kitewatch.infra.auth.BiometricAuthManager
import com.kitewatch.infra.auth.BiometricCapability

@Composable
internal fun BiometricSetupStep(
    biometricUnavailable: Boolean,
    biometricAuthManager: BiometricAuthManager,
    activity: FragmentActivity,
    callbacks: BiometricSetupCallbacks,
    modifier: Modifier = Modifier,
) {
    // Check capability eagerly when this step is shown
    LaunchedEffect(Unit) {
        val capability = biometricAuthManager.canAuthenticate()
        if (capability != BiometricCapability.Available) {
            callbacks.onBiometricUnavailable()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Secure Your Portfolio",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (biometricUnavailable) {
            Text(
                text =
                    "Biometric authentication is not available on this device. " +
                        "KiteWatch requires your device PIN or passcode to protect your financial data.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = callbacks.onPinSetup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use Device PIN")
            }
        } else {
            Text(
                text =
                    "KiteWatch requires biometric authentication to keep your " +
                        "financial data safe. Your fingerprint or face ID is never stored by the app.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    biometricAuthManager.authenticate(
                        activity = activity,
                        onPermanentFailure = { callbacks.onBiometricUnavailable() },
                    )
                    // Success is reported via lockState; caller observes and advances
                    callbacks.onBiometricSuccess()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Set Up Biometrics")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = callbacks.onPinSetup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use Device PIN Instead")
            }
        }
    }
}

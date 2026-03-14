package com.kitewatch.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitewatch.infra.auth.BiometricAuthManager
import com.kitewatch.infra.auth.LockState

/**
 * Full-screen lock gate shown whenever [LockState] is [LockState.Locked].
 *
 * On first composition the biometric prompt is shown automatically. If
 * authentication fails permanently (hardware error, too many attempts) a
 * "Use device PIN" fallback button is displayed so the user can retry.
 *
 * This composable requires the host [android.app.Activity] to be a
 * [FragmentActivity] (satisfied by [androidx.activity.ComponentActivity]).
 */
@Composable
fun AppLockScreen(
    biometricAuthManager: BiometricAuthManager,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? FragmentActivity
    var permanentFailureMessage by remember { mutableStateOf<String?>(null) }

    // Trigger the prompt whenever we enter a Locked state
    LaunchedEffect(lockState) {
        if (lockState == LockState.Locked && activity != null) {
            permanentFailureMessage = null
            biometricAuthManager.authenticate(activity) { errorMsg ->
                permanentFailureMessage = errorMsg
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "App locked",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "KiteWatch is locked",
                style = MaterialTheme.typography.titleLarge,
            )

            if (permanentFailureMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = permanentFailureMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        permanentFailureMessage = null
                        activity?.let {
                            biometricAuthManager.authenticate(it) { msg ->
                                permanentFailureMessage = msg
                            }
                        }
                    },
                ) {
                    Text("Use device PIN")
                }
            }
        }
    }
}

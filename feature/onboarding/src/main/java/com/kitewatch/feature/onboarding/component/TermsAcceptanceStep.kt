package com.kitewatch.feature.onboarding.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun TermsAcceptanceStep(
    termsChecked: Boolean,
    onCheckedChange: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Terms & Conditions",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                Text(
                    text = TERMS_AND_CONDITIONS_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = termsChecked,
                onCheckedChange = { onCheckedChange() },
            )
            Text(
                text = "I have read and agree to the Terms & Conditions and Privacy Policy.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onContinue,
            enabled = termsChecked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}

private const val TERMS_AND_CONDITIONS_TEXT = """
KiteWatch Terms & Conditions

1. LOCAL-ONLY DATA STORAGE
All financial data managed by KiteWatch is stored exclusively on your device. No personal or financial data is transmitted to any server operated by the app developer.

2. AUTHORISED DATA CONNECTIONS
KiteWatch connects to the following external services on your behalf:
  • Zerodha Kite Connect API — for fetching orders, holdings, fund balance, charge rates, and managing GTT orders.
  • Gmail API (optional) — read-only access, scoped strictly to user-defined filters for fund deposit/withdrawal detection.
  • Google Drive (optional) — for backup and restore using your own Google Drive account.

3. ZERODHA API USAGE
KiteWatch uses the Zerodha Kite Connect Developer API (free tier). You are responsible for obtaining and maintaining valid API credentials. KiteWatch does not place buy orders on your behalf. GTT sell orders are placed only under your configured profit targets.

4. DATA ACCURACY
KiteWatch calculates P&L and charges locally. These calculations are for informational purposes only and do not constitute financial advice. Calculations may differ from those in Zerodha's official statements.

5. NO WARRANTY
KiteWatch is provided "as is" without warranty of any kind. The app developer is not liable for any financial decisions made based on information displayed in the app.

6. PRIVACY
Your data remains on your device and in services you explicitly authorise (Google Drive). The developer has no access to your financial data. See the in-app Privacy & Security page for full details.

7. BIOMETRIC LOCK
KiteWatch requires biometric or device PIN authentication to protect your financial data. This requirement cannot be disabled.

By continuing, you confirm that you have read and agreed to these terms.
"""

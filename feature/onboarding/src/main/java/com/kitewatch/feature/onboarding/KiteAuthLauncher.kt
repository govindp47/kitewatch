package com.kitewatch.feature.onboarding

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens the Zerodha Kite Connect OAuth login page in a Chrome Custom Tab.
 *
 * The Custom Tab keeps the user within the app's task stack. After the user
 * authenticates, Zerodha redirects to `kitewatch://callback?request_token=…`,
 * which is handled by [DeepLinkHandlerActivity].
 *
 * @param context  An Android [Context] used to launch the Custom Tab.
 * @param apiKey   The registered Kite Connect API key.
 */
object KiteAuthLauncher {
    private const val LOGIN_BASE_URL = "https://kite.zerodha.com/connect/login"
    private const val API_VERSION = "3"

    fun launch(
        context: Context,
        apiKey: String,
    ) {
        val loginUri =
            Uri
                .parse(LOGIN_BASE_URL)
                .buildUpon()
                .appendQueryParameter("api_key", apiKey)
                .appendQueryParameter("v", API_VERSION)
                .build()

        CustomTabsIntent
            .Builder()
            .build()
            .launchUrl(context, loginUri)
    }
}

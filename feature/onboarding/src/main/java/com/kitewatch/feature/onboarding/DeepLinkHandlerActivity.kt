package com.kitewatch.feature.onboarding

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.kitewatch.domain.repository.TokenStore
import com.kitewatch.domain.usecase.auth.BindAccountUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Invisible trampoline activity that handles the OAuth deep-link redirect from Kite Connect.
 *
 * Intent filter (see [AndroidManifest.xml]):
 *   scheme = "kitewatch", host = "callback"
 *   e.g. `kitewatch://callback?request_token=<token>&action=login&type=login&status=success`
 *
 * On receipt:
 *   1. Extracts `request_token` from [android.content.Intent.getData].
 *   2. Reads `api_key` from [TokenStore].
 *   3. Calls [BindAccountUseCase] to exchange the token and persist the session.
 *   4. Calls [finish] — the Custom Tab is dismissed and the previous screen resumes.
 *
 * The activity has no UI (`android:theme="@android:style/Theme.NoDisplay"`).
 */
@AndroidEntryPoint
class DeepLinkHandlerActivity : androidx.activity.ComponentActivity() {
    @Inject lateinit var bindAccountUseCase: BindAccountUseCase

    @Inject lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestToken = intent?.data?.getQueryParameter("request_token")
        val apiKey = tokenStore.getApiKey()

        if (requestToken == null || apiKey == null) {
            finish()
            return
        }

        val apiSecret = BuildConfig.KITE_API_SECRET

        lifecycleScope.launch {
            bindAccountUseCase.execute(
                apiKey = apiKey,
                requestToken = requestToken,
                apiSecret = apiSecret,
            )
            finish()
        }
    }
}

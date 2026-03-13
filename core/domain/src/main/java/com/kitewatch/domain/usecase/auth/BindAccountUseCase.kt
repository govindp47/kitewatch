package com.kitewatch.domain.usecase.auth

import com.kitewatch.domain.model.AccountBinding
import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.TokenStore
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

/**
 * Exchanges a Kite Connect OAuth [requestToken] for an access token and binds
 * the account to this installation.
 *
 * Steps:
 *  1. Compute SHA-256 checksum of "{apiKey}{requestToken}{apiSecret}".
 *  2. POST /session/token to receive [SessionCredentials].
 *  3. Persist the access token via [TokenStore].
 *  4. Persist the [AccountBinding] (BR-01: fails silently if already bound — returns false).
 *
 * Constructor injection is wired by the app-level Hilt module, not by this class,
 * because [core:domain] is a pure JVM module with no Hilt dependency.
 *
 * @param apiKey    Zerodha API key (read from [TokenStore] at call time by the caller).
 * @param requestToken One-time token received via the deep-link callback.
 * @param apiSecret API secret from BuildConfig.KITE_API_SECRET (injected by caller).
 */
class BindAccountUseCase(
    private val kiteConnectRepository: KiteConnectRepository,
    private val accountBindingRepository: AccountBindingRepository,
    private val tokenStore: TokenStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun execute(
        apiKey: String,
        requestToken: String,
        apiSecret: String,
    ): Result<AccountBinding> {
        val checksum = sha256("$apiKey$requestToken$apiSecret")

        return kiteConnectRepository
            .generateSession(apiKey, requestToken, checksum)
            .mapCatching { session ->
                tokenStore.saveAccessToken(session.accessToken)

                val binding =
                    AccountBinding(
                        userId = session.userId,
                        userName = session.userName,
                        apiKey = apiKey,
                        boundAt = Instant.now(clock),
                    )
                accountBindingRepository.bind(binding)
                binding
            }
    }

    private fun sha256(input: String): String {
        val bytes =
            MessageDigest
                .getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

package com.kitewatch.infra.auth

import android.content.SharedPreferences
import com.kitewatch.domain.repository.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single, security-hardened store for all runtime secrets.
 *
 * The backing [SharedPreferences] instance MUST be an [androidx.security.crypto.EncryptedSharedPreferences]
 * provided by [di.AuthInfraModule]. No other class may call [SharedPreferences.edit] on any
 * preferences file that holds sensitive data.
 *
 * Implements [TokenStore] so that [com.kitewatch.domain.usecase.auth.BindAccountUseCase]
 * can save the access token without depending on this infra class directly.
 *
 * Key constants are package-internal by convention. The [com.kitewatch.network.kiteconnect.interceptor.KiteConnectAuthInterceptor]
 * reads the same file using the same key names ([KEY_API_KEY], [KEY_ACCESS_TOKEN]).
 */
@Singleton
class CredentialStore
    @Inject
    constructor(
        private val encryptedPrefs: SharedPreferences,
    ) : TokenStore {
        // -------------------------------------------------------------------------
        // Kite Connect session
        // -------------------------------------------------------------------------

        override fun saveAccessToken(token: String) = encryptedPrefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()

        override fun getAccessToken(): String? = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

        fun clearAccessToken() = encryptedPrefs.edit().remove(KEY_ACCESS_TOKEN).apply()

        // -------------------------------------------------------------------------
        // Kite Connect API key (entered by user during onboarding)
        // -------------------------------------------------------------------------

        fun saveApiKey(key: String) = encryptedPrefs.edit().putString(KEY_API_KEY, key).apply()

        override fun getApiKey(): String? = encryptedPrefs.getString(KEY_API_KEY, null)

        // -------------------------------------------------------------------------
        // Kite Connect user identity
        // -------------------------------------------------------------------------

        fun saveUserId(id: String) = encryptedPrefs.edit().putString(KEY_USER_ID, id).apply()

        fun getUserId(): String? = encryptedPrefs.getString(KEY_USER_ID, null)

        // -------------------------------------------------------------------------
        // Google OAuth (managed by Google Play Services — stored here for retrieval)
        // -------------------------------------------------------------------------

        fun saveGoogleOAuthToken(token: String) = encryptedPrefs.edit().putString(KEY_GOOGLE_OAUTH_TOKEN, token).apply()

        fun getGoogleOAuthToken(): String? = encryptedPrefs.getString(KEY_GOOGLE_OAUTH_TOKEN, null)

        // -------------------------------------------------------------------------
        // Account reset — wipes every entry in the encrypted preferences file
        // -------------------------------------------------------------------------

        fun clearAll() = encryptedPrefs.edit().clear().apply()

        companion object {
            /**
             * Key names must remain stable: [com.kitewatch.network.kiteconnect.interceptor.KiteConnectAuthInterceptor]
             * reads [KEY_API_KEY] and [KEY_ACCESS_TOKEN] from the same EncryptedSharedPreferences file.
             */
            const val KEY_ACCESS_TOKEN = "kite_access_token"
            const val KEY_API_KEY = "kite_api_key"
            const val KEY_USER_ID = "kite_user_id"
            const val KEY_GOOGLE_OAUTH_TOKEN = "google_oauth_token"

            internal const val PREFS_FILE_NAME = "kitewatch_secrets"
        }
    }

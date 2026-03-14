package com.kitewatch.domain.repository

/**
 * Domain-layer contract for reading and writing session credentials.
 *
 * Implemented by [com.kitewatch.infra.auth.CredentialStore] which backs
 * it with [androidx.security.crypto.EncryptedSharedPreferences].
 *
 * Exists as an interface so that [com.kitewatch.domain.usecase.auth.BindAccountUseCase]
 * can persist the access token without depending on the infra layer.
 */
interface TokenStore {
    fun saveAccessToken(token: String)

    fun getAccessToken(): String?

    fun getApiKey(): String?
}

package com.kitewatch.infra.auth.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.domain.repository.TokenStore
import com.kitewatch.infra.auth.AccountBindingStore
import com.kitewatch.infra.auth.CredentialStore
import com.kitewatch.infra.auth.MasterKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides [CredentialStore] and its backing [EncryptedSharedPreferences] as the
 * unqualified [SharedPreferences] singleton.
 *
 * The unqualified [SharedPreferences] binding is consumed by:
 *   - [CredentialStore] (via constructor injection)
 *   - [com.kitewatch.network.kiteconnect.interceptor.KiteConnectAuthInterceptor] (reads
 *     access_token and api_key at request time)
 *
 * No other module may declare a competing unqualified [SharedPreferences] binding.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthInfraModule {
    @Singleton
    @Provides
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context,
        masterKeyProvider: MasterKeyProvider,
    ): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            CredentialStore.PREFS_FILE_NAME,
            masterKeyProvider.masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    @Singleton
    @Provides
    fun provideCredentialStore(encryptedPrefs: SharedPreferences): CredentialStore = CredentialStore(encryptedPrefs)

    /**
     * Binds [CredentialStore] as the [TokenStore] consumed by
     * [com.kitewatch.domain.usecase.auth.BindAccountUseCase].
     */
    @Singleton
    @Provides
    fun provideTokenStore(credentialStore: CredentialStore): TokenStore = credentialStore

    @Singleton
    @Provides
    fun provideAccountBindingRepository(store: AccountBindingStore): AccountBindingRepository = store
}

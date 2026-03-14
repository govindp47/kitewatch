package com.kitewatch.network.di

import android.content.SharedPreferences
import com.kitewatch.domain.event.SessionExpiredEvent
import com.kitewatch.network.kiteconnect.KiteConnectApiService
import com.kitewatch.network.kiteconnect.adapter.ApiResultAdapterFactory
import com.kitewatch.network.kiteconnect.interceptor.KiteConnectAuthInterceptor
import com.kitewatch.network.kiteconnect.interceptor.KiteConnectRateLimitInterceptor
import com.kitewatch.network.kiteconnect.interceptor.TokenExpiredInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Provides the OkHttp client, interceptors, Moshi, and [KiteConnectApiService].
 *
 * The [SharedPreferences] binding (EncryptedSharedPreferences) must be provided by
 * SecurityModule in :infra-auth or :app — it is injected unqualified here.
 *
 * Interceptor order:
 *   1. [KiteConnectRateLimitInterceptor] — enforces ≥ 100 ms gap before each request
 *   2. [KiteConnectAuthInterceptor]      — injects Authorization header
 *   3. [TokenExpiredInterceptor]         — emits [SessionExpiredEvent] on 401/403
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val KITE_BASE_URL = "https://api.kite.trade/"
    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 60L

    @Singleton
    @Provides
    fun provideSessionExpiredFlow(): MutableSharedFlow<SessionExpiredEvent> = MutableSharedFlow(extraBufferCapacity = 1)

    @Singleton
    @Provides
    fun provideKiteConnectAuthInterceptor(encryptedPrefs: SharedPreferences): KiteConnectAuthInterceptor =
        KiteConnectAuthInterceptor(encryptedPrefs)

    @Singleton
    @Provides
    fun provideTokenExpiredInterceptor(
        sessionExpiredFlow: MutableSharedFlow<SessionExpiredEvent>,
    ): TokenExpiredInterceptor = TokenExpiredInterceptor(sessionExpiredFlow)

    @Singleton
    @Provides
    fun provideRateLimitInterceptor(): KiteConnectRateLimitInterceptor = KiteConnectRateLimitInterceptor()

    @Singleton
    @Provides
    fun provideOkHttpClient(
        rateLimitInterceptor: KiteConnectRateLimitInterceptor,
        authInterceptor: KiteConnectAuthInterceptor,
        tokenExpiredInterceptor: TokenExpiredInterceptor,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(tokenExpiredInterceptor)
            .build()

    @Singleton
    @Provides
    fun provideMoshi(): Moshi =
        Moshi
            .Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

    @Singleton
    @Provides
    fun provideKiteConnectApiService(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): KiteConnectApiService =
        Retrofit
            .Builder()
            .baseUrl(KITE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(ApiResultAdapterFactory())
            .build()
            .create(KiteConnectApiService::class.java)
}

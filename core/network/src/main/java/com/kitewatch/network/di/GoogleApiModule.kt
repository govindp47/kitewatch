package com.kitewatch.network.di

import com.kitewatch.network.drive.GoogleDriveApiClient
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module providing Google Drive API infrastructure.
 *
 * A dedicated [OkHttpClient] and [Retrofit] instance are used for Drive — they
 * must NOT share the Kite Connect client, which carries Kite-specific interceptors
 * (rate-limiter, Kite auth header, token-expiry listener).
 *
 * The Drive OAuth token is injected per-request by [GoogleDriveRemoteDataSource]
 * directly from EncryptedSharedPreferences; no OkHttp interceptor is needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object GoogleApiModule {
    private const val GOOGLE_API_BASE_URL = "https://www.googleapis.com/"
    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 120L // Drive uploads can be slow

    @Singleton
    @Provides
    @Named("google")
    fun provideGoogleOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

    @Singleton
    @Provides
    @Named("google")
    fun provideGoogleRetrofit(
        @Named("google") okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(GOOGLE_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Singleton
    @Provides
    fun provideGoogleDriveApiClient(
        @Named("google") retrofit: Retrofit,
    ): GoogleDriveApiClient = retrofit.create(GoogleDriveApiClient::class.java)
}

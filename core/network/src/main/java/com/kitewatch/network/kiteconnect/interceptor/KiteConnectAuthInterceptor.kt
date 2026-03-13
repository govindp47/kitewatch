package com.kitewatch.network.kiteconnect.interceptor

import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Injects the Kite Connect authorization header on every outgoing request.
 *
 * Header format: `Authorization: token {api_key}:{access_token}`
 *
 * If either credential is absent the request is forwarded without the header;
 * the [TokenExpiredInterceptor] will observe the 401/403 response downstream.
 */
class KiteConnectAuthInterceptor
    @Inject
    constructor(
        private val encryptedPrefs: SharedPreferences,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val apiKey = encryptedPrefs.getString(KEY_API_KEY, null)
            val accessToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

            val request =
                if (apiKey != null && accessToken != null) {
                    chain
                        .request()
                        .newBuilder()
                        .header(HEADER_AUTHORIZATION, "token $apiKey:$accessToken")
                        .build()
                } else {
                    chain.request()
                }

            return chain.proceed(request)
        }

        companion object {
            const val KEY_API_KEY = "kite_api_key"
            const val KEY_ACCESS_TOKEN = "kite_access_token"
            private const val HEADER_AUTHORIZATION = "Authorization"
        }
    }

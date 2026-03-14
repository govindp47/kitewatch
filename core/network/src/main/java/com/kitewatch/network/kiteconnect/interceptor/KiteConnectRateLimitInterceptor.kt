package com.kitewatch.network.kiteconnect.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Enforces a minimum [MIN_DELAY_MS] gap between consecutive Kite API calls.
 *
 * Uses [Thread.sleep] (acceptable on OkHttp's IO thread pool) to back-pressure
 * rapid callers. A 429 response is forwarded to the
 * [com.kitewatch.network.kiteconnect.adapter.ApiResultAdapterFactory] which maps
 * it to [com.kitewatch.domain.error.AppError.NetworkError.RateLimited] — the
 * request is NOT retried.
 */
class KiteConnectRateLimitInterceptor
    @Inject
    constructor() : Interceptor {
        private val lastRequestTimeMs = AtomicLong(0L)

        override fun intercept(chain: Interceptor.Chain): Response {
            enforceMinDelay()
            return chain.proceed(chain.request())
        }

        private fun enforceMinDelay() {
            val now = System.currentTimeMillis()
            val last = lastRequestTimeMs.get()

            if (last != 0L) {
                val elapsed = now - last
                if (elapsed < MIN_DELAY_MS) {
                    Thread.sleep(MIN_DELAY_MS - elapsed)
                }
            }

            lastRequestTimeMs.set(System.currentTimeMillis())
        }

        companion object {
            const val MIN_DELAY_MS = 100L
        }
    }

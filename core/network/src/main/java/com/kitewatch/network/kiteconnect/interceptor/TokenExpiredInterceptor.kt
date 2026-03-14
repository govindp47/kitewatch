package com.kitewatch.network.kiteconnect.interceptor

import com.kitewatch.domain.event.SessionExpiredEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Intercepts HTTP 401/403 responses and emits [SessionExpiredEvent] via [sessionExpiredFlow].
 *
 * The response is NOT retried — it is forwarded to the caller as-is so that
 * the [com.kitewatch.network.kiteconnect.adapter.ApiResultAdapterFactory] can map it
 * to the appropriate [com.kitewatch.domain.error.AppError].
 */
class TokenExpiredInterceptor
    @Inject
    constructor(
        private val sessionExpiredFlow: MutableSharedFlow<SessionExpiredEvent>,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())

            if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
                // tryEmit is non-suspending; the SharedFlow must have extraBufferCapacity >= 1.
                sessionExpiredFlow.tryEmit(SessionExpiredEvent)
            }

            return response
        }

        companion object {
            private const val HTTP_UNAUTHORIZED = 401
            private const val HTTP_FORBIDDEN = 403
        }
    }

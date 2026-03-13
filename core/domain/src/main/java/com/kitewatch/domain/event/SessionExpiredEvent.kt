package com.kitewatch.domain.event

/**
 * Emitted by [com.kitewatch.network.kiteconnect.interceptor.TokenExpiredInterceptor]
 * when the Kite API returns HTTP 401 or 403.
 *
 * Observed by [com.kitewatch.infra.auth.SessionManager] which clears the stored
 * access token and emits [ReAuthRequiredEvent] to trigger the re-authentication flow.
 */
data object SessionExpiredEvent

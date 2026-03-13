package com.kitewatch.domain.event

/**
 * Emitted by [com.kitewatch.infra.auth.SessionManager] after it clears the
 * access token in response to a [SessionExpiredEvent].
 *
 * UI observers should navigate the user to the onboarding / re-authentication screen.
 */
data object ReAuthRequiredEvent

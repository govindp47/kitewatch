package com.kitewatch.domain.model

/**
 * Result of a successful POST /session/token exchange.
 * Carries only the fields needed by [com.kitewatch.domain.usecase.auth.BindAccountUseCase].
 */
data class SessionCredentials(
    val accessToken: String,
    val userId: String,
    val userName: String,
)

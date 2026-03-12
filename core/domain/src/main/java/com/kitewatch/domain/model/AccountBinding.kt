package com.kitewatch.domain.model

import java.time.Instant

data class AccountBinding(
    val userId: String,
    val userName: String,
    val apiKey: String,
    val boundAt: Instant,
)

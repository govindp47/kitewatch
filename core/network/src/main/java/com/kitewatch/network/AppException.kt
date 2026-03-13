package com.kitewatch.network

import com.kitewatch.domain.error.AppError

/**
 * Wraps an [AppError] as a [Throwable] so it can be carried in [kotlin.Result].
 */
class AppException(
    val error: AppError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

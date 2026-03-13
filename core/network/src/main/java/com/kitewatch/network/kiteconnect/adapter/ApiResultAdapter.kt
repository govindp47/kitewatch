package com.kitewatch.network.kiteconnect.adapter

import com.kitewatch.domain.error.AppError
import com.kitewatch.network.AppException
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.SocketTimeoutException

/**
 * Retrofit [CallAdapter.Factory] that converts [Call]<T> to [Result]<T>.
 *
 * Service method return type must be declared as `Result<T>`:
 * ```
 * @GET("orders")
 * fun getOrders(): Result<List<OrderDto>>
 * ```
 *
 * HTTP error codes and [IOException]s are mapped to [AppException] wrapping
 * the appropriate [AppError] subtype.
 */
class ApiResultAdapterFactory : CallAdapter.Factory() {
    override fun get(
        returnType: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): CallAdapter<*, *>? {
        if (getRawType(returnType) != Result::class.java) return null
        check(returnType is ParameterizedType) {
            "Result return type must be parameterized as Result<Foo> or Result<out Foo>"
        }
        val bodyType = getParameterUpperBound(0, returnType)
        return ApiResultAdapter<Any>(bodyType)
    }
}

internal class ApiResultAdapter<T>(
    private val bodyType: Type,
) : CallAdapter<T, Result<T>> {
    override fun responseType(): Type = bodyType

    override fun adapt(call: Call<T>): Result<T> =
        try {
            val response = call.execute()
            mapResponse(response)
        } catch (e: SocketTimeoutException) {
            Result.failure(AppException(AppError.NetworkError.Timeout, e))
        } catch (e: IOException) {
            Result.failure(AppException(AppError.NetworkError.NoConnection, e))
        }

    private fun mapResponse(response: Response<T>): Result<T> {
        if (!response.isSuccessful) {
            return Result.failure(AppException(mapHttpError(response.code(), response.message())))
        }
        val body = response.body()
        return if (body != null) {
            Result.success(body)
        } else {
            Result.failure(AppException(AppError.NetworkError.HttpError(response.code(), "Empty response body")))
        }
    }
}

/**
 * Async variant wrapping a [Call]<T> as [Call]<[Result]<T>> for enqueue-based usage
 * (e.g., when composed with Retrofit's coroutine machinery).
 */
internal class ResultCall<T>(
    private val delegate: Call<T>,
) : Call<Result<T>> {
    override fun enqueue(callback: Callback<Result<T>>) {
        delegate.enqueue(
            object : Callback<T> {
                override fun onResponse(
                    call: Call<T>,
                    response: Response<T>,
                ) {
                    val result: Result<T> =
                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body != null) {
                                Result.success(body)
                            } else {
                                Result.failure(
                                    AppException(
                                        AppError.NetworkError.HttpError(response.code(), "Empty response body"),
                                    ),
                                )
                            }
                        } else {
                            Result.failure(AppException(mapHttpError(response.code(), response.message())))
                        }
                    callback.onResponse(this@ResultCall, Response.success(result))
                }

                override fun onFailure(
                    call: Call<T>,
                    t: Throwable,
                ) {
                    val error =
                        when (t) {
                            is SocketTimeoutException -> AppError.NetworkError.Timeout
                            is IOException -> AppError.NetworkError.NoConnection
                            else -> AppError.NetworkError.Unexpected(t)
                        }
                    callback.onResponse(this@ResultCall, Response.success(Result.failure(AppException(error))))
                }
            },
        )
    }

    override fun execute(): Response<Result<T>> =
        throw UnsupportedOperationException("Use adapt() for synchronous execution")

    override fun isExecuted(): Boolean = delegate.isExecuted

    override fun cancel() = delegate.cancel()

    override fun isCanceled(): Boolean = delegate.isCanceled

    override fun clone(): Call<Result<T>> = ResultCall(delegate.clone())

    override fun request(): Request = delegate.request()

    override fun timeout(): Timeout = delegate.timeout()
}

private fun mapHttpError(
    code: Int,
    message: String,
): AppError =
    when (code) {
        401, 403 -> AppError.AuthError.TokenExpired
        429 -> AppError.NetworkError.RateLimited
        else -> AppError.NetworkError.HttpError(code, message)
    }

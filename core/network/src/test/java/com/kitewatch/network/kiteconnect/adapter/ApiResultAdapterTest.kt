package com.kitewatch.network.kiteconnect.adapter

import com.kitewatch.domain.error.AppError
import com.kitewatch.network.AppException
import io.mockk.every
import io.mockk.mockk
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Call
import retrofit2.Response
import java.net.SocketTimeoutException

/**
 * Tests [ApiResultAdapter] and [ResultCall] mapping logic directly via MockK.
 *
 * Note: [kotlin.Result] is a value class erased to [Object] on the JVM, so Retrofit's type
 * system cannot detect it for non-suspend service methods. The adapter is designed for
 * contexts where the type IS inspectable (e.g., via direct instantiation or future
 * Retrofit support). Logic is verified here without a Retrofit proxy.
 */
class ApiResultAdapterTest {
    private val mockDelegate: Call<TestBody> = mockk()

    // --- ApiResultAdapter (synchronous path via adapt/execute) ---

    @Test
    fun `HTTP 200 with body returns Result success`() {
        val adapter = ApiResultAdapter<TestBody>(TestBody::class.java)
        every { mockDelegate.execute() } returns Response.success(TestBody("hello"))

        val result = adapter.adapt(mockDelegate)

        assertTrue(result.isSuccess)
        assertEquals("hello", result.getOrNull()?.value)
    }

    @Test
    fun `HTTP 200 with null body returns Result failure with HttpError`() {
        val adapter = ApiResultAdapter<TestBody>(TestBody::class.java)
        @Suppress("UNCHECKED_CAST")
        every { mockDelegate.execute() } returns Response.success(null as TestBody?)

        val result = adapter.adapt(mockDelegate)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as AppException).error
        assertTrue(error is AppError.NetworkError.HttpError)
    }

    @Test
    fun `HTTP 500 returns Result failure with HttpError`() {
        val adapter = ApiResultAdapter<TestBody>(TestBody::class.java)
        every { mockDelegate.execute() } returns errorResponse(500)

        val result = adapter.adapt(mockDelegate)

        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as AppException).error
        assertTrue(error is AppError.NetworkError.HttpError)
        assertEquals(500, (error as AppError.NetworkError.HttpError).code)
    }

    @Test
    fun `HTTP 429 returns Result failure with RateLimited`() {
        val adapter = ApiResultAdapter<TestBody>(TestBody::class.java)
        every { mockDelegate.execute() } returns errorResponse(429)

        val result = adapter.adapt(mockDelegate)

        assertTrue(result.isFailure)
        assertEquals(AppError.NetworkError.RateLimited, (result.exceptionOrNull() as AppException).error)
    }

    @Test
    fun `HTTP 401 returns Result failure with TokenExpired`() {
        val adapter = ApiResultAdapter<TestBody>(TestBody::class.java)
        every { mockDelegate.execute() } returns errorResponse(401)

        val result = adapter.adapt(mockDelegate)

        assertTrue(result.isFailure)
        assertEquals(AppError.AuthError.TokenExpired, (result.exceptionOrNull() as AppException).error)
    }

    @Test
    fun `SocketTimeoutException returns Result failure with Timeout`() {
        val adapter = ApiResultAdapter<TestBody>(TestBody::class.java)
        every { mockDelegate.execute() } throws SocketTimeoutException()

        val result = adapter.adapt(mockDelegate)

        assertTrue(result.isFailure)
        assertEquals(AppError.NetworkError.Timeout, (result.exceptionOrNull() as AppException).error)
    }

    @Test
    fun `IOException returns Result failure with NoConnection`() {
        val adapter = ApiResultAdapter<TestBody>(TestBody::class.java)
        every { mockDelegate.execute() } throws java.io.IOException("Network unavailable")

        val result = adapter.adapt(mockDelegate)

        assertTrue(result.isFailure)
        assertEquals(AppError.NetworkError.NoConnection, (result.exceptionOrNull() as AppException).error)
    }

    // --- ResultCall (async enqueue path) ---

    @Test
    fun `ResultCall enqueue HTTP 200 delivers Result success`() {
        val resultCall = ResultCall(mockDelegate)
        var captured: Result<TestBody>? = null

        every { mockDelegate.enqueue(any()) } answers {
            firstArg<retrofit2.Callback<TestBody>>().onResponse(
                mockDelegate,
                Response.success(TestBody("world")),
            )
        }

        resultCall.enqueue(
            object : retrofit2.Callback<Result<TestBody>> {
                override fun onResponse(
                    call: Call<Result<TestBody>>,
                    response: Response<Result<TestBody>>,
                ) {
                    captured = response.body()
                }

                override fun onFailure(
                    call: Call<Result<TestBody>>,
                    t: Throwable,
                ) {}
            },
        )

        assertTrue(captured!!.isSuccess)
        assertEquals("world", captured!!.getOrNull()?.value)
    }

    @Test
    fun `ResultCall enqueue HTTP 403 delivers Result failure with TokenExpired`() {
        val resultCall = ResultCall(mockDelegate)
        var captured: Result<TestBody>? = null

        every { mockDelegate.enqueue(any()) } answers {
            firstArg<retrofit2.Callback<TestBody>>().onResponse(
                mockDelegate,
                errorResponse(403),
            )
        }

        resultCall.enqueue(
            object : retrofit2.Callback<Result<TestBody>> {
                override fun onResponse(
                    call: Call<Result<TestBody>>,
                    response: Response<Result<TestBody>>,
                ) {
                    captured = response.body()
                }

                override fun onFailure(
                    call: Call<Result<TestBody>>,
                    t: Throwable,
                ) {}
            },
        )

        assertTrue(captured!!.isFailure)
        assertEquals(AppError.AuthError.TokenExpired, (captured!!.exceptionOrNull() as AppException).error)
    }

    @Test
    fun `ResultCall enqueue network failure delivers Result failure with NoConnection`() {
        val resultCall = ResultCall(mockDelegate)
        var captured: Result<TestBody>? = null

        every { mockDelegate.enqueue(any()) } answers {
            firstArg<retrofit2.Callback<TestBody>>().onFailure(
                mockDelegate,
                java.io.IOException("Connection refused"),
            )
        }

        resultCall.enqueue(
            object : retrofit2.Callback<Result<TestBody>> {
                override fun onResponse(
                    call: Call<Result<TestBody>>,
                    response: Response<Result<TestBody>>,
                ) {
                    captured = response.body()
                }

                override fun onFailure(
                    call: Call<Result<TestBody>>,
                    t: Throwable,
                ) {}
            },
        )

        assertTrue(captured!!.isFailure)
        assertEquals(AppError.NetworkError.NoConnection, (captured!!.exceptionOrNull() as AppException).error)
    }

    // --- Helpers ---

    private fun errorResponse(code: Int): Response<TestBody> = Response.error(code, "".toResponseBody(null))
}

data class TestBody(
    val value: String,
)

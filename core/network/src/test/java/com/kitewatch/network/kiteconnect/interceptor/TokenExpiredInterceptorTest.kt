package com.kitewatch.network.kiteconnect.interceptor

import com.kitewatch.domain.event.SessionExpiredEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TokenExpiredInterceptorTest {
    private val mockWebServer = MockWebServer()

    // replay=1 makes the last emission available to late subscribers — required for
    // the test pattern where tryEmit is called before first() begins collecting.
    // Production usage (NetworkModule) uses extraBufferCapacity=1, replay=0 because
    // the auth ViewModel subscribes at startup before any 403 can occur.
    private lateinit var sessionExpiredFlow: MutableSharedFlow<SessionExpiredEvent>
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer.start()
        sessionExpiredFlow = MutableSharedFlow(replay = 1)
        val interceptor = TokenExpiredInterceptor(sessionExpiredFlow)
        client =
            OkHttpClient
                .Builder()
                .addInterceptor(interceptor)
                .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `HTTP 403 emits SessionExpiredEvent`() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(403))

            val request = Request.Builder().url(mockWebServer.url("/orders")).build()
            client.newCall(request).execute()

            val event = withTimeout(1_000L) { sessionExpiredFlow.first() }
            assertEquals(SessionExpiredEvent, event)
        }

    @Test
    fun `HTTP 401 emits SessionExpiredEvent`() =
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(401))

            val request = Request.Builder().url(mockWebServer.url("/session/token")).build()
            client.newCall(request).execute()

            val event = withTimeout(1_000L) { sessionExpiredFlow.first() }
            assertEquals(SessionExpiredEvent, event)
        }

    @Test
    fun `HTTP 403 does not throw — 403 response is forwarded to the caller`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(403))

        val request = Request.Builder().url(mockWebServer.url("/orders")).build()
        val response = client.newCall(request).execute()

        assertEquals(403, response.code)
    }

    @Test
    fun `HTTP 200 does not emit SessionExpiredEvent`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder().url(mockWebServer.url("/orders")).build()
        client.newCall(request).execute()

        assertEquals(0, sessionExpiredFlow.replayCache.size)
    }
}

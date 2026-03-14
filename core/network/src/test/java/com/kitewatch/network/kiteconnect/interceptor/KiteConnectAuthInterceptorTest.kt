package com.kitewatch.network.kiteconnect.interceptor

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class KiteConnectAuthInterceptorTest {
    private val mockWebServer = MockWebServer()
    private val mockPrefs: SharedPreferences = mockk()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer.start()
        val interceptor = KiteConnectAuthInterceptor(mockPrefs)
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
    fun `injects correct Authorization header when both credentials are present`() {
        every { mockPrefs.getString(KiteConnectAuthInterceptor.KEY_API_KEY, null) } returns "test_api_key"
        every { mockPrefs.getString(KiteConnectAuthInterceptor.KEY_ACCESS_TOKEN, null) } returns "test_access_token"

        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder().url(mockWebServer.url("/orders")).build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertEquals("token test_api_key:test_access_token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `omits Authorization header when api_key is null`() {
        every { mockPrefs.getString(KiteConnectAuthInterceptor.KEY_API_KEY, null) } returns null
        every { mockPrefs.getString(KiteConnectAuthInterceptor.KEY_ACCESS_TOKEN, null) } returns "test_token"

        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder().url(mockWebServer.url("/orders")).build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `omits Authorization header when access_token is null`() {
        every { mockPrefs.getString(KiteConnectAuthInterceptor.KEY_API_KEY, null) } returns "test_api_key"
        every { mockPrefs.getString(KiteConnectAuthInterceptor.KEY_ACCESS_TOKEN, null) } returns null

        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder().url(mockWebServer.url("/orders")).build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `response is forwarded even without Authorization header`() {
        every { mockPrefs.getString(KiteConnectAuthInterceptor.KEY_API_KEY, null) } returns null
        every { mockPrefs.getString(KiteConnectAuthInterceptor.KEY_ACCESS_TOKEN, null) } returns null

        mockWebServer.enqueue(MockResponse().setResponseCode(403))
        val request = Request.Builder().url(mockWebServer.url("/orders")).build()
        val response = client.newCall(request).execute()

        assertEquals(403, response.code)
    }
}

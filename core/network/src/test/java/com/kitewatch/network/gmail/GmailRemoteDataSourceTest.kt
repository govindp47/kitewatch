package com.kitewatch.network.gmail

import android.content.SharedPreferences
import com.kitewatch.network.gmail.dto.GmailBodyDto
import com.kitewatch.network.gmail.dto.GmailHeaderDto
import com.kitewatch.network.gmail.dto.GmailMessageDto
import com.kitewatch.network.gmail.dto.GmailMessageListDto
import com.kitewatch.network.gmail.dto.GmailMessageStubDto
import com.kitewatch.network.gmail.dto.GmailPayloadDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.LocalDate
import java.util.Base64

class GmailRemoteDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var apiClient: GmailApiClient
    private lateinit var dataSource: GmailRemoteDataSource
    private val encryptedPrefs: SharedPreferences =
        mockk {
            every { getString("google_oauth_token", "") } returns "test_token"
        }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listAdapter = moshi.adapter(GmailMessageListDto::class.java)
    private val messageAdapter = moshi.adapter(GmailMessageDto::class.java)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        apiClient =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient())
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GmailApiClient::class.java)

        dataSource = GmailRemoteDataSource(apiClient, encryptedPrefs)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun encode64(text: String) = Base64.getUrlEncoder().encodeToString(text.toByteArray())

    private fun fundMessage(
        id: String,
        amount: String = "Rs. 50,000.00",
    ): GmailMessageDto =
        GmailMessageDto(
            id = id,
            internalDateMs = "1700000000000",
            payload =
                GmailPayloadDto(
                    mimeType = "text/plain",
                    headers =
                        listOf(
                            GmailHeaderDto("Subject", "Funds added to Kite"),
                            GmailHeaderDto("From", "no-reply@zerodha.com"),
                        ),
                    body = GmailBodyDto(data = encode64("Dear Customer, $amount credited.")),
                ),
        )

    private fun nonFundMessage(id: String): GmailMessageDto =
        GmailMessageDto(
            id = id,
            internalDateMs = "1700000000000",
            payload =
                GmailPayloadDto(
                    mimeType = "text/plain",
                    headers =
                        listOf(
                            GmailHeaderDto("Subject", "Order executed on NSE"),
                            GmailHeaderDto("From", "no-reply@zerodha.com"),
                        ),
                    body = GmailBodyDto(data = encode64("Your order for INFY was executed.")),
                ),
        )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `list returns 2 IDs - 1 fund and 1 non-fund - result has size 1`() =
        runTest {
            // Enqueue list response with 2 message stubs
            val listResponse =
                GmailMessageListDto(
                    messages =
                        listOf(
                            GmailMessageStubDto(id = "msg001"),
                            GmailMessageStubDto(id = "msg002"),
                        ),
                )
            server.enqueue(MockResponse().setBody(listAdapter.toJson(listResponse)))

            // msg001 → valid fund credit
            server.enqueue(MockResponse().setBody(messageAdapter.toJson(fundMessage("msg001"))))
            // msg002 → non-fund email
            server.enqueue(MockResponse().setBody(messageAdapter.toJson(nonFundMessage("msg002"))))

            val results = dataSource.scanForFundCredits(since = LocalDate.of(2024, 1, 1))

            assertEquals(1, results.size)
            assertEquals("msg001", results[0].messageId)
        }

    @Test
    fun `already seen IDs are skipped without fetching`() =
        runTest {
            val listResponse =
                GmailMessageListDto(
                    messages =
                        listOf(
                            GmailMessageStubDto(id = "msg001"),
                            GmailMessageStubDto(id = "msg002"),
                        ),
                )
            server.enqueue(MockResponse().setBody(listAdapter.toJson(listResponse)))
            // Only msg002 is fetched — msg001 is in alreadySeenIds
            server.enqueue(MockResponse().setBody(messageAdapter.toJson(fundMessage("msg002"))))

            val results =
                dataSource.scanForFundCredits(
                    since = LocalDate.of(2024, 1, 1),
                    alreadySeenIds = setOf("msg001"),
                )

            assertEquals(1, results.size)
            assertEquals("msg002", results[0].messageId)
            // Only 2 requests were made: list + 1 getMessage (msg001 was skipped)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `empty list response returns empty result`() =
        runTest {
            val listResponse = GmailMessageListDto(messages = null)
            server.enqueue(MockResponse().setBody(listAdapter.toJson(listResponse)))

            val results = dataSource.scanForFundCredits(since = LocalDate.of(2024, 1, 1))

            assertEquals(0, results.size)
            assertEquals(1, server.requestCount) // only list call
        }

    @Test
    fun `getMessage failure is skipped gracefully`() =
        runTest {
            val listResponse =
                GmailMessageListDto(
                    messages =
                        listOf(
                            GmailMessageStubDto(id = "msg001"),
                            GmailMessageStubDto(id = "msg002"),
                        ),
                )
            server.enqueue(MockResponse().setBody(listAdapter.toJson(listResponse)))

            // msg001 → server error (skipped gracefully)
            server.enqueue(MockResponse().setResponseCode(500))
            // msg002 → valid
            server.enqueue(MockResponse().setBody(messageAdapter.toJson(fundMessage("msg002"))))

            val results = dataSource.scanForFundCredits(since = LocalDate.of(2024, 1, 1))

            assertEquals(1, results.size)
            assertEquals("msg002", results[0].messageId)
        }
}

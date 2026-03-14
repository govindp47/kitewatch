package com.kitewatch.data.repository

import com.kitewatch.network.kiteconnect.KiteConnectApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class KiteConnectRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var apiService: KiteConnectApiService
    private lateinit var repository: KiteConnectRepositoryImpl

    private val moshi =
        Moshi
            .Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        apiService = retrofit.create(KiteConnectApiService::class.java)
        repository = KiteConnectRepositoryImpl(apiService)
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `fetchTodaysOrders returns only CNC orders`() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """
                        {
                          "status": "success",
                          "data": [
                            {
                              "order_id": "111111111",
                              "tradingsymbol": "INFY",
                              "transaction_type": "BUY",
                              "quantity": 10,
                              "average_price": 1500.0,
                              "status": "COMPLETE",
                              "product": "CNC",
                              "exchange": "NSE",
                              "fill_timestamp": "2024-01-15 10:30:00"
                            },
                            {
                              "order_id": "222222222",
                              "tradingsymbol": "INFY",
                              "transaction_type": "BUY",
                              "quantity": 5,
                              "average_price": 1520.0,
                              "status": "COMPLETE",
                              "product": "MIS",
                              "exchange": "NSE",
                              "fill_timestamp": "2024-01-15 11:00:00"
                            },
                            {
                              "order_id": "333333333",
                              "tradingsymbol": "TCS",
                              "transaction_type": "SELL",
                              "quantity": 3,
                              "average_price": 3500.0,
                              "status": "COMPLETE",
                              "product": "NRML",
                              "exchange": "NSE",
                              "fill_timestamp": "2024-01-15 12:00:00"
                            }
                          ]
                        }
                        """.trimIndent(),
                    ).setResponseCode(200),
            )

            val result = repository.fetchTodaysOrders()
            assertTrue(result.isSuccess)
            val orders = result.getOrThrow()
            assertEquals(1, orders.size)
            assertEquals("111111111", orders[0].zerodhaOrderId)
            assertEquals("INFY", orders[0].stockCode)
        }

    @Test
    fun `fetchTodaysOrders returns empty list when no CNC orders`() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"status": "success", "data": [
                          {"order_id": "999", "tradingsymbol": "X", "transaction_type": "BUY",
                           "quantity": 1, "average_price": 100.0, "status": "COMPLETE",
                           "product": "MIS", "exchange": "NSE", "fill_timestamp": "2024-01-15 10:00:00"}
                        ]}""",
                    ).setResponseCode(200),
            )
            val result = repository.fetchTodaysOrders()
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isEmpty())
        }

    @Test
    fun `fetchTodaysOrders returns failure on API error status`() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setBody("""{"status": "error", "message": "Token expired", "data": null}""")
                    .setResponseCode(403),
            )
            val result = repository.fetchTodaysOrders()
            // HTTP 403 → response body is null or status != success
            assertTrue(result.isFailure)
        }

    @Test
    fun `fetchTodaysOrders excludes orders with missing required fields`() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """
                        {"status": "success", "data": [
                          {
                            "order_id": null,
                            "tradingsymbol": "INFY",
                            "transaction_type": "BUY",
                            "quantity": 10,
                            "average_price": 1500.0,
                            "status": "COMPLETE",
                            "product": "CNC",
                            "exchange": "NSE",
                            "fill_timestamp": "2024-01-15 10:30:00"
                          }
                        ]}
                        """.trimIndent(),
                    ).setResponseCode(200),
            )
            val result = repository.fetchTodaysOrders()
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isEmpty())
        }
}

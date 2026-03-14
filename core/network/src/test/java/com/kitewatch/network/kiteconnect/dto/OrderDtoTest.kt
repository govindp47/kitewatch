package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderDtoTest {
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `parses full order JSON correctly`() {
        val json =
            """
            {
              "order_id": "220101000000001",
              "tradingsymbol": "INFY",
              "transaction_type": "BUY",
              "quantity": 10,
              "average_price": 1500.50,
              "status": "COMPLETE",
              "product": "CNC",
              "exchange": "NSE",
              "fill_timestamp": "2022-01-01 15:30:00"
            }
            """.trimIndent()

        val adapter = moshi.adapter(OrderDto::class.java)
        val dto = adapter.fromJson(json)!!

        assertEquals("220101000000001", dto.orderId)
        assertEquals("INFY", dto.tradingSymbol)
        assertEquals("BUY", dto.transactionType)
        assertEquals(10, dto.quantity)
        assertEquals(1500.50, dto.averagePrice!!, 0.001)
        assertEquals("COMPLETE", dto.status)
        assertEquals("CNC", dto.product)
        assertEquals("NSE", dto.exchange)
        assertEquals("2022-01-01 15:30:00", dto.fillTimestamp)
    }

    @Test
    fun `missing fill_timestamp produces null without crash`() {
        val json =
            """
            {
              "order_id": "220101000000002",
              "tradingsymbol": "TCS",
              "transaction_type": "SELL",
              "quantity": 5,
              "average_price": 3200.00,
              "status": "OPEN",
              "product": "CNC",
              "exchange": "NSE"
            }
            """.trimIndent()

        val adapter = moshi.adapter(OrderDto::class.java)
        val dto = adapter.fromJson(json)!!

        assertNull(dto.fillTimestamp)
        assertEquals("220101000000002", dto.orderId)
    }

    @Test
    fun `parses list of orders`() {
        val json =
            """
            [
              {"order_id": "1", "tradingsymbol": "INFY"},
              {"order_id": "2", "tradingsymbol": "TCS"}
            ]
            """.trimIndent()

        val type = Types.newParameterizedType(List::class.java, OrderDto::class.java)
        val adapter = moshi.adapter<List<OrderDto>>(type)
        val list = adapter.fromJson(json)!!

        assertEquals(2, list.size)
        assertEquals("INFY", list[0].tradingSymbol)
        assertEquals("TCS", list[1].tradingSymbol)
    }
}

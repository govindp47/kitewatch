package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GttDtoTest {
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `parses full GTT JSON correctly`() {
        val json =
            """
            {
              "id": 12345,
              "type": "single",
              "status": "active",
              "condition": {
                "tradingsymbol": "INFY",
                "trigger_values": [1600.00],
                "exchange": "NSE"
              },
              "orders": [
                {
                  "quantity": 10,
                  "transaction_type": "SELL",
                  "product": "CNC",
                  "order_type": "LIMIT",
                  "price": 1600.00
                }
              ]
            }
            """.trimIndent()

        val dto = moshi.adapter(GttDto::class.java).fromJson(json)!!

        assertEquals(12345, dto.id)
        assertEquals("single", dto.type)
        assertEquals("active", dto.status)
        assertEquals("INFY", dto.condition!!.tradingSymbol)
        assertEquals(1600.0, dto.condition.triggerValues!![0], 0.001)
        assertEquals(1, dto.orders!!.size)
        assertEquals("SELL", dto.orders[0].transactionType)
        assertEquals(10, dto.orders[0].quantity)
    }

    @Test
    fun `missing optional fields produce null without crash`() {
        val json = """{"id": 999}"""

        val dto = moshi.adapter(GttDto::class.java).fromJson(json)!!

        assertEquals(999, dto.id)
        assertNull(dto.type)
        assertNull(dto.status)
        assertNull(dto.condition)
        assertNull(dto.orders)
    }
}

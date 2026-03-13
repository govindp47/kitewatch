package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HoldingDtoTest {
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `parses full holding JSON correctly`() {
        val json =
            """
            {
              "tradingsymbol": "INFY",
              "quantity": 100,
              "average_price": 1400.25,
              "t1_quantity": 5,
              "exchange": "NSE",
              "isin": "INE009A01021"
            }
            """.trimIndent()

        val dto = moshi.adapter(HoldingDto::class.java).fromJson(json)!!

        assertEquals("INFY", dto.tradingSymbol)
        assertEquals(100, dto.quantity)
        assertEquals(1400.25, dto.averagePrice!!, 0.001)
        assertEquals(5, dto.t1Quantity)
        assertEquals("NSE", dto.exchange)
        assertEquals("INE009A01021", dto.isin)
    }

    @Test
    fun `missing optional fields produce null without crash`() {
        val json = """{"tradingsymbol": "TCS"}"""

        val dto = moshi.adapter(HoldingDto::class.java).fromJson(json)!!

        assertEquals("TCS", dto.tradingSymbol)
        assertNull(dto.quantity)
        assertNull(dto.averagePrice)
        assertNull(dto.t1Quantity)
        assertNull(dto.exchange)
        assertNull(dto.isin)
    }
}

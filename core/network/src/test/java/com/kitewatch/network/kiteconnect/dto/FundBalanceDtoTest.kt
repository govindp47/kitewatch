package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FundBalanceDtoTest {
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `parses fund balance JSON correctly`() {
        val json =
            """
            {
              "equity": {
                "available": {
                  "live_balance": 50000.00,
                  "opening_balance": 48000.00
                },
                "net": 49500.00
              }
            }
            """.trimIndent()

        val dto = moshi.adapter(FundBalanceDto::class.java).fromJson(json)!!

        assertEquals(50000.00, dto.equity!!.available!!.liveBalance!!, 0.001)
        assertEquals(48000.00, dto.equity.available!!.openingBalance!!, 0.001)
        assertEquals(49500.00, dto.equity.net!!, 0.001)
    }

    @Test
    fun `missing equity segment produces null without crash`() {
        val json = """{}"""

        val dto = moshi.adapter(FundBalanceDto::class.java).fromJson(json)!!

        assertNull(dto.equity)
    }

    @Test
    fun `missing nested available fields produce null without crash`() {
        val json = """{"equity": {"net": 1000.0}}"""

        val dto = moshi.adapter(FundBalanceDto::class.java).fromJson(json)!!

        assertNull(dto.equity!!.available)
        assertEquals(1000.0, dto.equity.net!!, 0.001)
    }
}

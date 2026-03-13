package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeRateDtoTest {
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `parses charge rate JSON correctly`() {
        val json =
            """
            {
              "equity": {
                "delivery": {
                  "stt_ctt": 0.1,
                  "exchange_turnover_charge": 0.00297,
                  "gst": 18.0,
                  "sebi_turnover_charge": 10.0,
                  "stamp_duty": 0.015
                }
              }
            }
            """.trimIndent()

        val dto = moshi.adapter(ChargeRateDto::class.java).fromJson(json)!!

        assertEquals(0.1, dto.equity!!.delivery!!.sttCtt!!, 0.0001)
        assertEquals(0.00297, dto.equity.delivery!!.exchangeTurnoverCharge!!, 0.000001)
        assertEquals(18.0, dto.equity.delivery!!.gst!!, 0.001)
        assertEquals(10.0, dto.equity.delivery!!.sebiTurnoverCharge!!, 0.001)
        assertEquals(0.015, dto.equity.delivery!!.stampDuty!!, 0.0001)
    }

    @Test
    fun `completely empty JSON produces nulls without crash — fallback ready`() {
        val json = """{}"""

        val dto = moshi.adapter(ChargeRateDto::class.java).fromJson(json)!!

        assertNull(dto.equity)
    }

    @Test
    fun `partial charge rate JSON produces nulls for missing fields`() {
        val json = """{"equity": {"delivery": {"stt_ctt": 0.1}}}"""

        val dto = moshi.adapter(ChargeRateDto::class.java).fromJson(json)!!

        assertEquals(0.1, dto.equity!!.delivery!!.sttCtt!!, 0.0001)
        assertNull(dto.equity.delivery!!.gst)
        assertNull(dto.equity.delivery!!.stampDuty)
    }
}

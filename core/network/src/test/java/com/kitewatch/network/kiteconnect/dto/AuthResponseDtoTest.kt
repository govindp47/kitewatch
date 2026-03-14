package com.kitewatch.network.kiteconnect.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthResponseDtoTest {
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `parses full auth response JSON correctly`() {
        val json =
            """
            {
              "access_token": "abc123token",
              "public_token": "pub456token",
              "user_id": "AB1234",
              "user_name": "John Doe"
            }
            """.trimIndent()

        val dto = moshi.adapter(AuthResponseDto::class.java).fromJson(json)!!

        assertEquals("abc123token", dto.accessToken)
        assertEquals("pub456token", dto.publicToken)
        assertEquals("AB1234", dto.userId)
        assertEquals("John Doe", dto.userName)
    }

    @Test
    fun `missing optional fields produce null without crash`() {
        val json = """{"access_token": "xyz789"}"""

        val dto = moshi.adapter(AuthResponseDto::class.java).fromJson(json)!!

        assertEquals("xyz789", dto.accessToken)
        assertNull(dto.publicToken)
        assertNull(dto.userId)
        assertNull(dto.userName)
    }
}

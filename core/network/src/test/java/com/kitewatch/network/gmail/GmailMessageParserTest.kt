package com.kitewatch.network.gmail

import com.kitewatch.domain.model.Paisa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GmailMessageParserTest {
    // ── Fixture 1: valid Zerodha fund-credit email body ───────────────────────

    @Test
    fun `Rs dot format - extracts correct Paisa amount`() {
        val body =
            """
            Dear Customer,

            Your funds have been added to your Kite account.
            Amount: Rs. 50,000.00

            Regards,
            Zerodha
            """.trimIndent()

        val result = GmailMessageParser.parse(body)

        assertNotNull(result)
        assertEquals(Paisa.fromRupees(java.math.BigDecimal("50000.00")), result!!.amount)
    }

    @Test
    fun `rupee symbol format - extracts correct Paisa amount`() {
        val body =
            """
            Funds credited to your account.
            ₹1,00,000 has been added to your Kite trading account.
            """.trimIndent()

        val result = GmailMessageParser.parse(body)

        assertNotNull(result)
        // ₹1,00,000 = 100000 rupees = 10000000 paisa
        assertEquals(Paisa(10_000_000L), result!!.amount)
    }

    @Test
    fun `Rs without dot - extracts correct Paisa amount`() {
        val body = "Funds of Rs.75000 added successfully."

        val result = GmailMessageParser.parse(body)

        assertNotNull(result)
        assertEquals(Paisa(7_500_000L), result!!.amount)
    }

    // ── Fixture 2: non-Zerodha email ─────────────────────────────────────────

    @Test
    fun `non-matching email body returns null`() {
        val body =
            """
            Your Amazon order has been shipped.
            Order total: ${'$'}49.99
            Expected delivery: Friday
            """.trimIndent()

        val result = GmailMessageParser.parse(body)

        assertNull(result)
    }

    @Test
    fun `email with no amount pattern returns null`() {
        val body = "Funds have been added to your Kite account. Please check your balance."

        val result = GmailMessageParser.parse(body)

        assertNull(result)
    }

    // ── Fixture 3: Zerodha email with non-fund subject (via DTO parse) ────────

    @Test
    fun `DTO parse - non-fund subject returns null`() {
        val dto =
            com.kitewatch.network.gmail.dto.GmailMessageDto(
                id = "msg001",
                payload =
                    com.kitewatch.network.gmail.dto.GmailPayloadDto(
                        headers =
                            listOf(
                                com.kitewatch.network.gmail.dto
                                    .GmailHeaderDto("Subject", "Order executed on NSE"),
                                com.kitewatch.network.gmail.dto
                                    .GmailHeaderDto("From", "no-reply@zerodha.com"),
                            ),
                    ),
            )

        val result = GmailMessageParser.parse(dto)

        assertNull("Non-fund subject should return null", result)
    }

    @Test
    fun `DTO parse - non-zerodha sender returns null even with matching subject`() {
        val body64 =
            java.util.Base64
                .getUrlEncoder()
                .encodeToString("Amount: Rs. 10,000.00".toByteArray())

        val dto =
            com.kitewatch.network.gmail.dto.GmailMessageDto(
                id = "msg002",
                internalDateMs = "1700000000000",
                payload =
                    com.kitewatch.network.gmail.dto.GmailPayloadDto(
                        mimeType = "text/plain",
                        headers =
                            listOf(
                                com.kitewatch.network.gmail.dto
                                    .GmailHeaderDto("Subject", "Funds added to Kite"),
                                com.kitewatch.network.gmail.dto
                                    .GmailHeaderDto("From", "phishing@evil.com"),
                            ),
                        body =
                            com.kitewatch.network.gmail.dto
                                .GmailBodyDto(data = body64),
                    ),
            )

        val result = GmailMessageParser.parse(dto)

        assertNull("Non-Zerodha sender should return null", result)
    }

    @Test
    fun `DTO parse - valid Zerodha fund email returns correct result`() {
        val bodyText = "Dear Customer, Rs. 25,000.00 has been added to your account."
        val body64 =
            java.util.Base64
                .getUrlEncoder()
                .encodeToString(bodyText.toByteArray())

        val dto =
            com.kitewatch.network.gmail.dto.GmailMessageDto(
                id = "msg003",
                internalDateMs = "1705276800000", // 2024-01-15 in epoch ms (approx)
                payload =
                    com.kitewatch.network.gmail.dto.GmailPayloadDto(
                        mimeType = "text/plain",
                        headers =
                            listOf(
                                com.kitewatch.network.gmail.dto
                                    .GmailHeaderDto("Subject", "Funds added to Kite"),
                                com.kitewatch.network.gmail.dto
                                    .GmailHeaderDto("From", "no-reply@zerodha.com"),
                            ),
                        body =
                            com.kitewatch.network.gmail.dto
                                .GmailBodyDto(data = body64),
                    ),
            )

        val result = GmailMessageParser.parse(dto)

        assertNotNull(result)
        assertEquals("msg003", result!!.messageId)
        assertEquals(Paisa(2_500_000L), result.amount) // ₹25,000 = 2,500,000 paisa
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `zero amount returns null`() {
        val result = GmailMessageParser.parse("Amount: Rs. 0.00 credited.")
        assertNull(result)
    }

    @Test
    fun `rupee symbol with decimal returns correct amount`() {
        val result = GmailMessageParser.parse("₹1,234.56 added to your account.")
        assertNotNull(result)
        assertEquals(Paisa.fromRupees(java.math.BigDecimal("1234.56")), result!!.amount)
    }
}

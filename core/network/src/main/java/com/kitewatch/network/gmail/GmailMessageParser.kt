package com.kitewatch.network.gmail

import com.kitewatch.domain.model.Paisa
import com.kitewatch.network.gmail.dto.GmailHeaderDto
import com.kitewatch.network.gmail.dto.GmailMessageDto
import com.kitewatch.network.gmail.dto.GmailPayloadDto
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

/**
 * Extracts fund-credit information from a Zerodha "Funds added to Kite" email.
 *
 * Parsing strategy:
 * 1. Verify the message has the expected Zerodha fund-credit subject.
 * 2. Decode the `text/plain` body part from base64url.
 * 3. Extract the rupee amount using two patterns:
 *    - `Rs. 1,00,000.00`  (ASCII, with optional decimal)
 *    - `₹1,00,000`        (Unicode rupee symbol, with optional decimal)
 * 4. Convert to [Paisa] (× 100, integer-safe via BigDecimal).
 * 5. Derive [LocalDate] from the message's `internalDate` epoch-millisecond field.
 *
 * Returns `null` — rather than throwing — for any non-matching input.
 */
object GmailMessageParser {
    /**
     * Expected subject prefix for Zerodha fund-credit confirmation emails.
     * Matched case-insensitively to guard against minor formatting changes.
     */
    private const val ZERODHA_FUND_SUBJECT = "funds added"

    /**
     * Expected sender domain. Used as a secondary guard inside parse to prevent
     * false positives from unrelated emails with similar subjects.
     */
    private const val ZERODHA_SENDER_DOMAIN = "zerodha.com"

    /**
     * Amount patterns covering:
     *  - `Rs. 50,000.00`   → group 1 = "50,000.00"
     *  - `Rs.50000`        → group 1 = "50000"
     *  - `₹50,000`         → group 1 = "50,000"
     *  - `₹ 50,000.50`     → group 1 = "50,000.50"
     */
    private val AMOUNT_PATTERNS =
        listOf(
            Regex("""(?:Rs\.?\s*)([\d,]+(?:\.\d{1,2})?)"""),
            Regex("""₹\s*([\d,]+(?:\.\d{1,2})?)"""),
        )

    /**
     * Parses a full Gmail message resource and returns a [FundDetectionResult],
     * or `null` if the message does not match the Zerodha fund-credit pattern.
     *
     * @param message Full [GmailMessageDto] fetched with `format=full`.
     */
    fun parse(message: GmailMessageDto): FundDetectionResult? =
        extractValidatedSubject(message.payload?.headers)?.let { subject ->
            extractPlainTextBody(message.payload)?.let { body ->
                buildResult(message, subject, body)
            }
        }

    private fun extractValidatedSubject(headers: List<GmailHeaderDto>?): String? {
        if (headers == null) return null
        val subject = headers.firstOrNull { it.name.equals("Subject", ignoreCase = true) }?.value
        val from = headers.firstOrNull { it.name.equals("From", ignoreCase = true) }?.value
        val isValidSender = from?.contains(ZERODHA_SENDER_DOMAIN, ignoreCase = true) == true
        val isValidSubject = subject?.contains(ZERODHA_FUND_SUBJECT, ignoreCase = true) == true
        return subject.takeIf { isValidSubject && isValidSender }
    }

    private fun buildResult(
        message: GmailMessageDto,
        subject: String,
        body: String,
    ): FundDetectionResult? {
        val amount = extractAmount(body) ?: return null
        val date =
            message.internalDateMs
                ?.toLongOrNull()
                ?.let { ms -> Instant.ofEpochMilli(ms).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate() }
                ?: LocalDate.now()
        return FundDetectionResult(
            messageId = message.id,
            subject = subject,
            amount = amount,
            date = date,
        )
    }

    /**
     * Overload that accepts a raw message body string (for unit testing without a full DTO).
     * Subject and sender validation are skipped when calling this overload directly.
     */
    fun parse(messageBody: String): FundDetectionResult? =
        extractAmount(messageBody)?.let { amount ->
            FundDetectionResult(
                messageId = "",
                subject = "",
                amount = amount,
                date = LocalDate.now(),
            )
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Recursively walks the MIME payload tree to find the first `text/plain` body.
     * Gmail may nest the plain-text part inside a `multipart/alternative` wrapper.
     */
    private fun extractPlainTextBody(payload: GmailPayloadDto?): String? {
        if (payload == null) return null
        return if (payload.mimeType?.startsWith("text/plain") == true) {
            payload.body?.data?.let { String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }
        } else {
            payload.parts?.firstNotNullOfOrNull { extractPlainTextBody(it) }
        }
    }

    /**
     * Scans [body] for a rupee amount in any supported format.
     * Returns the first match as [Paisa], or `null` if nothing matches.
     */
    private fun extractAmount(body: String): Paisa? =
        AMOUNT_PATTERNS
            .firstNotNullOfOrNull { pattern ->
                val raw = pattern.find(body)?.groupValues?.getOrNull(1) ?: return@firstNotNullOfOrNull null
                val rupees = raw.replace(",", "").toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
                rupees?.let { Paisa.fromRupees(it) }
            }
}

/**
 * Result of a successful fund-credit parse from a Gmail message.
 *
 * @param messageId Gmail message ID — stored in `gmail_scan_cache` to prevent re-processing.
 * @param subject   Full email subject line for display in the confirmation UI.
 * @param amount    Detected fund amount in [Paisa].
 * @param date      Trade/credit date derived from the message's `internalDate`.
 */
data class FundDetectionResult(
    val messageId: String,
    val subject: String,
    val amount: Paisa,
    val date: LocalDate,
)

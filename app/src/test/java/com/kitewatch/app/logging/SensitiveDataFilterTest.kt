package com.kitewatch.app.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataFilterTest {
    @Test
    fun sanitize_accessToken_isRedacted() {
        val input = "Request failed access_token=abc123xyz"
        val result = SensitiveDataFilter.sanitize(input)
        assertFalse("access_token value must not appear", result.contains("abc123xyz"))
        assertTrue("access_token key must be preserved", result.contains("access_token"))
        assertTrue("REDACTED marker must be present", result.contains("[REDACTED]"))
    }

    @Test
    fun sanitize_apiKey_isRedacted() {
        val input = "Auth header built api_key=mySecretKey123"
        val result = SensitiveDataFilter.sanitize(input)
        assertFalse(result.contains("mySecretKey123"))
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun sanitize_password_isRedacted() {
        val input = "Login attempt password=hunter2"
        val result = SensitiveDataFilter.sanitize(input)
        assertFalse(result.contains("hunter2"))
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun sanitize_colonSeparator_isRedacted() {
        val input = "secret: topsecretvalue"
        val result = SensitiveDataFilter.sanitize(input)
        assertFalse(result.contains("topsecretvalue"))
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun sanitize_caseInsensitiveKey_isRedacted() {
        val input = "API_KEY=UpperCaseKey ACCESS_TOKEN=upperToken"
        val result = SensitiveDataFilter.sanitize(input)
        assertFalse(result.contains("UpperCaseKey"))
        assertFalse(result.contains("upperToken"))
    }

    @Test
    fun sanitize_nonSensitiveMessage_isUnchanged() {
        val input = "Order sync completed: 5 new orders for INFY"
        val result = SensitiveDataFilter.sanitize(input)
        assertEquals("Non-sensitive message must pass through unchanged", input, result)
    }

    @Test
    fun sanitize_emptyMessage_returnsEmpty() {
        assertEquals("", SensitiveDataFilter.sanitize(""))
    }

    @Test
    fun sanitize_multipleSecrets_allRedacted() {
        val input = "api_key=key123 access_token=tok456 password=pass789"
        val result = SensitiveDataFilter.sanitize(input)
        assertFalse(result.contains("key123"))
        assertFalse(result.contains("tok456"))
        assertFalse(result.contains("pass789"))
        assertEquals(3, Regex.fromLiteral("[REDACTED]").findAll(result).count())
    }

    @Test
    fun sanitize_requestToken_isRedacted() {
        val input = "OAuth callback request_token=rt_abcdef"
        val result = SensitiveDataFilter.sanitize(input)
        assertFalse(result.contains("rt_abcdef"))
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun sanitize_passphrase_isRedacted() {
        val input = "db_passphrase=base64encodedvalue123"
        val result = SensitiveDataFilter.sanitize(input)
        assertFalse(result.contains("base64encodedvalue123"))
        assertTrue(result.contains("[REDACTED]"))
    }
}

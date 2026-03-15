package com.kitewatch.app.logging

/**
 * Redacts known-sensitive credential patterns from log message strings.
 *
 * Matches key=value and key:value forms (case-insensitive) where the key is one of
 * the known sensitive identifiers. The matched value token is replaced with
 * [REDACTED_PLACEHOLDER], leaving the key name visible for diagnostics.
 *
 * Usage:
 * ```kotlin
 * val safe = SensitiveDataFilter.sanitize("auth failed token=abc123 for user")
 * // → "auth failed token=[REDACTED] for user"
 * ```
 *
 * This filter is applied in [com.kitewatch.app.logging.SanitisedDebugTree] so that
 * every `Timber.*` call in debug builds is scrubbed before reaching logcat. The
 * [com.kitewatch.app.ReleaseTree] no-ops all log calls so this filter is not needed
 * in release builds.
 */
object SensitiveDataFilter {
    private const val REDACTED_PLACEHOLDER = "[REDACTED]"

    /**
     * Ordered set of key names whose associated values must never appear in logs.
     * Keys are matched case-insensitively. Extend this set as new secret types are introduced.
     */
    private val SENSITIVE_KEYS: Set<String> =
        setOf(
            "access_token",
            "accesstoken",
            "request_token",
            "requesttoken",
            "api_key",
            "apikey",
            "api_secret",
            "apisecret",
            "secret",
            "password",
            "passphrase",
            "db_passphrase",
            "authorization",
            "bearer",
            "refresh_token",
            "refreshtoken",
        )

    /**
     * Regex that matches `<key><separator><value>` where:
     * - `<key>` is any of [SENSITIVE_KEYS]
     * - `<separator>` is `=` or `:` with optional surrounding whitespace
     * - `<value>` is one or more non-whitespace characters
     *
     * Built once at class-load time from the [SENSITIVE_KEYS] set.
     */
    private val PATTERN: Regex by lazy {
        val keyAlternation = SENSITIVE_KEYS.joinToString("|") { Regex.escape(it) }
        Regex(
            "(?i)($keyAlternation)\\s*[=:]\\s*(\\S+)",
        )
    }

    /**
     * Returns [message] with every sensitive key=value occurrence replaced by
     * `<key>=[REDACTED]`. Non-sensitive content is preserved verbatim.
     */
    fun sanitize(message: String): String =
        PATTERN.replace(message) { match ->
            "${match.groupValues[1]}=$REDACTED_PLACEHOLDER"
        }
}

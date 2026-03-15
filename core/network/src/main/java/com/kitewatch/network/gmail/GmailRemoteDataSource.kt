package com.kitewatch.network.gmail

import android.content.SharedPreferences
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates Gmail search and message parsing for Zerodha fund-credit detection.
 *
 * The OAuth token is read at call time from [encryptedPrefs] so that a token refresh
 * between worker invocations is picked up automatically.
 *
 * Deduplication against already-processed messages is the caller's responsibility:
 * pass a set of [alreadySeenIds] (sourced from `GmailScanCacheDao`) to skip those messages.
 * This keeps `:core-network` free of any DAO or database dependency.
 */
@Singleton
class GmailRemoteDataSource
    @Inject
    constructor(
        private val apiClient: GmailApiClient,
        private val encryptedPrefs: SharedPreferences,
    ) {
        private val bearerToken: String
            get() = "Bearer ${encryptedPrefs.getString(KEY_GOOGLE_OAUTH_TOKEN, "") ?: ""}"

        /**
         * Searches Gmail for Zerodha fund-credit emails since [since] and parses each
         * into a [FundDetectionResult].
         *
         * @param since          Only messages received on or after this date are scanned.
         * @param alreadySeenIds Set of Gmail message IDs already stored in `gmail_scan_cache`.
         *                       Messages whose ID is in this set are skipped without fetching.
         * @param maxResults     Maximum number of messages to retrieve from the search (default 50).
         * @return A list of successfully parsed fund-credit results — may be empty.
         */
        suspend fun scanForFundCredits(
            since: LocalDate,
            alreadySeenIds: Set<String> = emptySet(),
            maxResults: Int = 50,
        ): List<FundDetectionResult> {
            val query = buildQuery(since)
            val listResponse =
                apiClient.listMessages(
                    auth = bearerToken,
                    query = query,
                    max = maxResults,
                )

            val stubs = listResponse.messages ?: return emptyList()

            return stubs
                .filter { it.id !in alreadySeenIds }
                .mapNotNull { stub ->
                    val fullMessage =
                        runCatching {
                            apiClient.getMessage(auth = bearerToken, id = stub.id)
                        }.getOrNull() ?: return@mapNotNull null

                    GmailMessageParser.parse(fullMessage)
                }
        }

        // ── Helpers ───────────────────────────────────────────────────────────────

        /**
         * Builds a Gmail search query targeting Zerodha fund-credit confirmation emails.
         *
         * Format example: `from:no-reply@zerodha.com subject:"Funds added" after:2024/01/15`
         *
         * The `after:` operand uses Gmail's `YYYY/MM/DD` date format (not ISO-8601).
         */
        private fun buildQuery(since: LocalDate): String {
            val dateStr = since.format(GMAIL_DATE_FORMAT)
            return """from:no-reply@zerodha.com subject:"Funds added" after:$dateStr"""
        }

        companion object {
            /** Key for the Google OAuth token in EncryptedSharedPreferences. */
            const val KEY_GOOGLE_OAUTH_TOKEN = "google_oauth_token"

            /** Gmail `after:` operand requires `YYYY/MM/DD`. */
            private val GMAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        }
    }

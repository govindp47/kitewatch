package com.kitewatch.domain.model

import java.time.LocalDate

data class FundEntry(
    val entryId: Long,
    val entryType: FundEntryType,
    val amount: Paisa,
    val entryDate: LocalDate,
    val note: String?,
    val gmailMessageId: String?,
) {
    init {
        require(amount.value > 0) { "FundEntry amount must be > 0, was ${amount.value}" }
    }
}

enum class FundEntryType { DEPOSIT, WITHDRAWAL, DIVIDEND, MISC_ADJUSTMENT, GMAIL_DETECTED }

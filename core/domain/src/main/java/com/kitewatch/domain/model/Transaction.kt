package com.kitewatch.domain.model

import java.time.LocalDate

data class Transaction(
    val transactionId: Long,
    val type: TransactionType,
    val referenceId: String?,
    val stockCode: String?,
    val amount: Paisa,
    val transactionDate: LocalDate,
    val description: String,
    val source: TransactionSource,
)

enum class TransactionType {
    EQUITY_BUY,
    EQUITY_SELL,
    STT_CHARGE,
    EXCHANGE_CHARGE,
    SEBI_CHARGE,
    GST_CHARGE,
    STAMP_DUTY_CHARGE,
    DP_CHARGE,
    BROKERAGE_CHARGE,
    FUND_DEPOSIT,
    FUND_WITHDRAWAL,
    MISC_ADJUSTMENT,
}

enum class TransactionSource { SYNC, CSV_IMPORT, MANUAL, GMAIL_SCAN }

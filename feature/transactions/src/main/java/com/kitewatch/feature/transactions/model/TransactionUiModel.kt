package com.kitewatch.feature.transactions.model

data class TransactionUiModel(
    val transactionId: Long,
    val date: String,
    val typeLabel: String,
    val stockCode: String?,
    val amount: String,
    val description: String,
    /** True for inflows (credits), false for outflows/charges. Controls amount color. */
    val isCredit: Boolean,
)

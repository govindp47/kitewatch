package com.kitewatch.feature.transactions

import com.kitewatch.domain.model.TransactionType

sealed interface TransactionsIntent {
    data class FilterByType(
        val type: TransactionType?,
    ) : TransactionsIntent
}

data class TransactionsState(
    val selectedType: TransactionType? = null,
)

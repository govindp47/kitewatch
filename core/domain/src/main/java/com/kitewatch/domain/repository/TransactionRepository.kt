package com.kitewatch.domain.repository

import com.kitewatch.domain.model.Transaction
import com.kitewatch.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * Append-only ledger. BR-10: no update or delete operations exist on this interface.
 * The absence of update/delete methods is enforced at compile time.
 */
interface TransactionRepository {
    suspend fun insert(transaction: Transaction): Long

    suspend fun insertAll(transactions: List<Transaction>): List<Long>

    fun observeAll(): Flow<List<Transaction>>

    fun observeByType(type: TransactionType): Flow<List<Transaction>>
}

package com.kitewatch.data.repository

import com.kitewatch.data.mapper.toDomain
import com.kitewatch.data.mapper.toEntity
import com.kitewatch.database.dao.TransactionDao
import com.kitewatch.domain.model.Transaction
import com.kitewatch.domain.model.TransactionType
import com.kitewatch.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
    ) : TransactionRepository {
        override suspend fun insert(transaction: Transaction): Long = transactionDao.insert(transaction.toEntity())

        override suspend fun insertAll(transactions: List<Transaction>): List<Long> =
            transactionDao.insertAll(transactions.map { it.toEntity() })

        override fun observeAll(): Flow<List<Transaction>> =
            transactionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

        override fun observeByType(type: TransactionType): Flow<List<Transaction>> =
            transactionDao.observeByType(type.name).map { entities -> entities.map { it.toDomain() } }
    }

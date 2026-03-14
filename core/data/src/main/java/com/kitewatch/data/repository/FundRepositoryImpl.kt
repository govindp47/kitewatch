package com.kitewatch.data.repository

import com.kitewatch.data.mapper.toDomain
import com.kitewatch.data.mapper.toEntity
import com.kitewatch.database.dao.FundEntryDao
import com.kitewatch.domain.model.FundEntry
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.FundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FundRepositoryImpl
    @Inject
    constructor(
        private val fundEntryDao: FundEntryDao,
    ) : FundRepository {
        override suspend fun insertEntry(entry: FundEntry): Long = fundEntryDao.insert(entry.toEntity())

        override fun observeEntries(): Flow<List<FundEntry>> =
            fundEntryDao.observeConfirmed().map { entities -> entities.map { it.toDomain() } }

        /**
         * Running balance: sum of inflows (DEPOSIT, DIVIDEND) minus sum of outflows (WITHDRAWAL).
         * Computed in-memory from the current confirmed entries list.
         */
        override suspend fun getRunningBalance(): Paisa {
            val entries = fundEntryDao.observeConfirmed().first()
            var balance = 0L
            for (entity in entries) {
                when (entity.entryType) {
                    "ADDITION", "DIVIDEND" -> balance += entity.amountPaisa
                    "WITHDRAWAL" -> balance -= entity.amountPaisa
                    // MISC_ADJUSTMENT: treat as inflow (positive)
                    else -> balance += entity.amountPaisa
                }
            }
            return Paisa(balance)
        }
    }

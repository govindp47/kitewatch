package com.kitewatch.domain.repository

import com.kitewatch.domain.model.FundEntry
import com.kitewatch.domain.model.Paisa
import kotlinx.coroutines.flow.Flow

interface FundRepository {
    suspend fun insertEntry(entry: FundEntry): Long

    fun observeEntries(): Flow<List<FundEntry>>

    suspend fun getRunningBalance(): Paisa
}

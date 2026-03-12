package com.kitewatch.domain.repository

import com.kitewatch.domain.model.ChargeRateSnapshot

interface ChargeRateRepository {
    suspend fun saveRates(snapshot: ChargeRateSnapshot)

    suspend fun getCurrentRates(): ChargeRateSnapshot?
}

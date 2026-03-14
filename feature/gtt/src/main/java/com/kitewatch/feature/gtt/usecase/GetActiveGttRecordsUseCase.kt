package com.kitewatch.feature.gtt.usecase

import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.repository.GttRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActiveGttRecordsUseCase
    @Inject
    constructor(
        private val gttRepository: GttRepository,
    ) {
        fun execute(): Flow<List<GttRecord>> = gttRepository.observeActive()
    }

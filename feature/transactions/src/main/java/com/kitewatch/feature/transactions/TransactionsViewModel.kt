package com.kitewatch.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.kitewatch.domain.model.Transaction
import com.kitewatch.feature.transactions.mapper.toUiModel
import com.kitewatch.feature.transactions.model.TransactionUiModel
import com.kitewatch.feature.transactions.usecase.GetTransactionsPagedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel
    @Inject
    constructor(
        private val getTransactionsPagedUseCase: GetTransactionsPagedUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(TransactionsState())
        val state: StateFlow<TransactionsState> = _state.asStateFlow()

        /** Paged transaction stream filtered by [TransactionsState.selectedType], cached across recompositions. */
        @OptIn(ExperimentalCoroutinesApi::class)
        val transactionsPaged: Flow<PagingData<TransactionUiModel>> =
            _state
                .flatMapLatest { s -> getTransactionsPagedUseCase.execute(s.selectedType) }
                .map { pagingData -> pagingData.map(Transaction::toUiModel) }
                .cachedIn(viewModelScope)

        fun processIntent(intent: TransactionsIntent) {
            when (intent) {
                is TransactionsIntent.FilterByType -> {
                    _state.update { it.copy(selectedType = intent.type) }
                }
            }
        }
    }

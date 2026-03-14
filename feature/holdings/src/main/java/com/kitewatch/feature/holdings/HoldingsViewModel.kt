package com.kitewatch.feature.holdings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitewatch.domain.model.ProfitTarget
import com.kitewatch.domain.repository.GttRepository
import com.kitewatch.domain.usecase.holdings.GetHoldingsUseCase
import com.kitewatch.domain.usecase.holdings.UpdateProfitTargetUseCase
import com.kitewatch.feature.holdings.mapper.toUiModel
import com.kitewatch.feature.holdings.model.HoldingUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HoldingsViewModel
    @Inject
    constructor(
        private val getHoldingsUseCase: GetHoldingsUseCase,
        private val updateProfitTargetUseCase: UpdateProfitTargetUseCase,
        private val gttRepository: GttRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HoldingsState())
        val state: StateFlow<HoldingsState> = _state.asStateFlow()

        private val _sideEffect = Channel<HoldingsSideEffect>(Channel.BUFFERED)
        val sideEffect: Flow<HoldingsSideEffect> = _sideEffect.receiveAsFlow()

        // Tracks expanded state per stock code (not part of use case output).
        private val expandedCodes = MutableStateFlow<Set<String>>(emptySet())

        init {
            observeHoldings()
        }

        fun processIntent(intent: HoldingsIntent) {
            when (intent) {
                is HoldingsIntent.ToggleExpand -> {
                    expandedCodes.update { codes ->
                        if (intent.stockCode in codes) codes - intent.stockCode else codes + intent.stockCode
                    }
                }

                is HoldingsIntent.EditProfitTarget -> {
                    _state.update { it.copy(editingStockCode = intent.stockCode) }
                }

                is HoldingsIntent.DismissEditSheet -> {
                    _state.update { it.copy(editingStockCode = null) }
                }

                is HoldingsIntent.SaveProfitTarget -> {
                    saveProfitTarget(intent.stockCode, intent.newTarget)
                }

                is HoldingsIntent.NavigateToGtt -> {
                    viewModelScope.launch {
                        _sideEffect.send(HoldingsSideEffect.NavigateToGtt)
                    }
                }
            }
        }

        // ── Private helpers ───────────────────────────────────────────────────────

        private fun observeHoldings() {
            viewModelScope.launch {
                combine(
                    getHoldingsUseCase.execute(),
                    gttRepository.observeActive(),
                    expandedCodes,
                ) { holdings, gttRecords, expanded ->
                    val gttByStock = gttRecords.associateBy { it.stockCode }
                    holdings.map { holding ->
                        holding.toUiModel(
                            gttRecord = gttByStock[holding.stockCode],
                            isExpanded = holding.stockCode in expanded,
                        )
                    }
                }.catch { t ->
                    _state.update { it.copy(isLoading = false, error = t.message) }
                }.collect { uiModels ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            holdings = uiModels,
                            error = null,
                        )
                    }
                }
            }
        }

        private fun saveProfitTarget(
            stockCode: String,
            newTarget: ProfitTarget,
        ) {
            viewModelScope.launch {
                runCatching {
                    updateProfitTargetUseCase.execute(stockCode, newTarget)
                }.onSuccess {
                    _state.update { it.copy(editingStockCode = null) }
                    _sideEffect.send(HoldingsSideEffect.ShowSnackbar("Target saved"))
                }.onFailure { t ->
                    _sideEffect.send(
                        HoldingsSideEffect.ShowSnackbar(
                            t.message ?: "Failed to save target",
                        ),
                    )
                }
            }
        }

        // Exposed for tests to verify mapping only — not part of production API.
        internal fun currentHoldings(): List<HoldingUiModel> = _state.value.holdings
    }

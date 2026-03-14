package com.kitewatch.feature.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitewatch.domain.repository.AlertRepository
import com.kitewatch.domain.repository.OrderRepository
import com.kitewatch.domain.usecase.fund.GetFundBalanceUseCase
import com.kitewatch.domain.usecase.portfolio.CalculatePnlUseCase
import com.kitewatch.feature.portfolio.mapper.toUiModel
import com.kitewatch.ui.component.DateRangePreset
import com.kitewatch.ui.formatter.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@HiltViewModel
class PortfolioViewModel
    @Inject
    constructor(
        private val calculatePnlUseCase: CalculatePnlUseCase,
        private val getFundBalanceUseCase: GetFundBalanceUseCase,
        private val alertRepository: AlertRepository,
        private val orderRepository: OrderRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PortfolioState())
        val state: StateFlow<PortfolioState> = _state.asStateFlow()

        private val _sideEffect = Channel<PortfolioSideEffect>(Channel.BUFFERED)
        val sideEffect: Flow<PortfolioSideEffect> = _sideEffect.receiveAsFlow()

        // Tracks the running P&L collection so it can be cancelled on range change.
        private var pnlJob: Job? = null

        init {
            observeFundBalance()
            observeAlerts()
            observeOrdersForChecklist()
            launchPnlCollection(_state.value.selectedRange, customFrom = null, customTo = null)
        }

        fun processIntent(intent: PortfolioIntent) {
            when (intent) {
                is PortfolioIntent.LoadData -> {
                    launchPnlCollection(
                        _state.value.selectedRange,
                        customFrom = null,
                        customTo = null,
                    )
                }

                is PortfolioIntent.SelectDateRange -> {
                    _state.update { it.copy(selectedRange = intent.range, isLoading = true, error = null) }
                    launchPnlCollection(intent.range, customFrom = null, customTo = null)
                }

                is PortfolioIntent.SelectCustomRange -> {
                    _state.update { it.copy(selectedRange = DateRangePreset.CUSTOM, isLoading = true, error = null) }
                    launchPnlCollection(DateRangePreset.CUSTOM, intent.from, intent.to)
                }

                is PortfolioIntent.DismissAlert -> {
                    viewModelScope.launch {
                        runCatching {
                            alertRepository.acknowledge(intent.alertId, Instant.now())
                        }
                    }
                }

                is PortfolioIntent.RefreshSync -> {
                    viewModelScope.launch {
                        _sideEffect.send(PortfolioSideEffect.NavigateToSync)
                    }
                }
            }
        }

        // ── Private helpers ───────────────────────────────────────────────────────

        private fun launchPnlCollection(
            preset: DateRangePreset,
            customFrom: LocalDate?,
            customTo: LocalDate?,
        ) {
            pnlJob?.cancel()
            pnlJob =
                viewModelScope.launch {
                    val dateRange = preset.toDateRange(customFrom, customTo)
                    calculatePnlUseCase
                        .execute(dateRange)
                        .catch { t ->
                            _state.update { it.copy(isLoading = false, error = t.message) }
                        }.collect { summary ->
                            _state.update { current ->
                                current.copy(
                                    isLoading = false,
                                    pnlSummary = summary.toUiModel(),
                                    error = null,
                                )
                            }
                        }
                }
        }

        private fun observeFundBalance() {
            viewModelScope.launch {
                getFundBalanceUseCase
                    .execute()
                    .catch { /* non-fatal; fund balance stays empty */ }
                    .collect { balance ->
                        _state.update { it.copy(fundBalance = CurrencyFormatter.format(balance)) }
                    }
            }
        }

        private fun observeAlerts() {
            viewModelScope.launch {
                alertRepository
                    .observeUnacknowledged()
                    .catch { /* non-fatal; alerts stay empty */ }
                    .collect { alerts ->
                        _state.update {
                            it.copy(unacknowledgedAlerts = alerts.map { a -> a.toUiModel() })
                        }
                    }
            }
        }

        private fun observeOrdersForChecklist() {
            viewModelScope.launch {
                orderRepository
                    .observeAll()
                    .catch { /* non-fatal */ }
                    .collect { orders ->
                        _state.update { it.copy(showSetupChecklist = orders.isEmpty()) }
                    }
            }
        }
    }

// ── DateRangePreset → ClosedRange<LocalDate> ─────────────────────────────────

private fun DateRangePreset.toDateRange(
    customFrom: LocalDate?,
    customTo: LocalDate?,
): ClosedRange<LocalDate> {
    val today = LocalDate.now()
    return when (this) {
        DateRangePreset.TODAY -> today..today

        DateRangePreset.THIS_WEEK ->
            today.with(java.time.DayOfWeek.MONDAY)..today

        DateRangePreset.THIS_MONTH ->
            today.with(TemporalAdjusters.firstDayOfMonth())..today

        DateRangePreset.THIS_YEAR ->
            today.with(TemporalAdjusters.firstDayOfYear())..today

        DateRangePreset.ALL_TIME ->
            LocalDate.of(2000, 1, 1)..today

        DateRangePreset.CUSTOM -> {
            val from = customFrom ?: today.with(TemporalAdjusters.firstDayOfMonth())
            val to = customTo ?: today
            from..to
        }
    }
}

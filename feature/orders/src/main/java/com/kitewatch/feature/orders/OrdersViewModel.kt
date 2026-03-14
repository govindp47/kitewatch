package com.kitewatch.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.SyncResult
import com.kitewatch.domain.usecase.SyncOrdersUseCase
import com.kitewatch.feature.orders.mapper.toUiModel
import com.kitewatch.feature.orders.model.OrderUiModel
import com.kitewatch.feature.orders.usecase.GetOrdersPagedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel
    @Inject
    constructor(
        private val getOrdersPagedUseCase: GetOrdersPagedUseCase,
        private val syncOrdersUseCase: SyncOrdersUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrdersState())
        val state: StateFlow<OrdersState> = _state.asStateFlow()

        private val _sideEffect = Channel<OrdersSideEffect>(Channel.BUFFERED)
        val sideEffect: Flow<OrdersSideEffect> = _sideEffect.receiveAsFlow()

        /** Paged order stream mapped to UI models, cached across recompositions. */
        val ordersPaged: Flow<PagingData<OrderUiModel>> =
            getOrdersPagedUseCase
                .execute()
                .map { pagingData -> pagingData.map(Order::toUiModel) }
                .cachedIn(viewModelScope)

        fun processIntent(intent: OrdersIntent) {
            when (intent) {
                is OrdersIntent.SyncNow -> sync()
                is OrdersIntent.SelectDateRange -> {
                    _state.update { it.copy(selectedRange = intent.preset) }
                }
            }
        }

        // ── Private helpers ───────────────────────────────────────────────────────

        private fun sync() {
            if (_state.value.isSyncing) return
            _state.update { it.copy(isSyncing = true) }
            viewModelScope.launch {
                _sideEffect.send(OrdersSideEffect.ShowSnackbar("Syncing…"))
                runCatching { syncOrdersUseCase.execute() }
                    .onSuccess { result ->
                        val syncResult = result.getOrNull()
                        val msg =
                            when (syncResult) {
                                is SyncResult.Success -> {
                                    val count = syncResult.newOrderCount
                                    "Sync complete — $count new order${if (count == 1) "" else "s"}"
                                }
                                is SyncResult.NoNewOrders -> "Sync complete — no new orders"
                                is SyncResult.Skipped -> "Sync skipped (weekend)"
                                else -> "Sync complete"
                            }
                        _sideEffect.send(OrdersSideEffect.ShowSnackbar(msg))
                    }.onFailure { t ->
                        _sideEffect.send(
                            OrdersSideEffect.ShowSnackbar(
                                t.message ?: "Sync failed",
                            ),
                        )
                    }
                _state.update { it.copy(isSyncing = false) }
            }
        }
    }

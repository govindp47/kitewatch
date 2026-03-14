package com.kitewatch.feature.gtt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.feature.gtt.mapper.toUiModel
import com.kitewatch.feature.gtt.usecase.GetActiveGttRecordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class GttViewModel
    @Inject
    constructor(
        private val getActiveGttRecordsUseCase: GetActiveGttRecordsUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(GttState())
        val state: StateFlow<GttState> = _state.asStateFlow()

        init {
            getActiveGttRecordsUseCase
                .execute()
                .onEach { records ->
                    val uiModels = records.map { it.toUiModel() }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            records = uiModels,
                            unacknowledgedOverrides =
                                records.count { r ->
                                    r.status == GttStatus.MANUAL_OVERRIDE_DETECTED
                                },
                        )
                    }
                }.catch { _state.update { it.copy(isLoading = false) } }
                .launchIn(viewModelScope)
        }
    }

package com.kitewatch.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitewatch.domain.usecase.gmail.ConfirmGmailEntryUseCase
import com.kitewatch.domain.usecase.gmail.GmailCacheEntry
import com.kitewatch.domain.usecase.gmail.GmailCachePort
import com.kitewatch.domain.usecase.gmail.ScanGmailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State ─────────────────────────────────────────────────────────────────────

data class GmailDetectionsState(
    val pending: List<GmailCacheEntry> = emptyList(),
    val isScanning: Boolean = false,
    val scanError: String? = null,
)

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface GmailDetectionsIntent {
    data object ScanNow : GmailDetectionsIntent

    data class Confirm(
        val messageId: String,
    ) : GmailDetectionsIntent

    data class Dismiss(
        val messageId: String,
    ) : GmailDetectionsIntent
}

// ── Side Effects ──────────────────────────────────────────────────────────────

sealed interface GmailDetectionsSideEffect {
    data class ShowSnackbar(
        val message: String,
    ) : GmailDetectionsSideEffect
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class GmailDetectionsViewModel
    @Inject
    constructor(
        private val scanGmailUseCase: ScanGmailUseCase,
        private val confirmGmailEntryUseCase: ConfirmGmailEntryUseCase,
        gmailCachePort: GmailCachePort,
    ) : ViewModel() {
        private val _state = MutableStateFlow(GmailDetectionsState())
        val state: StateFlow<GmailDetectionsState> = _state

        private val _sideEffect = Channel<GmailDetectionsSideEffect>(Channel.BUFFERED)
        val sideEffect = _sideEffect.receiveAsFlow()

        init {
            viewModelScope.launch {
                gmailCachePort
                    .observePending()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
                    .collect { entries ->
                        _state.update { it.copy(pending = entries) }
                    }
            }
        }

        fun processIntent(intent: GmailDetectionsIntent) {
            when (intent) {
                is GmailDetectionsIntent.ScanNow -> scanNow()
                is GmailDetectionsIntent.Confirm -> confirm(intent.messageId)
                is GmailDetectionsIntent.Dismiss -> dismiss(intent.messageId)
            }
        }

        private fun scanNow() {
            viewModelScope.launch {
                _state.update { it.copy(isScanning = true, scanError = null) }
                scanGmailUseCase
                    .execute()
                    .onSuccess { newCount ->
                        val message =
                            if (newCount > 0) {
                                "$newCount new fund credit(s) detected"
                            } else {
                                "No new fund credits found"
                            }
                        _sideEffect.send(GmailDetectionsSideEffect.ShowSnackbar(message))
                    }.onFailure { error ->
                        _state.update { it.copy(scanError = error.message) }
                        _sideEffect.send(
                            GmailDetectionsSideEffect.ShowSnackbar(
                                "Scan failed: ${error.message ?: "Unknown error"}",
                            ),
                        )
                    }
                _state.update { it.copy(isScanning = false) }
            }
        }

        private fun confirm(messageId: String) {
            viewModelScope.launch {
                confirmGmailEntryUseCase
                    .confirm(messageId)
                    .onSuccess {
                        _sideEffect.send(GmailDetectionsSideEffect.ShowSnackbar("Fund entry confirmed"))
                    }.onFailure { error ->
                        _sideEffect.send(
                            GmailDetectionsSideEffect.ShowSnackbar(
                                "Could not confirm: ${error.message ?: "Unknown error"}",
                            ),
                        )
                    }
            }
        }

        private fun dismiss(messageId: String) {
            viewModelScope.launch {
                confirmGmailEntryUseCase
                    .dismiss(messageId)
                    .onFailure { error ->
                        _sideEffect.send(
                            GmailDetectionsSideEffect.ShowSnackbar(
                                "Could not dismiss: ${error.message ?: "Unknown error"}",
                            ),
                        )
                    }
            }
        }
    }

package com.kitewatch.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.domain.repository.ChargeRateRepository
import com.kitewatch.domain.repository.OrderRepository
import com.kitewatch.domain.usecase.orders.CsvParsePort
import com.kitewatch.domain.usecase.orders.ImportCsvUseCase
import com.kitewatch.domain.usecase.orders.ImportRowError
import com.kitewatch.domain.usecase.orders.ImportTransactionPort
import com.kitewatch.domain.usecase.orders.ImportValidationException
import com.kitewatch.domain.usecase.orders.PreImportBackupPort
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI models ─────────────────────────────────────────────────────────────────

sealed class CsvImportUiResult {
    data class Success(
        val newOrderCount: Int,
        val skippedDuplicateCount: Int,
        val estimatedChargesCount: Int,
    ) : CsvImportUiResult()

    data class Failure(
        val errors: List<CsvErrorUiModel>,
    ) : CsvImportUiResult()
}

data class CsvErrorUiModel(
    val rowNumber: Int,
    val field: String,
    val message: String,
)

// ── State ─────────────────────────────────────────────────────────────────────

data class CsvImportState(
    val isImporting: Boolean = false,
    val result: CsvImportUiResult? = null,
    val error: String? = null,
)

// ── Intent ────────────────────────────────────────────────────────────────────

sealed class CsvImportIntent {
    data class SelectFile(
        val uri: Uri,
    ) : CsvImportIntent()

    data object DismissResult : CsvImportIntent()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class CsvImportViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val accountBindingRepository: AccountBindingRepository,
        private val orderRepository: OrderRepository,
        private val chargeRateRepository: ChargeRateRepository,
        private val csvParsePort: CsvParsePort,
        private val preImportBackupPort: PreImportBackupPort,
        private val importTransactionPort: ImportTransactionPort,
    ) : ViewModel() {
        private val importCsvUseCase =
            ImportCsvUseCase(
                csvParsePort = csvParsePort,
                preImportBackupPort = preImportBackupPort,
                importTransactionPort = importTransactionPort,
                orderRepository = orderRepository,
                chargeRateRepository = chargeRateRepository,
            )

        private val _state = MutableStateFlow(CsvImportState())
        val state: StateFlow<CsvImportState> = _state.asStateFlow()

        fun processIntent(intent: CsvImportIntent) {
            when (intent) {
                is CsvImportIntent.SelectFile -> startImport(intent.uri)
                CsvImportIntent.DismissResult -> _state.update { it.copy(result = null, error = null) }
            }
        }

        private fun startImport(uri: Uri) {
            if (_state.value.isImporting) return
            _state.update { it.copy(isImporting = true, result = null, error = null) }

            viewModelScope.launch {
                val accountId =
                    accountBindingRepository.getBinding()?.userId ?: ""

                val inputStream =
                    runCatching { context.contentResolver.openInputStream(uri) }
                        .getOrNull()

                if (inputStream == null) {
                    _state.update {
                        it.copy(isImporting = false, error = "Could not open the selected file.")
                    }
                    return@launch
                }

                val result =
                    inputStream.use { stream ->
                        importCsvUseCase.execute(stream, accountId)
                    }

                _state.update { current ->
                    current.copy(
                        isImporting = false,
                        result =
                            result.fold(
                                onSuccess = { importResult ->
                                    CsvImportUiResult.Success(
                                        newOrderCount = importResult.newOrderCount,
                                        skippedDuplicateCount = importResult.skippedDuplicateCount,
                                        estimatedChargesCount = importResult.estimatedChargesCount,
                                    )
                                },
                                onFailure = { throwable ->
                                    val errors: List<ImportRowError> =
                                        (throwable as? ImportValidationException)?.errors ?: emptyList()
                                    CsvImportUiResult.Failure(
                                        errors.map { e ->
                                            CsvErrorUiModel(
                                                rowNumber = e.rowNumber,
                                                field = e.field,
                                                message = e.message,
                                            )
                                        },
                                    )
                                },
                            ),
                        error =
                            if (result.isFailure &&
                                result.exceptionOrNull() !is ImportValidationException
                            ) {
                                result.exceptionOrNull()?.message ?: "An unexpected error occurred."
                            } else {
                                null
                            },
                    )
                }
            }
        }
    }

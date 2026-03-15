package com.kitewatch.infra.csv

import android.content.Context
import androidx.core.content.FileProvider
import com.kitewatch.domain.repository.HoldingRepository
import com.kitewatch.domain.repository.OrderRepository
import com.kitewatch.domain.repository.TransactionRepository
import com.kitewatch.domain.usecase.backup.ExportExcelUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/**
 * Concrete implementation of [ExportExcelUseCase].
 *
 * Fetches all orders, active holdings (quantity > 0), and all transactions,
 * builds a multi-sheet .xlsx via [ExcelExporter], writes it to
 * `<cacheDir>/exports/kitewatch_export_<date>.xlsx`, and returns a
 * FileProvider content URI string.
 *
 * The FileProvider authority must be declared in the app's AndroidManifest.xml
 * as `${applicationId}.fileprovider` with the `exports` cache path exposed.
 */
class ExcelExportUseCaseImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val orderRepository: OrderRepository,
        private val holdingRepository: HoldingRepository,
        private val transactionRepository: TransactionRepository,
    ) : ExportExcelUseCase {
        override suspend fun execute(): Result<String> =
            runCatching {
                val orders = orderRepository.getAll()
                val holdings = holdingRepository.getAll().filter { it.quantity > 0 }
                val transactions = transactionRepository.observeAll().first()

                val bytes = ExcelExporter.export(orders, holdings, transactions)

                val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
                val fileName = "kitewatch_export_${LocalDate.now()}.xlsx"
                val exportFile = File(exportDir, fileName).also { it.writeBytes(bytes) }

                FileProvider
                    .getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        exportFile,
                    ).toString()
            }
    }

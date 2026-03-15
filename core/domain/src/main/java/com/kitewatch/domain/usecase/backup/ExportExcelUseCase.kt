package com.kitewatch.domain.usecase.backup

/**
 * Port interface for Excel export.
 *
 * The implementation in `:infra-csv` fetches all records from repositories,
 * builds the .xlsx via [ExcelExporter], writes it to the app cache directory,
 * and returns a FileProvider content URI suitable for [android.content.Intent.ACTION_SEND].
 *
 * The URI is returned as a [String] to keep this interface Android-free
 * (`:core-domain` is a java-library module with no Android dependency).
 *
 * @return [Result.success] with a content URI string on success,
 *         or [Result.failure] with the underlying exception.
 */
fun interface ExportExcelUseCase {
    suspend fun execute(): Result<String>
}

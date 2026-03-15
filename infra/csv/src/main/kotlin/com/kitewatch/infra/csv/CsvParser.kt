package com.kitewatch.infra.csv

import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.infra.csv.model.CsvFormat
import com.kitewatch.infra.csv.model.CsvFormat.KITEWATCH_CUSTOM
import com.kitewatch.infra.csv.model.CsvFormat.KITE_ORDERS
import com.kitewatch.infra.csv.model.CsvFormat.KITE_TRADE_BOOK
import com.kitewatch.infra.csv.model.CsvParseResult
import com.kitewatch.infra.csv.model.CsvRowError
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Value holder for buildOrder parameters
// ---------------------------------------------------------------------------

private data class OrderParams(
    val zerodhaOrderId: String,
    val stockCode: String,
    val stockName: String,
    val orderType: OrderType,
    val quantity: Int,
    val price: Paisa,
    val tradeDate: LocalDate,
    val exchange: Exchange,
)

/**
 * Parses CSV import files in three supported formats into a list of [Order] domain objects.
 *
 * - All errors are collected before returning; parsing is never fail-fast.
 * - Non-delivery rows (product ≠ CNC / series ≠ EQ) are silently excluded.
 * - Has zero database dependencies.
 */
object CsvParser {
    // ---------------------------------------------------------------------------
    // Column name constants
    // ---------------------------------------------------------------------------

    private object TradeBook {
        const val TRADE_DATE = "trade_date"
        const val SYMBOL = "tradingsymbol"
        const val EXCHANGE = "exchange"
        const val SERIES = "series"
        const val TRADE_TYPE = "trade_type"
        const val QUANTITY = "quantity"
        const val PRICE = "price"
        const val ORDER_ID = "order_id"
        const val TRADE_ID = "trade_id"

        val REQUIRED = setOf(TRADE_DATE, SYMBOL, EXCHANGE, SERIES, TRADE_TYPE, QUANTITY, PRICE, ORDER_ID)
    }

    private object KiteOrders {
        const val DATE = "Date"
        const val TYPE = "Type"
        const val INSTRUMENT = "Instrument"
        const val PRODUCT = "Product"
        const val QTY = "Qty"
        const val AVG_PRICE = "Avg. price"
        const val STATUS = "Status"

        val REQUIRED = setOf(DATE, TYPE, INSTRUMENT, PRODUCT, QTY, AVG_PRICE, STATUS)
    }

    private object KiteWatchCustom {
        const val DATE = "date"
        const val STOCK_CODE = "stock_code"
        const val EXCHANGE = "exchange"
        const val TYPE = "type"
        const val QUANTITY = "quantity"
        const val PRICE = "price"
        const val ORDER_ID = "zerodha_order_id"

        val REQUIRED = setOf(DATE, STOCK_CODE, EXCHANGE, TYPE, QUANTITY, PRICE, ORDER_ID)
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Auto-detects the CSV format from the header line.
     *
     * @throws IllegalArgumentException if the header does not match any known format.
     */
    fun detect(firstLine: String): CsvFormat {
        val headers = splitCsvLine(firstLine).map { it.trim().lowercase() }.toSet()
        return when {
            TradeBook.TRADE_DATE in headers && TradeBook.TRADE_ID in headers -> KITE_TRADE_BOOK
            KiteOrders.AVG_PRICE.lowercase() in headers &&
                KiteOrders.PRODUCT.lowercase() in headers -> KITE_ORDERS
            KiteWatchCustom.ORDER_ID in headers &&
                KiteWatchCustom.STOCK_CODE in headers -> KITEWATCH_CUSTOM
            else -> throw IllegalArgumentException("Unrecognised CSV header: $firstLine")
        }
    }

    /**
     * Parses [inputStream] according to [format].
     *
     * Returns [CsvParseResult.Success] only when every data row is valid.
     * Returns [CsvParseResult.ValidationFailure] with ALL errors if any row fails.
     */
    fun parse(
        inputStream: InputStream,
        format: CsvFormat,
    ): CsvParseResult {
        val lines =
            inputStream
                .bufferedReader(Charsets.UTF_8)
                .readLines()
                .map { it.trimStart('\uFEFF') } // strip BOM if present
                .filter { it.isNotBlank() }

        val errors = mutableListOf<CsvRowError>()
        val orders = mutableListOf<Order>()

        if (lines.isNotEmpty()) {
            val headerLine = lines.first()
            val headers = splitCsvLine(headerLine).map { it.trim() }
            val dataLines = lines.drop(1)

            val requiredHeaders =
                when (format) {
                    KITE_TRADE_BOOK -> TradeBook.REQUIRED
                    KITE_ORDERS -> KiteOrders.REQUIRED
                    KITEWATCH_CUSTOM -> KiteWatchCustom.REQUIRED
                }
            val headersLower = headers.map { it.lowercase() }.toSet()
            val missingHeaders = requiredHeaders.filter { it.lowercase() !in headersLower }
            if (missingHeaders.isNotEmpty()) {
                errors.add(
                    CsvRowError(
                        rowNumber = 0,
                        field = "header",
                        message = "Missing required columns: ${missingHeaders.joinToString()}",
                    ),
                )
            } else {
                val columnIndex: Map<String, Int> =
                    headers.mapIndexed { i, name -> name.lowercase() to i }.toMap()

                dataLines.forEachIndexed { index, line ->
                    val rowNumber = index + 1
                    val cells = splitCsvLine(line)
                    val cell: (String) -> String = { name ->
                        columnIndex[name.lowercase()]?.let { cells.getOrNull(it)?.trim() } ?: ""
                    }
                    when (format) {
                        KITE_TRADE_BOOK ->
                            parseTradeBookRow(rowNumber, cell, errors, orders)
                        KITE_ORDERS ->
                            parseKiteOrdersRow(rowNumber, cell, errors, orders)
                        KITEWATCH_CUSTOM ->
                            parseKiteWatchCustomRow(rowNumber, cell, errors, orders)
                    }
                }
            }
        }

        return if (errors.isEmpty()) {
            CsvParseResult.Success(orders)
        } else {
            CsvParseResult.ValidationFailure(errors)
        }
    }

    /** Exposed for testing. */
    internal fun splitCsvLine(line: String): List<String> = csvSplitLine(line)

    // ---------------------------------------------------------------------------
    // Format-specific row parsers
    // ---------------------------------------------------------------------------

    private fun parseTradeBookRow(
        rowNumber: Int,
        cell: (String) -> String,
        errors: MutableList<CsvRowError>,
        orders: MutableList<Order>,
    ) {
        val series = cell(TradeBook.SERIES)
        if (!series.equals("EQ", ignoreCase = true)) return

        val rowErrors = mutableListOf<CsvRowError>()
        val tradeDate = csvParseDate(cell(TradeBook.TRADE_DATE), TradeBook.TRADE_DATE, rowNumber, rowErrors)
        val symbol = csvRequireNonBlank(cell(TradeBook.SYMBOL), TradeBook.SYMBOL, rowNumber, rowErrors)
        val exchange = csvParseExchange(cell(TradeBook.EXCHANGE), TradeBook.EXCHANGE, rowNumber, rowErrors)
        val orderType = csvParseOrderType(cell(TradeBook.TRADE_TYPE), TradeBook.TRADE_TYPE, rowNumber, rowErrors)
        val quantity = csvParseQuantity(cell(TradeBook.QUANTITY), TradeBook.QUANTITY, rowNumber, rowErrors)
        val price = csvParsePrice(cell(TradeBook.PRICE), TradeBook.PRICE, rowNumber, rowErrors)
        val orderId = csvRequireNonBlank(cell(TradeBook.ORDER_ID), TradeBook.ORDER_ID, rowNumber, rowErrors)

        errors.addAll(rowErrors)
        if (rowErrors.isEmpty()) {
            orders.add(
                csvBuildOrder(
                    OrderParams(
                        zerodhaOrderId = orderId!!,
                        stockCode = symbol!!,
                        stockName = symbol,
                        orderType = orderType!!,
                        quantity = quantity!!,
                        price = price!!,
                        tradeDate = tradeDate!!,
                        exchange = exchange!!,
                    ),
                ),
            )
        }
    }

    private fun parseKiteOrdersRow(
        rowNumber: Int,
        cell: (String) -> String,
        errors: MutableList<CsvRowError>,
        orders: MutableList<Order>,
    ) {
        val product = cell(KiteOrders.PRODUCT)
        if (!product.equals("CNC", ignoreCase = true)) return
        val status = cell(KiteOrders.STATUS)
        if (!status.equals("COMPLETE", ignoreCase = true)) return

        val rowErrors = mutableListOf<CsvRowError>()
        val tradeDate = csvParseDate(cell(KiteOrders.DATE), KiteOrders.DATE, rowNumber, rowErrors)
        val instrument =
            csvRequireNonBlank(cell(KiteOrders.INSTRUMENT), KiteOrders.INSTRUMENT, rowNumber, rowErrors)
        val orderType = csvParseOrderType(cell(KiteOrders.TYPE), KiteOrders.TYPE, rowNumber, rowErrors)
        val quantity = csvParseQuantity(cell(KiteOrders.QTY), KiteOrders.QTY, rowNumber, rowErrors)
        val price = csvParsePrice(cell(KiteOrders.AVG_PRICE), KiteOrders.AVG_PRICE, rowNumber, rowErrors)

        // Kite Orders export does not include order_id — generate a synthetic one
        val syntheticOrderId = "KITE_${tradeDate}_${instrument}_${orderType}_$quantity"

        errors.addAll(rowErrors)
        if (rowErrors.isEmpty()) {
            val (exchange, stockCode) = csvParseInstrumentField(instrument!!)
            orders.add(
                csvBuildOrder(
                    OrderParams(
                        zerodhaOrderId = syntheticOrderId,
                        stockCode = stockCode,
                        stockName = stockCode,
                        orderType = orderType!!,
                        quantity = quantity!!,
                        price = price!!,
                        tradeDate = tradeDate!!,
                        exchange = exchange,
                    ),
                ),
            )
        }
    }

    private fun parseKiteWatchCustomRow(
        rowNumber: Int,
        cell: (String) -> String,
        errors: MutableList<CsvRowError>,
        orders: MutableList<Order>,
    ) {
        val rowErrors = mutableListOf<CsvRowError>()
        val tradeDate = csvParseDate(cell(KiteWatchCustom.DATE), KiteWatchCustom.DATE, rowNumber, rowErrors)
        val stockCode =
            csvRequireNonBlank(cell(KiteWatchCustom.STOCK_CODE), KiteWatchCustom.STOCK_CODE, rowNumber, rowErrors)
        val exchange = csvParseExchange(cell(KiteWatchCustom.EXCHANGE), KiteWatchCustom.EXCHANGE, rowNumber, rowErrors)
        val orderType = csvParseOrderType(cell(KiteWatchCustom.TYPE), KiteWatchCustom.TYPE, rowNumber, rowErrors)
        val quantity = csvParseQuantity(cell(KiteWatchCustom.QUANTITY), KiteWatchCustom.QUANTITY, rowNumber, rowErrors)
        val price = csvParsePrice(cell(KiteWatchCustom.PRICE), KiteWatchCustom.PRICE, rowNumber, rowErrors)
        val orderId =
            csvRequireNonBlank(cell(KiteWatchCustom.ORDER_ID), KiteWatchCustom.ORDER_ID, rowNumber, rowErrors)

        errors.addAll(rowErrors)
        if (rowErrors.isEmpty()) {
            orders.add(
                csvBuildOrder(
                    OrderParams(
                        zerodhaOrderId = orderId!!,
                        stockCode = stockCode!!,
                        stockName = stockCode,
                        orderType = orderType!!,
                        quantity = quantity!!,
                        price = price!!,
                        tradeDate = tradeDate!!,
                        exchange = exchange!!,
                    ),
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// File-level helpers — excluded from object TooManyFunctions count
// ---------------------------------------------------------------------------

private fun csvRequireNonBlank(
    value: String,
    field: String,
    rowNumber: Int,
    errors: MutableList<CsvRowError>,
): String? {
    if (value.isBlank()) {
        errors.add(CsvRowError(rowNumber, field, "Field '$field' is required but was empty"))
        return null
    }
    return value
}

private val CSV_DATE_FORMATS =
    listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
    )

private fun csvParseDate(
    value: String,
    field: String,
    rowNumber: Int,
    errors: MutableList<CsvRowError>,
): LocalDate? {
    val trimmed = value.trim()
    val datePart = trimmed.substringBefore(' ')
    val parsed =
        if (trimmed.isNotBlank()) {
            CSV_DATE_FORMATS
                .firstNotNullOfOrNull { formatter ->
                    runCatching { LocalDate.parse(datePart, formatter) }
                        .recoverCatching { LocalDate.parse(trimmed, formatter) }
                        .getOrNull()
                }
        } else {
            null
        }
    if (parsed == null) {
        val msg =
            if (trimmed.isBlank()) {
                "Field '$field' is required but was empty"
            } else {
                "Cannot parse date '$value' in field '$field'"
            }
        errors.add(CsvRowError(rowNumber, field, msg))
    }
    return parsed
}

private fun csvParseExchange(
    value: String,
    field: String,
    rowNumber: Int,
    errors: MutableList<CsvRowError>,
): Exchange? =
    when (value.trim().uppercase()) {
        "NSE" -> Exchange.NSE
        "BSE" -> Exchange.BSE
        else -> {
            errors.add(CsvRowError(rowNumber, field, "Unknown exchange '$value'; expected NSE or BSE"))
            null
        }
    }

private fun csvParseOrderType(
    value: String,
    field: String,
    rowNumber: Int,
    errors: MutableList<CsvRowError>,
): OrderType? =
    when (value.trim().uppercase()) {
        "BUY", "B" -> OrderType.BUY
        "SELL", "S" -> OrderType.SELL
        else -> {
            errors.add(CsvRowError(rowNumber, field, "Unknown order type '$value'; expected BUY or SELL"))
            null
        }
    }

private fun csvParseQuantity(
    value: String,
    field: String,
    rowNumber: Int,
    errors: MutableList<CsvRowError>,
): Int? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) {
        errors.add(CsvRowError(rowNumber, field, "Field '$field' is required but was empty"))
        return null
    }
    val qty = trimmed.toIntOrNull()
    return if (qty != null && qty > 0) {
        qty
    } else {
        errors.add(CsvRowError(rowNumber, field, "Quantity must be a positive integer, got '$value'"))
        null
    }
}

private fun csvParsePrice(
    value: String,
    field: String,
    rowNumber: Int,
    errors: MutableList<CsvRowError>,
): Paisa? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) {
        errors.add(CsvRowError(rowNumber, field, "Field '$field' is required but was empty"))
        return null
    }
    val decimal = runCatching { BigDecimal(trimmed) }.getOrNull()
    return when {
        decimal == null -> {
            errors.add(CsvRowError(rowNumber, field, "Price must be a positive decimal, got '$value'"))
            null
        }
        decimal <= BigDecimal.ZERO -> {
            errors.add(CsvRowError(rowNumber, field, "Price must be positive, got '$value'"))
            null
        }
        else -> Paisa.fromRupees(decimal)
    }
}

/**
 * Splits a single CSV line respecting double-quoted fields.
 * Handles commas inside quoted fields and escaped quotes ("").
 */
internal fun csvSplitLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' && !inQuotes -> inQuotes = true
            ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                current.append('"')
                i++
            }
            ch == '"' && inQuotes -> inQuotes = false
            ch == ',' && !inQuotes -> {
                result.add(current.toString())
                current.clear()
            }
            else -> current.append(ch)
        }
        i++
    }
    result.add(current.toString())
    return result
}

/**
 * Parses instrument field which may be either "INFY" or "NSE:INFY".
 * Returns (exchange, stockCode). Defaults to NSE when not specified.
 */
private fun csvParseInstrumentField(instrument: String): Pair<Exchange, String> {
    val parts = instrument.trim().split(":")
    return if (parts.size == 2) {
        val exchange =
            when (parts[0].uppercase()) {
                "BSE" -> Exchange.BSE
                else -> Exchange.NSE
            }
        exchange to parts[1].trim()
    } else {
        Exchange.NSE to instrument.trim()
    }
}

private fun csvBuildOrder(params: OrderParams): Order =
    Order(
        orderId = 0L,
        zerodhaOrderId = params.zerodhaOrderId,
        stockCode = params.stockCode,
        stockName = params.stockName,
        orderType = params.orderType,
        quantity = params.quantity,
        price = params.price,
        totalValue = params.price * params.quantity,
        tradeDate = params.tradeDate,
        exchange = params.exchange,
        settlementId = null,
        source = OrderSource.CSV_IMPORT,
    )

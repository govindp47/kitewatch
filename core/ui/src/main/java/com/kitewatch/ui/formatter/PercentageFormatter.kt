package com.kitewatch.ui.formatter

import java.math.BigDecimal
import java.math.RoundingMode

object PercentageFormatter {
    /**
     * Formats basis points as a percentage string.
     *
     * Examples:
     *  - 500  → "5.00%"
     *  - 25   → "0.25%"
     *  - -250 → "-2.50%"
     */
    fun format(basisPoints: Int): String {
        val pct = BigDecimal(basisPoints).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        return "${pct.toPlainString()}%"
    }

    /**
     * Formats basis points with an explicit sign prefix.
     *
     * Examples:
     *  - 500  → "+5.00%"
     *  - -250 → "-2.50%"
     *  - 0    → "+0.00%"
     */
    fun formatWithSign(basisPoints: Int): String {
        val pct = BigDecimal(basisPoints).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        val prefix = if (basisPoints < 0) "" else "+"
        return "$prefix${pct.toPlainString()}%"
    }
}

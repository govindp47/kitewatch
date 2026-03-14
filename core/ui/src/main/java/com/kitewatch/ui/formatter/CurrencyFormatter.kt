package com.kitewatch.ui.formatter

import com.kitewatch.domain.model.Paisa
import java.math.BigDecimal
import java.math.RoundingMode

object CurrencyFormatter {
    /**
     * Formats a [Paisa] value as an Indian-formatted currency string.
     *
     * Uses the Indian numbering convention: groups the last three digits,
     * then groups subsequent digits in pairs from right to left.
     *
     * Examples:
     *  - Paisa(10_000_00L)       → "₹1,00,000.00"
     *  - Paisa(-50_000L)         → "-₹500.00"
     *  - Paisa(0L)               → "₹0.00"
     *  - Paisa(1_00_00_000_00L)  → "₹1,00,00,000.00"
     */
    fun format(paisa: Paisa): String {
        val rupees = paisa.toRupees()
        val isNegative = rupees.signum() < 0
        val absRupees = rupees.abs().setScale(2, RoundingMode.HALF_UP)

        val formatted = formatIndian(absRupees)
        return if (isNegative) "-₹$formatted" else "₹$formatted"
    }

    /**
     * Formats an absolute [BigDecimal] using Indian number grouping.
     * Groups: last 3 digits, then pairs of 2 digits moving left.
     */
    private fun formatIndian(value: BigDecimal): String {
        // Split integer and decimal parts
        val plain = value.toPlainString()
        val dotIndex = plain.indexOf('.')
        val intPart: String
        val decPart: String
        if (dotIndex >= 0) {
            intPart = plain.substring(0, dotIndex)
            decPart = plain.substring(dotIndex) // includes "."
        } else {
            intPart = plain
            decPart = ".00"
        }

        return indianGrouped(intPart) + decPart
    }

    /**
     * Applies Indian grouping to a string of digits (no sign, no decimal).
     * Rule: last 3 digits form the rightmost group; then groups of 2 to the left.
     */
    private fun indianGrouped(digits: String): String {
        if (digits.length <= 3) return digits

        val sb = StringBuilder()
        val firstGroup = digits.length - 3
        sb.append(digits.substring(0, firstGroup))

        // Insert commas every 2 digits within the left portion
        val leftPart = sb.toString()
        sb.clear()
        val remainder = leftPart.length % 2
        for (i in leftPart.indices) {
            if (i != 0 && (i - remainder) % 2 == 0) sb.append(',')
            sb.append(leftPart[i])
        }

        sb.append(',')
        sb.append(digits.substring(firstGroup))
        return sb.toString()
    }
}

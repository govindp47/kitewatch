package com.kitewatch.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong

@JvmInline
value class Paisa(
    val value: Long,
) : Comparable<Paisa> {
    operator fun plus(other: Paisa) = Paisa(value + other.value)

    operator fun minus(other: Paisa) = Paisa(value - other.value)

    operator fun times(quantity: Int) = Paisa(value * quantity)

    operator fun times(quantity: Long) = Paisa(value * quantity)

    operator fun div(divisor: Int): Paisa {
        require(divisor != 0) { "Division by zero" }
        return Paisa(value / divisor)
    }

    operator fun div(divisor: Long): Paisa {
        require(divisor != 0L) { "Division by zero" }
        return Paisa(value / divisor)
    }

    operator fun unaryMinus() = Paisa(-value)

    fun abs() = Paisa(abs(value))

    fun isPositive() = value > 0

    fun isNegative() = value < 0

    fun isZero() = value == 0L

    /**
     * Multiply by a rate in basis points (1 basis point = 0.01%).
     *
     * Formula: (value * basisPoints + 5000) / 10_000
     * The +5000 implements round-half-up for the division by 10_000.
     *
     * Example: Paisa(1_000_000) applyBasisPoints 500 (5%) = Paisa(50_000)
     */
    fun applyBasisPoints(basisPoints: Int): Paisa {
        val result = (value * basisPoints + 5000) / 10_000
        return Paisa(result)
    }

    /**
     * Convert to BigDecimal in rupees (divides by 100).
     * Used ONLY at display and reporting boundaries — never in arithmetic.
     */
    fun toRupees(): BigDecimal = BigDecimal(value).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

    override fun compareTo(other: Paisa): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Paisa(0)

        fun fromRupees(rupees: BigDecimal): Paisa = Paisa(rupees.multiply(BigDecimal(100)).toLong())

        fun fromRupees(rupees: Double): Paisa = Paisa((rupees * 100).roundToLong())
    }
}

package com.kitewatch.domain.model

sealed interface ProfitTarget {
    val displayValue: String

    /**
     * @param basisPoints Target profit as basis points. 500 = 5.00%, 250 = 2.50%.
     */
    data class Percentage(
        val basisPoints: Int,
    ) : ProfitTarget {
        init {
            require(basisPoints >= 0) { "Profit target percentage cannot be negative" }
        }

        override val displayValue: String
            get() {
                val whole = basisPoints / 100
                val decimal = (basisPoints % 100).toString().padStart(2, '0')
                return "$whole.$decimal%"
            }
    }

    /**
     * @param amount Absolute profit target in Paisa. Must be non-negative.
     */
    data class Absolute(
        val amount: Paisa,
    ) : ProfitTarget {
        init {
            require(amount.value >= 0) { "Profit target amount cannot be negative" }
        }

        override val displayValue: String
            get() = "₹${amount.toRupees()}"
    }

    fun computeTargetProfit(investedAmount: Paisa): Paisa =
        when (this) {
            is Percentage -> investedAmount.applyBasisPoints(basisPoints)
            is Absolute -> amount
        }
}

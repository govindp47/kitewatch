package com.kitewatch.feature.transactions.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kitewatch.feature.transactions.TransactionsRoute

const val TRANSACTIONS_ROUTE = "transactions"

fun NavGraphBuilder.transactionsScreen() {
    composable(route = TRANSACTIONS_ROUTE) {
        TransactionsRoute()
    }
}

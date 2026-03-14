package com.kitewatch.feature.holdings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kitewatch.feature.holdings.HoldingsRoute

const val HOLDINGS_ROUTE = "holdings"

fun NavGraphBuilder.holdingsScreen(onNavigateToGtt: () -> Unit) {
    composable(route = HOLDINGS_ROUTE) {
        HoldingsRoute(onNavigateToGtt = onNavigateToGtt)
    }
}

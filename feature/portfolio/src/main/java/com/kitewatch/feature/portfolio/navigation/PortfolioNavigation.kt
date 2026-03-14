package com.kitewatch.feature.portfolio.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kitewatch.feature.portfolio.PortfolioRoute

const val PORTFOLIO_ROUTE = "portfolio"

fun NavGraphBuilder.portfolioScreen() {
    composable(route = PORTFOLIO_ROUTE) {
        PortfolioRoute()
    }
}

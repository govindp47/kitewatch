package com.kitewatch.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kitewatch.feature.gtt.navigation.GTT_ROUTE
import com.kitewatch.feature.gtt.navigation.gttScreen
import com.kitewatch.feature.holdings.navigation.HOLDINGS_ROUTE
import com.kitewatch.feature.holdings.navigation.holdingsScreen
import com.kitewatch.feature.onboarding.navigation.ONBOARDING_ROUTE
import com.kitewatch.feature.onboarding.navigation.onboardingScreen
import com.kitewatch.feature.orders.navigation.ORDERS_ROUTE
import com.kitewatch.feature.orders.navigation.ordersScreen
import com.kitewatch.feature.portfolio.navigation.PORTFOLIO_ROUTE
import com.kitewatch.feature.portfolio.navigation.portfolioScreen
import com.kitewatch.feature.settings.navigation.SETTINGS_ROUTE
import com.kitewatch.feature.settings.navigation.settingsScreen
import com.kitewatch.feature.transactions.navigation.TRANSACTIONS_ROUTE
import com.kitewatch.feature.transactions.navigation.transactionsScreen
import com.kitewatch.infra.auth.BiometricAuthManager

// ── Bottom nav tabs ───────────────────────────────────────────────────────────

private enum class BottomNavTab(
    val route: String,
    val icon: ImageVector,
    val label: String,
) {
    PORTFOLIO(PORTFOLIO_ROUTE, Icons.Outlined.PieChart, "Portfolio"),
    HOLDINGS(HOLDINGS_ROUTE, Icons.Outlined.AccountBalance, "Holdings"),
    ORDERS(ORDERS_ROUTE, Icons.Outlined.Receipt, "Orders"),
    TRANSACTIONS(TRANSACTIONS_ROUTE, Icons.Outlined.SwapVert, "Transactions"),
    SETTINGS(SETTINGS_ROUTE, Icons.Outlined.Settings, "Settings"),
}

private val bottomNavRoutes = BottomNavTab.entries.map { it.route }.toSet()

// ── Onboarding nav host ───────────────────────────────────────────────────────

@Composable
fun OnboardingNavHost(
    biometricAuthManager: BiometricAuthManager,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ONBOARDING_ROUTE,
        modifier = modifier,
    ) {
        onboardingScreen(
            biometricAuthManager = biometricAuthManager,
            onOnboardingComplete = onOnboardingComplete,
        )
    }
}

// ── Main nav host (post-onboarding) ──────────────────────────────────────────

@Suppress("UnusedParameter")
@Composable
fun MainNavHost(
    biometricAuthManager: BiometricAuthManager,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val showBottomBar =
                currentDestination?.hierarchy?.any { it.route in bottomNavRoutes } == true
            if (showBottomBar) {
                NavigationBar {
                    BottomNavTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected =
                                currentDestination
                                    ?.hierarchy
                                    ?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = PORTFOLIO_ROUTE,
            modifier = Modifier.padding(innerPadding),
        ) {
            portfolioScreen()
            holdingsScreen(onNavigateToGtt = { navController.navigate(GTT_ROUTE) })
            ordersScreen()
            transactionsScreen()
            settingsScreen()
            gttScreen()
        }
    }
}

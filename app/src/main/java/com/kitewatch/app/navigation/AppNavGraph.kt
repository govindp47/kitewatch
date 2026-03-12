package com.kitewatch.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

// ── Routes ────────────────────────────────────────────────────────────────────

private const val ROUTE_PORTFOLIO = "portfolio"
private const val ROUTE_HOLDINGS = "holdings"
private const val ROUTE_ORDERS = "orders"
private const val ROUTE_TRANSACTIONS = "transactions"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_GTT = "gtt"
private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_AUTH_LOCK = "auth_lock"

// ── Bottom nav tabs ───────────────────────────────────────────────────────────

private enum class BottomNavTab(
    val route: String,
    val icon: ImageVector,
    val label: String,
) {
    PORTFOLIO(ROUTE_PORTFOLIO, Icons.Outlined.PieChart, "Portfolio"),
    HOLDINGS(ROUTE_HOLDINGS, Icons.Outlined.AccountBalance, "Holdings"),
    ORDERS(ROUTE_ORDERS, Icons.Outlined.Receipt, "Orders"),
    TRANSACTIONS(ROUTE_TRANSACTIONS, Icons.Outlined.SwapVert, "Transactions"),
    SETTINGS(ROUTE_SETTINGS, Icons.Outlined.Settings, "Settings"),
}

// ── Nav host ──────────────────────────────────────────────────────────────────

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val showBottomBar =
                BottomNavTab.entries.any { tab ->
                    currentDestination?.hierarchy?.any { it.route == tab.route } == true
                }
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
            startDestination = ROUTE_PORTFOLIO,
            modifier = Modifier.padding(innerPadding),
        ) {
            // ── Main tabs ─────────────────────────────────────────────────────
            composable(ROUTE_PORTFOLIO) { PlaceholderScreen("Portfolio") }
            composable(ROUTE_HOLDINGS) { PlaceholderScreen("Holdings") }
            composable(ROUTE_ORDERS) { PlaceholderScreen("Orders") }
            composable(ROUTE_TRANSACTIONS) { PlaceholderScreen("Transactions") }
            composable(ROUTE_SETTINGS) { PlaceholderScreen("Settings") }

            // ── Non-tab routes (stubs) ────────────────────────────────────────
            composable(ROUTE_GTT) { PlaceholderScreen("GTT Orders") }
            composable(ROUTE_ONBOARDING) { PlaceholderScreen("Onboarding") }
            composable(ROUTE_AUTH_LOCK) { PlaceholderScreen("Auth Lock") }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name)
    }
}

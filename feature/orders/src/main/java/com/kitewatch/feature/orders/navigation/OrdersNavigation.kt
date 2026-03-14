package com.kitewatch.feature.orders.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kitewatch.feature.orders.OrdersRoute

const val ORDERS_ROUTE = "orders"

fun NavGraphBuilder.ordersScreen() {
    composable(route = ORDERS_ROUTE) {
        OrdersRoute()
    }
}

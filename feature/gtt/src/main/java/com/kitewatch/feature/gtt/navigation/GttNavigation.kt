package com.kitewatch.feature.gtt.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kitewatch.feature.gtt.GttRoute

const val GTT_ROUTE = "gtt"

fun NavGraphBuilder.gttScreen() {
    composable(route = GTT_ROUTE) {
        GttRoute()
    }
}

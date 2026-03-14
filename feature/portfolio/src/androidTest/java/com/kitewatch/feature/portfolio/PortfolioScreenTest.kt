package com.kitewatch.feature.portfolio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kitewatch.feature.portfolio.model.ChargeBreakdownUiModel
import com.kitewatch.feature.portfolio.model.PnlUiModel
import com.kitewatch.feature.portfolio.model.SyncStatusUiModel
import com.kitewatch.ui.component.DateRangePreset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PortfolioScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakePnlSummary =
        PnlUiModel(
            realizedPnl = "₹5,000.00",
            totalCharges = "₹50.00",
            pnlPercentage = "+10.00%",
            totalInvestedValue = "₹50,000.00",
            chargeBreakdownFormatted =
                ChargeBreakdownUiModel(
                    stt = "₹10.00",
                    exchangeTxn = "₹5.00",
                    sebiCharges = "₹1.00",
                    stampDuty = "₹2.00",
                    gst = "₹32.00",
                    total = "₹50.00",
                ),
            isProfit = true,
        )

    private val loadedState =
        PortfolioState(
            isLoading = false,
            pnlSummary = fakePnlSummary,
            fundBalance = "₹10,000.00",
            selectedRange = DateRangePreset.THIS_MONTH,
            lastSyncStatus = SyncStatusUiModel(lastSyncTime = null, lastSyncFailed = false),
        )

    @Test
    fun skeletonLoader_visibleWhenIsLoadingTrue() {
        composeTestRule.setContent {
            PortfolioScreen(
                state = PortfolioState(isLoading = true),
                onIntent = {},
            )
        }
        // When loading, the skeleton is shown and P&L values are NOT visible
        composeTestRule.onNodeWithText("₹5,000.00").assertDoesNotExist()
    }

    @Test
    fun pnlValue_rendersCorrectlyWhenLoaded() {
        composeTestRule.setContent {
            PortfolioScreen(
                state = loadedState,
                onIntent = {},
            )
        }
        composeTestRule.onNodeWithText("₹5,000.00").assertIsDisplayed()
    }

    @Test
    fun dateRangeChip_pressEmitsSelectDateRangeIntent() {
        var capturedIntent: PortfolioIntent? = null
        composeTestRule.setContent {
            PortfolioScreen(
                state = loadedState,
                onIntent = { capturedIntent = it },
            )
        }
        composeTestRule.onNodeWithText("All Time").performClick()
        assertEquals(PortfolioIntent.SelectDateRange(DateRangePreset.ALL_TIME), capturedIntent)
    }

    @Test
    fun setupChecklist_shownWhenFlagIsTrue() {
        composeTestRule.setContent {
            PortfolioScreen(
                state = loadedState.copy(showSetupChecklist = true),
                onIntent = {},
            )
        }
        composeTestRule.onNodeWithText("Get Started").assertIsDisplayed()
    }
}

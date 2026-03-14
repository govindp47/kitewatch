package com.kitewatch.feature.gtt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kitewatch.feature.gtt.model.GttUiModel
import org.junit.Rule
import org.junit.Test

class GttScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun activeModel(id: Long) =
        GttUiModel(
            gttId = id,
            stockCode = "INFY",
            triggerPrice = "₹2,000.00",
            quantity = "10",
            statusLabel = "Active",
            isManualOverride = false,
            lastSynced = "Never",
        )

    private fun overrideModel(id: Long) =
        GttUiModel(
            gttId = id,
            stockCode = "RELIANCE",
            triggerPrice = "₹3,000.00",
            quantity = "5",
            statusLabel = "Manual override",
            isManualOverride = true,
            lastSynced = "Never",
        )

    @Test
    fun `screen renders top bar with GTT Orders title`() {
        composeRule.setContent {
            GttScreen(state = GttState(isLoading = false))
        }
        composeRule.onNodeWithText("GTT Orders").assertIsDisplayed()
    }

    @Test
    fun `empty state renders when no records`() {
        composeRule.setContent {
            GttScreen(state = GttState(isLoading = false, records = emptyList()))
        }
        composeRule.onNodeWithText("No active GTT orders.").assertIsDisplayed()
    }

    @Test
    fun `active record row is displayed`() {
        composeRule.setContent {
            GttScreen(
                state =
                    GttState(
                        isLoading = false,
                        records = listOf(activeModel(1L)),
                    ),
            )
        }
        composeRule.onNodeWithText("INFY").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()
        composeRule.onNodeWithText("₹2,000.00").assertIsDisplayed()
    }

    @Test
    fun `override record shows warning note`() {
        composeRule.setContent {
            GttScreen(
                state =
                    GttState(
                        isLoading = false,
                        records = listOf(overrideModel(2L)),
                    ),
            )
        }
        composeRule.onNodeWithText("Manual override").assertIsDisplayed()
        composeRule.onNodeWithText("Modified outside KiteWatch — review required").assertIsDisplayed()
    }

    @Test
    fun `non-override row does not show override warning`() {
        composeRule.setContent {
            GttScreen(
                state =
                    GttState(
                        isLoading = false,
                        records = listOf(activeModel(1L)),
                    ),
            )
        }
        composeRule.onNodeWithText("Modified outside KiteWatch — review required").assertDoesNotExist()
    }

    @Test
    fun `both active and override records rendered together`() {
        composeRule.setContent {
            GttScreen(
                state =
                    GttState(
                        isLoading = false,
                        records = listOf(activeModel(1L), overrideModel(2L)),
                    ),
            )
        }
        composeRule.onNodeWithText("INFY").assertIsDisplayed()
        composeRule.onNodeWithText("RELIANCE").assertIsDisplayed()
        composeRule.onNodeWithText("Modified outside KiteWatch — review required").assertIsDisplayed()
    }

    @Test
    fun `no create or edit controls visible`() {
        composeRule.setContent {
            GttScreen(
                state =
                    GttState(
                        isLoading = false,
                        records = listOf(activeModel(1L)),
                    ),
            )
        }
        // No buttons with these labels should exist
        composeRule.onNodeWithText("Create").assertDoesNotExist()
        composeRule.onNodeWithText("Edit").assertDoesNotExist()
        composeRule.onNodeWithText("Add").assertDoesNotExist()
    }
}

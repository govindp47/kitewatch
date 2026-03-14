package com.kitewatch.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitewatch.domain.model.Paisa
import com.kitewatch.ui.theme.KiteWatchTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdditionalComponentsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // ── CurrencyText ──────────────────────────────────────────────────────────

    @Test
    fun currencyText_positive_displaysFormattedValue() {
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                CurrencyText(paisa = Paisa(50_000L)) // ₹500.00
            }
        }
        composeTestRule.onNodeWithText("₹500.00").assertIsDisplayed()
    }

    @Test
    fun currencyText_negative_displaysFormattedValue() {
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                CurrencyText(paisa = Paisa(-50_000L)) // -₹500.00
            }
        }
        composeTestRule.onNodeWithText("-₹500.00").assertIsDisplayed()
    }

    // ── DateRangeSelector ────────────────────────────────────────────────────

    @Test
    fun dateRangeSelector_chipTap_callsOnRangeSelected() {
        var selected: DateRangePreset? = null
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                DateRangeSelector(
                    selectedRange = DateRangePreset.TODAY,
                    onRangeSelected = { selected = it },
                )
            }
        }
        composeTestRule.onNodeWithText("This Month").performClick()
        assertEquals(DateRangePreset.THIS_MONTH, selected)
    }

    @Test
    fun dateRangeSelector_allChipsVisible() {
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                DateRangeSelector(
                    selectedRange = DateRangePreset.TODAY,
                    onRangeSelected = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("All Time").assertIsDisplayed()
        composeTestRule.onNodeWithText("Custom").assertIsDisplayed()
    }

    // ── PaginatedLazyColumn ───────────────────────────────────────────────────

    @Test
    fun paginatedLazyColumn_emptyData_showsEmptyState() {
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                val pagingItems =
                    flowOf(PagingData.from(emptyList<String>()))
                        .collectAsLazyPagingItems()
                PaginatedLazyColumn(
                    pagingItems = pagingItems,
                    itemContent = { item -> androidx.compose.material3.Text(item) },
                    emptyState = { androidx.compose.material3.Text("Nothing here") },
                    errorState = { androidx.compose.material3.Text("Error occurred") },
                )
            }
        }
        composeTestRule.onNodeWithText("Nothing here").assertIsDisplayed()
    }

    // ── SetupChecklist ───────────────────────────────────────────────────────

    @Test
    fun setupChecklist_incompleteItem_showsActionButton() {
        var clicked = false
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                SetupChecklist(
                    items =
                        listOf(
                            ChecklistItem(
                                label = "Fetch charge rates",
                                isComplete = false,
                                actionLabel = "Go",
                                onAction = { clicked = true },
                            ),
                        ),
                )
            }
        }
        composeTestRule.onNodeWithText("Go").performClick()
        assertTrue(clicked)
    }

    @Test
    fun setupChecklist_completedItem_hidesActionButton() {
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                SetupChecklist(
                    items =
                        listOf(
                            ChecklistItem(
                                label = "Fetch charge rates",
                                isComplete = true,
                                actionLabel = "Go",
                                onAction = {},
                            ),
                        ),
                )
            }
        }
        // Action button should not appear for completed items
        composeTestRule.onNodeWithText("Go").assertDoesNotExist()
    }

    // ── ErrorStateWidget ──────────────────────────────────────────────────────

    @Test
    fun errorStateWidget_displaysMessage() {
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                ErrorStateWidget(message = "Something went wrong")
            }
        }
        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
    }

    @Test
    fun errorStateWidget_withRetry_showsRetryButton() {
        var retried = false
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                ErrorStateWidget(
                    message = "Network error",
                    onRetry = { retried = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }
}

package com.kitewatch.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun alertBanner_displaysMessage() {
        composeTestRule.setContent {
            AlertBanner(message = "Test error message", type = AlertType.Error)
        }
        composeTestRule.onNodeWithText("Test error message").assertIsDisplayed()
    }

    @Test
    fun confirmationDialog_callsOnConfirmWhenConfirmTapped() {
        var confirmed = false
        composeTestRule.setContent {
            ConfirmationDialog(
                state =
                    ConfirmationDialogState(
                        title = "Delete",
                        message = "Are you sure?",
                        confirmLabel = "Yes",
                        cancelLabel = "No",
                        onConfirm = { confirmed = true },
                        onCancel = {},
                    ),
            )
        }
        composeTestRule.onNodeWithText("Yes").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun confirmationDialog_callsOnCancelWhenCancelTapped() {
        var cancelled = false
        composeTestRule.setContent {
            ConfirmationDialog(
                state =
                    ConfirmationDialogState(
                        title = "Delete",
                        message = "Are you sure?",
                        confirmLabel = "Yes",
                        cancelLabel = "No",
                        onConfirm = {},
                        onCancel = { cancelled = true },
                    ),
            )
        }
        composeTestRule.onNodeWithText("No").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun filterChipGroup_callsOnSelectWithCorrectItem() {
        val options = listOf("Daily", "Weekly", "Monthly")
        var selected = ""
        composeTestRule.setContent {
            FilterChipGroup(
                options = options,
                selectedOption = "Daily",
                onSelect = { selected = it },
                labelFor = { it },
            )
        }
        composeTestRule.onNodeWithText("Weekly").performClick()
        assertTrue(selected == "Weekly")
    }

    @Test
    fun emptyStateWidget_displaysTitle() {
        composeTestRule.setContent {
            EmptyStateWidget(
                title = "Nothing here",
                description = "No items found",
            )
        }
        composeTestRule.onNodeWithText("Nothing here").assertIsDisplayed()
        composeTestRule.onNodeWithText("No items found").assertIsDisplayed()
    }

    @Test
    fun emptyStateWidget_actionButtonVisible_whenProvided() {
        var clicked = false
        composeTestRule.setContent {
            EmptyStateWidget(
                title = "Empty",
                description = "No data",
                actionLabel = "Sync Now",
                onAction = { clicked = true },
            )
        }
        composeTestRule.onNodeWithText("Sync Now").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sync Now").performClick()
        assertTrue(clicked)
    }
}

package com.kitewatch.feature.transactions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.kitewatch.domain.model.TransactionType
import com.kitewatch.feature.transactions.model.TransactionUiModel
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class TransactionsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun makeCreditModel(id: Long) =
        TransactionUiModel(
            transactionId = id,
            date = "01 Jan 2025",
            typeLabel = "Fund Deposit",
            stockCode = null,
            amount = "₹1,000.00",
            description = "Bank transfer",
            isCredit = true,
        )

    private fun makeDebitModel(id: Long) =
        TransactionUiModel(
            transactionId = id,
            date = "02 Jan 2025",
            typeLabel = "Buy",
            stockCode = "INFY",
            amount = "₹50,000.00",
            description = "Equity purchase",
            isCredit = false,
        )

    @Test
    fun `screen renders top bar with Transactions title`() {
        composeRule.setContent {
            val pagingItems = flowOf(PagingData.empty<TransactionUiModel>()).collectAsLazyPagingItems()
            TransactionsScreen(
                state = TransactionsState(),
                pagingItems = pagingItems,
                onIntent = {},
            )
        }
        composeRule.onNodeWithText("Transactions").assertIsDisplayed()
    }

    @Test
    fun `filter chips render all options`() {
        composeRule.setContent {
            val pagingItems = flowOf(PagingData.empty<TransactionUiModel>()).collectAsLazyPagingItems()
            TransactionsScreen(
                state = TransactionsState(),
                pagingItems = pagingItems,
                onIntent = {},
            )
        }
        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithText("Buy").assertIsDisplayed()
        composeRule.onNodeWithText("Sell").assertIsDisplayed()
        composeRule.onNodeWithText("Fund Credit").assertIsDisplayed()
        composeRule.onNodeWithText("Fund Debit").assertIsDisplayed()
    }

    @Test
    fun `credit transaction row is displayed`() {
        val item = makeCreditModel(1L)
        composeRule.setContent {
            val pagingItems =
                flowOf(PagingData.from(listOf(item))).collectAsLazyPagingItems()
            TransactionsScreen(
                state = TransactionsState(),
                pagingItems = pagingItems,
                onIntent = {},
            )
        }
        composeRule.onNodeWithText("Fund Deposit").assertIsDisplayed()
        composeRule.onNodeWithText("₹1,000.00").assertIsDisplayed()
    }

    @Test
    fun `debit transaction row is displayed`() {
        val item = makeDebitModel(2L)
        composeRule.setContent {
            val pagingItems =
                flowOf(PagingData.from(listOf(item))).collectAsLazyPagingItems()
            TransactionsScreen(
                state = TransactionsState(),
                pagingItems = pagingItems,
                onIntent = {},
            )
        }
        composeRule.onNodeWithText("Buy").assertIsDisplayed()
        composeRule.onNodeWithText("₹50,000.00").assertIsDisplayed()
        composeRule.onNodeWithText("· INFY").assertIsDisplayed()
    }

    @Test
    fun `tapping Buy chip emits FilterByType EQUITY_BUY intent`() {
        var emittedIntent: TransactionsIntent? = null
        composeRule.setContent {
            val pagingItems = flowOf(PagingData.empty<TransactionUiModel>()).collectAsLazyPagingItems()
            TransactionsScreen(
                state = TransactionsState(),
                pagingItems = pagingItems,
                onIntent = { emittedIntent = it },
            )
        }
        composeRule.onNodeWithText("Buy").performClick()
        assert(emittedIntent is TransactionsIntent.FilterByType)
        assert((emittedIntent as TransactionsIntent.FilterByType).type == TransactionType.EQUITY_BUY)
    }

    @Test
    fun `tapping Fund Credit chip emits FilterByType FUND_DEPOSIT intent`() {
        var emittedIntent: TransactionsIntent? = null
        composeRule.setContent {
            val pagingItems = flowOf(PagingData.empty<TransactionUiModel>()).collectAsLazyPagingItems()
            TransactionsScreen(
                state = TransactionsState(),
                pagingItems = pagingItems,
                onIntent = { emittedIntent = it },
            )
        }
        composeRule.onNodeWithText("Fund Credit").performClick()
        assert(emittedIntent is TransactionsIntent.FilterByType)
        assert((emittedIntent as TransactionsIntent.FilterByType).type == TransactionType.FUND_DEPOSIT)
    }

    @Test
    fun `empty state displays no transactions message`() {
        composeRule.setContent {
            val pagingItems = flowOf(PagingData.empty<TransactionUiModel>()).collectAsLazyPagingItems()
            TransactionsScreen(
                state = TransactionsState(),
                pagingItems = pagingItems,
                onIntent = {},
            )
        }
        composeRule.onNodeWithText("No transactions recorded.").assertIsDisplayed()
    }

    @Test
    fun `tapping All chip emits FilterByType null intent`() {
        var emittedIntent: TransactionsIntent? = null
        composeRule.setContent {
            val pagingItems = flowOf(PagingData.empty<TransactionUiModel>()).collectAsLazyPagingItems()
            // Start with a filter selected
            TransactionsScreen(
                state = TransactionsState(selectedType = TransactionType.EQUITY_BUY),
                pagingItems = pagingItems,
                onIntent = { emittedIntent = it },
            )
        }
        composeRule.onNodeWithText("All").performClick()
        assert(emittedIntent is TransactionsIntent.FilterByType)
        assert((emittedIntent as TransactionsIntent.FilterByType).type == null)
    }
}

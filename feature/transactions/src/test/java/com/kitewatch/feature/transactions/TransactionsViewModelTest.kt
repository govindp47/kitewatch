package com.kitewatch.feature.transactions

import androidx.paging.PagingData
import com.kitewatch.domain.model.TransactionType
import com.kitewatch.feature.transactions.usecase.GetTransactionsPagedUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val getTransactionsPagedUseCase: GetTransactionsPagedUseCase = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getTransactionsPagedUseCase.execute(null) } returns flowOf(PagingData.empty())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = TransactionsViewModel(getTransactionsPagedUseCase)

    @Test
    fun `initial state has no filter selected`() {
        val vm = buildViewModel()
        assertEquals(null, vm.state.value.selectedType)
    }

    @Test
    fun `FilterByType EQUITY_BUY updates selectedType`() =
        runTest {
            every { getTransactionsPagedUseCase.execute(TransactionType.EQUITY_BUY) } returns flowOf(PagingData.empty())
            val vm = buildViewModel()

            vm.processIntent(TransactionsIntent.FilterByType(TransactionType.EQUITY_BUY))
            advanceUntilIdle()

            assertEquals(TransactionType.EQUITY_BUY, vm.state.value.selectedType)
        }

    @Test
    fun `FilterByType EQUITY_SELL updates selectedType`() =
        runTest {
            every { getTransactionsPagedUseCase.execute(TransactionType.EQUITY_SELL) } returns
                flowOf(PagingData.empty())
            val vm = buildViewModel()

            vm.processIntent(TransactionsIntent.FilterByType(TransactionType.EQUITY_SELL))
            advanceUntilIdle()

            assertEquals(TransactionType.EQUITY_SELL, vm.state.value.selectedType)
        }

    @Test
    fun `FilterByType FUND_DEPOSIT updates selectedType`() =
        runTest {
            every { getTransactionsPagedUseCase.execute(TransactionType.FUND_DEPOSIT) } returns
                flowOf(PagingData.empty())
            val vm = buildViewModel()

            vm.processIntent(TransactionsIntent.FilterByType(TransactionType.FUND_DEPOSIT))
            advanceUntilIdle()

            assertEquals(TransactionType.FUND_DEPOSIT, vm.state.value.selectedType)
        }

    @Test
    fun `FilterByType null clears the filter`() =
        runTest {
            every { getTransactionsPagedUseCase.execute(TransactionType.EQUITY_SELL) } returns
                flowOf(PagingData.empty())
            val vm = buildViewModel()

            vm.processIntent(TransactionsIntent.FilterByType(TransactionType.EQUITY_SELL))
            advanceUntilIdle()

            vm.processIntent(TransactionsIntent.FilterByType(null))
            advanceUntilIdle()

            assertEquals(null, vm.state.value.selectedType)
        }

    @Test
    fun `FilterByType FUND_WITHDRAWAL updates selectedType`() =
        runTest {
            every { getTransactionsPagedUseCase.execute(TransactionType.FUND_WITHDRAWAL) } returns
                flowOf(PagingData.empty())
            val vm = buildViewModel()

            vm.processIntent(TransactionsIntent.FilterByType(TransactionType.FUND_WITHDRAWAL))
            advanceUntilIdle()

            assertEquals(TransactionType.FUND_WITHDRAWAL, vm.state.value.selectedType)
        }

    @Test
    fun `multiple sequential filter changes update state correctly`() =
        runTest {
            every { getTransactionsPagedUseCase.execute(TransactionType.EQUITY_BUY) } returns flowOf(PagingData.empty())
            every { getTransactionsPagedUseCase.execute(TransactionType.FUND_DEPOSIT) } returns
                flowOf(PagingData.empty())
            val vm = buildViewModel()

            vm.processIntent(TransactionsIntent.FilterByType(TransactionType.EQUITY_BUY))
            advanceUntilIdle()
            assertEquals(TransactionType.EQUITY_BUY, vm.state.value.selectedType)

            vm.processIntent(TransactionsIntent.FilterByType(TransactionType.FUND_DEPOSIT))
            advanceUntilIdle()
            assertEquals(TransactionType.FUND_DEPOSIT, vm.state.value.selectedType)
        }
}

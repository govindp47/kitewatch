package com.kitewatch.feature.transactions

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.Transaction
import com.kitewatch.domain.model.TransactionSource
import com.kitewatch.domain.model.TransactionType
import com.kitewatch.domain.repository.TransactionRepository
import com.kitewatch.feature.transactions.usecase.TransactionListPagingSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransactionListPagingSourceTest {
    private val repository: TransactionRepository = mockk()

    private fun makeTransaction(
        id: Long,
        daysAgo: Long = id,
    ) = Transaction(
        transactionId = id,
        type = TransactionType.EQUITY_BUY,
        referenceId = null,
        stockCode = "INFY",
        amount = Paisa(10_000_00L),
        transactionDate = LocalDate.of(2025, 1, 1).plusDays(100 - daysAgo),
        description = "txn $id",
        source = TransactionSource.SYNC,
    )

    private fun testConfig() =
        PagingConfig(
            pageSize = 50,
            initialLoadSize = 50, // override default 3× to control page size in tests
            enablePlaceholders = false,
        )

    @Test
    fun `first page returns up to pageSize items`() =
        runTest {
            val transactions = (1L..120L).map { makeTransaction(it) }
            every { repository.observeAll() } returns flowOf(transactions)

            val source = TransactionListPagingSource(repository, null)
            val pager = TestPager(config = testConfig(), pagingSource = source)

            val page = pager.refresh() as PagingSource.LoadResult.Page
            assertEquals(50, page.data.size)
        }

    @Test
    fun `transactions sorted descending by date`() =
        runTest {
            val transactions = (1L..10L).map { makeTransaction(it) }
            every { repository.observeAll() } returns flowOf(transactions)

            val source = TransactionListPagingSource(repository, null)
            val pager = TestPager(config = testConfig(), pagingSource = source)

            val page = pager.refresh() as PagingSource.LoadResult.Page
            val dates = page.data.map { it.transactionDate }
            assertEquals(dates, dates.sortedDescending())
        }

    @Test
    fun `no duplicates across pages`() =
        runTest {
            val transactions = (1L..120L).map { makeTransaction(it) }
            every { repository.observeAll() } returns flowOf(transactions)

            val source = TransactionListPagingSource(repository, null)
            val pager = TestPager(config = testConfig(), pagingSource = source)

            pager.refresh()
            pager.append()
            pager.append()

            val allItems = pager.getPages().flatMap { it.data }
            val ids = allItems.map { it.transactionId }
            assertEquals(ids.size, ids.toSet().size)
        }

    @Test
    fun `empty list returns empty page with no next key`() =
        runTest {
            every { repository.observeAll() } returns flowOf(emptyList())

            val source = TransactionListPagingSource(repository, null)
            val pager = TestPager(config = testConfig(), pagingSource = source)

            val page = pager.refresh() as PagingSource.LoadResult.Page
            assertTrue(page.data.isEmpty())
            assertNull(page.nextKey)
        }

    @Test
    fun `filter by type excludes other types`() =
        runTest {
            val buyTxn = makeTransaction(1L).copy(type = TransactionType.EQUITY_BUY)
            val sellTxn = makeTransaction(2L).copy(type = TransactionType.EQUITY_SELL)
            every { repository.observeByType(TransactionType.EQUITY_BUY) } returns flowOf(listOf(buyTxn))

            val source = TransactionListPagingSource(repository, TransactionType.EQUITY_BUY)
            val pager = TestPager(config = testConfig(), pagingSource = source)

            val page = pager.refresh() as PagingSource.LoadResult.Page
            assertEquals(1, page.data.size)
            assertEquals(TransactionType.EQUITY_BUY, page.data[0].type)
            assertTrue(page.data.none { it.transactionId == sellTxn.transactionId })
        }
}

package com.kitewatch.feature.orders

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.OrderRepository
import com.kitewatch.feature.orders.usecase.OrderListPagingSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OrderListPagingSourceTest {
    private val repo: OrderRepository = mockk()

    private fun makeOrders(count: Int): List<Order> =
        (1..count).map { i ->
            Order(
                orderId = i.toLong(),
                zerodhaOrderId = "ZRD-$i",
                stockCode = "STOCK$i",
                stockName = "Stock $i",
                orderType = if (i % 2 == 0) OrderType.SELL else OrderType.BUY,
                quantity = 5,
                price = Paisa(100_00L),
                totalValue = Paisa(500_00L),
                tradeDate = LocalDate.of(2026, 1, i.coerceAtMost(28)),
                exchange = Exchange.NSE,
                settlementId = null,
                source = OrderSource.SYNC,
            )
        }

    /** Config with initialLoadSize == pageSize to avoid oversized first-page loads in tests. */
    private fun testConfig(pageSize: Int = 50) =
        PagingConfig(
            pageSize = pageSize,
            initialLoadSize = pageSize,
            enablePlaceholders = false,
        )

    @Test
    fun `first page loads page-size items and provides nextKey`() =
        runTest {
            val orders = makeOrders(120)
            coEvery { repo.getAll() } returns orders

            val pager = TestPager(config = testConfig(), pagingSource = OrderListPagingSource(repo))

            val page = pager.refresh() as PagingSource.LoadResult.Page
            assertEquals(50, page.data.size)
            assertNull(page.prevKey)
            assertEquals(1, page.nextKey)
        }

    @Test
    fun `subsequent pages load without duplication`() =
        runTest {
            val orders = makeOrders(120)
            coEvery { repo.getAll() } returns orders

            val pager = TestPager(config = testConfig(), pagingSource = OrderListPagingSource(repo))

            val page1 = pager.refresh() as PagingSource.LoadResult.Page
            assertEquals(50, page1.data.size)

            val page2Result = pager.append()
            assertTrue(page2Result is PagingSource.LoadResult.Page)
            val page2 = page2Result as PagingSource.LoadResult.Page
            assertEquals(50, page2.data.size)

            val page3Result = pager.append()
            assertTrue(page3Result is PagingSource.LoadResult.Page)
            val page3 = page3Result as PagingSource.LoadResult.Page
            assertEquals(20, page3.data.size) // 120 - 50 - 50 = 20
            assertNull(page3.nextKey) // last page has no nextKey
        }

    @Test
    fun `no duplicates across pages — orderId set stays the same size`() =
        runTest {
            val orders = makeOrders(120)
            coEvery { repo.getAll() } returns orders

            val pager = TestPager(config = testConfig(), pagingSource = OrderListPagingSource(repo))

            val allIds = mutableListOf<Long>()
            val page1 = pager.refresh() as PagingSource.LoadResult.Page
            allIds += page1.data.map { it.orderId }
            val page2 = pager.append() as PagingSource.LoadResult.Page
            allIds += page2.data.map { it.orderId }
            val page3 = pager.append() as PagingSource.LoadResult.Page
            allIds += page3.data.map { it.orderId }

            assertEquals(120, allIds.size)
            assertEquals(120, allIds.toSet().size) // no duplicates
        }

    @Test
    fun `empty list returns empty page with no next key`() =
        runTest {
            coEvery { repo.getAll() } returns emptyList()

            val pager = TestPager(config = testConfig(), pagingSource = OrderListPagingSource(repo))

            val page = pager.refresh() as PagingSource.LoadResult.Page
            assertEquals(0, page.data.size)
            assertNull(page.nextKey)
        }

    @Test
    fun `orders returned in descending trade date order`() =
        runTest {
            val orders = makeOrders(3)
            coEvery { repo.getAll() } returns orders

            val pager = TestPager(config = testConfig(), pagingSource = OrderListPagingSource(repo))

            val page = pager.refresh() as PagingSource.LoadResult.Page
            val dates = page.data.map { it.tradeDate }
            assertEquals(dates.sortedDescending(), dates)
        }
}

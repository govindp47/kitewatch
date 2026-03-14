package com.kitewatch.feature.orders.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns a [Flow<PagingData<Order>>] for the Orders screen.
 *
 * Uses a list-backed [PagingSource] since [OrderRepository] is a pure-domain interface
 * without Room coupling. Each refresh fetches the full sorted list and slices pages.
 */
class GetOrdersPagedUseCase
    @Inject
    constructor(
        private val orderRepository: OrderRepository,
    ) {
        fun execute(): Flow<PagingData<Order>> =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
                pagingSourceFactory = { OrderListPagingSource(orderRepository) },
            ).flow

        private companion object {
            const val PAGE_SIZE = 50
        }
    }

internal class OrderListPagingSource(
    private val orderRepository: OrderRepository,
) : PagingSource<Int, Order>() {
    // Cached on first load within a single PagingSource lifetime.
    private var cachedOrders: List<Order>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Order> =
        runCatching {
            val orders =
                cachedOrders ?: orderRepository
                    .getAll()
                    .sortedByDescending { it.tradeDate }
                    .also { cachedOrders = it }

            val page = params.key ?: 0
            val from = page * params.loadSize
            val slice = orders.drop(from).take(params.loadSize)

            LoadResult.Page(
                data = slice,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (from + slice.size >= orders.size) null else page + 1,
            )
        }.getOrElse { t -> LoadResult.Error(t) }

    override fun getRefreshKey(state: PagingState<Int, Order>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}

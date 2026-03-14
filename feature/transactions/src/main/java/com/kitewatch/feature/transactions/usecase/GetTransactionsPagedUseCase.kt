package com.kitewatch.feature.transactions.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.kitewatch.domain.model.Transaction
import com.kitewatch.domain.model.TransactionType
import com.kitewatch.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetTransactionsPagedUseCase
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
    ) {
        fun execute(filterType: TransactionType? = null): Flow<PagingData<Transaction>> =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
                pagingSourceFactory = { TransactionListPagingSource(transactionRepository, filterType) },
            ).flow

        private companion object {
            const val PAGE_SIZE = 50
        }
    }

internal class TransactionListPagingSource(
    private val transactionRepository: TransactionRepository,
    private val filterType: TransactionType?,
) : PagingSource<Int, Transaction>() {
    private var cachedTransactions: List<Transaction>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Transaction> =
        runCatching {
            val transactions =
                cachedTransactions ?: run {
                    val source =
                        if (filterType == null) {
                            transactionRepository.observeAll()
                        } else {
                            transactionRepository.observeByType(filterType)
                        }
                    source.first().sortedByDescending { it.transactionDate }.also { cachedTransactions = it }
                }

            val page = params.key ?: 0
            val from = page * params.loadSize
            val slice = transactions.drop(from).take(params.loadSize)

            LoadResult.Page(
                data = slice,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (from + slice.size >= transactions.size) null else page + 1,
            )
        }.getOrElse { t -> LoadResult.Error(t) }

    override fun getRefreshKey(state: PagingState<Int, Transaction>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}

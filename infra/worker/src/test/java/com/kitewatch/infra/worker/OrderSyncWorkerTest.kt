package com.kitewatch.infra.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.SyncResult
import com.kitewatch.domain.repository.SyncEventRepository
import com.kitewatch.domain.usecase.AppException
import com.kitewatch.domain.usecase.SyncOrdersUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrderSyncWorkerTest {
    private val syncOrdersUseCase = mockk<SyncOrdersUseCase>()
    private val syncEventRepo = mockk<SyncEventRepository>(relaxed = true)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        coEvery { syncEventRepo.beginEvent(any(), any(), any()) } returns 1L
        coEvery { syncEventRepo.finishEvent(any(), any(), any(), any(), any()) } returns Unit
    }

    private fun buildWorker(): OrderSyncWorker =
        TestListenableWorkerBuilder<OrderSyncWorker>(context)
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: androidx.work.WorkerParameters,
                    ) = OrderSyncWorker(appContext, workerParameters, syncOrdersUseCase, syncEventRepo)
                },
            ).build()

    // ── Success path ──────────────────────────────────────────────────────────

    @Test
    fun `success result returns Result_success`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns
                kotlin.Result.success(SyncResult.Success(newOrderCount = 3, updatedGttCount = 1))

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            coVerify(exactly = 1) { syncEventRepo.finishEvent(1L, any(), "SUCCESS", any(), null) }
        }

    @Test
    fun `NoNewOrders returns Result_success`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns kotlin.Result.success(SyncResult.NoNewOrders)

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.success(), result)
        }

    @Test
    fun `Skipped returns Result_success`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns
                kotlin.Result.success(SyncResult.Skipped("Weekend — no trading day"))

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.success(), result)
        }

    // ── NetworkError → retry ──────────────────────────────────────────────────

    @Test
    fun `NetworkError_NoConnection returns Result_retry`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns
                kotlin.Result.failure(AppException(AppError.NetworkError.NoConnection))

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            coVerify(exactly = 1) { syncEventRepo.finishEvent(1L, any(), "RETRYING", null, any()) }
        }

    @Test
    fun `NetworkError_Timeout returns Result_retry`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns
                kotlin.Result.failure(AppException(AppError.NetworkError.Timeout))

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `NetworkError_HttpError returns Result_retry`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns
                kotlin.Result.failure(AppException(AppError.NetworkError.HttpError(500, "Internal Server Error")))

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    // ── Domain / non-retriable errors → failure ───────────────────────────────

    @Test
    fun `HoldingsMismatch returns Result_failure`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns
                kotlin.Result.failure(
                    AppException(AppError.DomainError.HoldingsMismatch(listOf("INFY: remote=10 local=8"))),
                )

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            coVerify(exactly = 1) { syncEventRepo.finishEvent(1L, any(), "FAILED", null, any()) }
        }

    @Test
    fun `HoldingsFetchFailed returns Result_failure`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns
                kotlin.Result.failure(AppException(AppError.DomainError.HoldingsFetchFailed))

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
        }

    // ── Audit log bracketing ──────────────────────────────────────────────────

    @Test
    fun `beginEvent is always called before doWork returns`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns kotlin.Result.success(SyncResult.NoNewOrders)

            buildWorker().doWork()

            coVerify(exactly = 1) {
                syncEventRepo.beginEvent(OrderSyncWorker.EVENT_TYPE, any(), OrderSyncWorker.WORKER_TAG)
            }
        }

    @Test
    fun `finishEvent is called even on network error`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns
                kotlin.Result.failure(AppException(AppError.NetworkError.NoConnection))

            buildWorker().doWork()

            coVerify(exactly = 1) { syncEventRepo.finishEvent(any(), any(), any(), any(), any()) }
        }
}

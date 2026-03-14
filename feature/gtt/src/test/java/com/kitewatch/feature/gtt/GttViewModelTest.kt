package com.kitewatch.feature.gtt

import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.domain.model.Paisa
import com.kitewatch.feature.gtt.usecase.GetActiveGttRecordsUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GttViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val getActiveGttRecordsUseCase: GetActiveGttRecordsUseCase = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeRecord(
        id: Long,
        status: GttStatus = GttStatus.ACTIVE,
    ) = GttRecord(
        gttId = id,
        zerodhaGttId = "GTT-$id",
        stockCode = "INFY",
        triggerPrice = Paisa(2000_00L),
        quantity = 10,
        status = status,
        isAppManaged = true,
        lastSyncedAt = null,
    )

    private fun buildViewModel() = GttViewModel(getActiveGttRecordsUseCase)

    @Test
    fun `initial state is loading`() {
        every { getActiveGttRecordsUseCase.execute() } returns flowOf(emptyList())
        val vm = buildViewModel()
        // isLoading starts true; after first emission from flowOf it becomes false
        // We check before advancing to verify initial state
        assertTrue(vm.state.value.isLoading)
    }

    @Test
    fun `empty list sets isLoading false and empty records`() =
        runTest {
            every { getActiveGttRecordsUseCase.execute() } returns flowOf(emptyList())
            val vm = buildViewModel()
            advanceUntilIdle()

            assertFalse(vm.state.value.isLoading)
            assertTrue(
                vm.state.value.records
                    .isEmpty(),
            )
        }

    @Test
    fun `active records are mapped to ui models`() =
        runTest {
            every { getActiveGttRecordsUseCase.execute() } returns flowOf(listOf(makeRecord(1L)))
            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(1, vm.state.value.records.size)
            assertEquals(
                "INFY",
                vm.state.value.records[0]
                    .stockCode,
            )
            assertFalse(
                vm.state.value.records[0]
                    .isManualOverride,
            )
        }

    @Test
    fun `MANUAL_OVERRIDE_DETECTED record sets isManualOverride true`() =
        runTest {
            every { getActiveGttRecordsUseCase.execute() } returns
                flowOf(listOf(makeRecord(1L, GttStatus.MANUAL_OVERRIDE_DETECTED)))
            val vm = buildViewModel()
            advanceUntilIdle()

            assertTrue(
                vm.state.value.records[0]
                    .isManualOverride,
            )
        }

    @Test
    fun `unacknowledgedOverrides count reflects override records`() =
        runTest {
            every { getActiveGttRecordsUseCase.execute() } returns
                flowOf(
                    listOf(
                        makeRecord(1L, GttStatus.ACTIVE),
                        makeRecord(2L, GttStatus.MANUAL_OVERRIDE_DETECTED),
                        makeRecord(3L, GttStatus.MANUAL_OVERRIDE_DETECTED),
                    ),
                )
            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(2, vm.state.value.unacknowledgedOverrides)
        }

    @Test
    fun `multiple records all mapped`() =
        runTest {
            every { getActiveGttRecordsUseCase.execute() } returns
                flowOf(listOf(makeRecord(1L), makeRecord(2L), makeRecord(3L)))
            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(3, vm.state.value.records.size)
        }

    @Test
    fun `zero overrides when all records are ACTIVE`() =
        runTest {
            every { getActiveGttRecordsUseCase.execute() } returns
                flowOf(listOf(makeRecord(1L, GttStatus.ACTIVE), makeRecord(2L, GttStatus.ACTIVE)))
            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(0, vm.state.value.unacknowledgedOverrides)
        }
}

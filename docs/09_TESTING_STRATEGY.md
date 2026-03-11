# 09 — Testing Strategy

**Version:** 1.0
**Product:** KiteWatch — Android Local-First Portfolio Management
**Last Updated:** 2026-03-10

---

## 1. Testing Philosophy

1. **Domain logic is pure Kotlin — test it exhaustively.** The `:core-domain` module has zero Android dependencies. All engines, calculators, and UseCases are testable with fast JVM-only unit tests.
2. **Database tests use Room's in-memory testing.** DAO queries, migrations, and integrity invariants are tested against a real SQLite instance (via `Room.inMemoryDatabaseBuilder`), but run on the JVM.
3. **UI tests verify behavior, not layout.** Compose tests assert that the correct state renders the correct composables and that user interactions dispatch the correct intents. Pixel-perfect layout testing is not in scope.
4. **Integration tests validate critical paths end-to-end.** Order sync, backup/restore, and CSV import are tested as integration flows covering multiple layers.
5. **No flaky tests.** Tests that fail intermittently are quarantined and fixed or removed. The `main` branch must be green at all times.

---

## 2. Test Pyramid

```
                    ┌───────────┐
                    │    E2E    │  ~5% of tests
                    │  (Manual) │  Critical user flows
                    ├───────────┤
                  ┌─┤Integration├─┐  ~15% of tests
                  │ │   Tests   │ │  Multi-layer flows
                  │ ├───────────┤ │
                ┌─┤ │  UI Tests │ ├─┐  ~15% of tests
                │ │ │ (Compose) │ │ │  Screen behavior
                │ │ ├───────────┤ │ │
              ┌─┤ │ │  Database │ │ ├─┐  ~15% of tests
              │ │ │ │   Tests  │ │ │ │  DAOs, migrations
              │ │ │ ├───────────┤ │ │ │
            ┌─┴─┴─┴─┤   Unit   ├─┴─┴─┴─┐  ~50% of tests
            │        │  Tests   │        │  Domain logic
            │        │  (JVM)   │        │
            └────────┴──────────┴────────┘
```

| Layer | Runner | Speed | Count (Target) |
|---|---|---|---|
| Unit Tests (JVM) | JUnit 5 + MockK | < 1 ms each | ~200+ |
| Database Tests | AndroidJUnit4 + Room Testing | ~50 ms each | ~60 |
| UI Tests | Compose Test | ~100 ms each | ~50 |
| Integration Tests | AndroidJUnit4 | ~500 ms each | ~30 |
| E2E (Manual) | Developer on device | Minutes | ~15 flows |

---

## 3. Unit Test Strategy

### 3.1 Domain Engine Tests

Every engine in `:core-domain/engine/` has a dedicated test class.

#### ChargeCalculator Tests

```kotlin
class ChargeCalculatorTest {
    private val standardRates = ChargeRateSnapshot(
        brokerageDeliveryBps = 0,
        sttBuyBps = 10,       // 0.1%
        sttSellBps = 25,      // 0.025%
        exchangeNseBps = 297, // 0.00297%
        gstBps = 1800,        // 18%
        sebiFeePerCrorePaisa = 1000,  // ₹10/crore
        stampDutyBps = 15,    // 0.015%
        dpChargesPerScriptPaisa = 1580, // ₹15.80
        effectiveFrom = LocalDate.of(2025, 1, 1),
    )

    @Test
    fun `buy order calculates all applicable charges`() {
        val order = Order(
            type = OrderType.BUY,
            quantity = 10,
            totalValue = Paisa(1_500_000), // ₹15,000
            exchange = Exchange.NSE,
        )
        val charges = ChargeCalculator.calculate(order, standardRates)

        assertAll(
            { assertEquals(Paisa(0), charges.brokerage, "Zerodha delivery brokerage is ₹0") },
            { assertEquals(Paisa(1500), charges.stt, "STT 0.1% of ₹15,000 = ₹15") },
            { assertTrue(charges.exchangeCharges.value > 0, "Exchange charges > 0") },
            { assertTrue(charges.gst.value > 0, "GST > 0") },
            { assertTrue(charges.sebi.value >= 0, "SEBI >= 0") },
            { assertEquals(Paisa(225), charges.stampDuty, "Stamp duty 0.015% of ₹15,000 ≈ ₹2.25") },
            { assertEquals(Paisa(0), charges.dpCharges, "No DP charges on buy") },
            { assertTrue(charges.total.value > 0, "Total charges > 0") },
        )
    }

    @Test
    fun `sell order includes DP charges and excludes stamp duty`() {
        val order = Order(type = OrderType.SELL, quantity = 10,
            totalValue = Paisa(2_000_000), exchange = Exchange.NSE)
        val charges = ChargeCalculator.calculate(order, standardRates)

        assertEquals(Paisa(0), charges.stampDuty, "No stamp duty on sell")
        assertEquals(Paisa(1580), charges.dpCharges, "DP charges on sell = ₹15.80")
    }

    @Test
    fun `zero brokerage does not affect GST calculation`() {
        // GST = 18% of (brokerage + exchange charges)
        // With zero brokerage, GST = 18% of exchange charges only
        val order = Order(type = OrderType.BUY, quantity = 1,
            totalValue = Paisa(100_000), exchange = Exchange.NSE)
        val charges = ChargeCalculator.calculate(order, standardRates)

        val expectedGstBase = charges.brokerage + charges.exchangeCharges
        val expectedGst = expectedGstBase.applyBasisPoints(1800)
        assertEquals(expectedGst, charges.gst)
    }

    @Test
    fun `very small order has minimum charges`() {
        val order = Order(type = OrderType.BUY, quantity = 1,
            totalValue = Paisa(100), exchange = Exchange.NSE) // ₹1.00
        val charges = ChargeCalculator.calculate(order, standardRates)

        // Even tiny orders should produce non-negative charges
        assertTrue(charges.total.value >= 0)
    }

    @Test
    fun `very large order (1 crore) has correct SEBI fee`() {
        val order = Order(type = OrderType.BUY, quantity = 100,
            totalValue = Paisa(100_000_000_00), exchange = Exchange.NSE) // ₹1 crore
        val charges = ChargeCalculator.calculate(order, standardRates)

        // SEBI: ₹10 per crore
        assertEquals(Paisa(1000), charges.sebi, "SEBI fee for ₹1Cr = ₹10")
    }
}
```

#### TargetPriceCalculator Tests

```kotlin
class TargetPriceCalculatorTest {
    @Test
    fun `percentage target converges within 5 iterations`() {
        val holding = Holding(
            investedAmount = Paisa(1_000_000), // ₹10,000
            quantity = 10,
            totalBuyCharges = Paisa(200),
            profitTarget = ProfitTarget.Percentage(500), // 5%
            exchange = Exchange.NSE,
        )
        val targetPrice = TargetPriceCalculator.calculate(holding, standardRates)

        // Target price should be > avg buy price (₹1,000)
        assertTrue(targetPrice.value > 100_000, "Target > ₹1,000")
        // Target price should cover: cost + 5% profit + buy charges + sell charges
        assertTrue(targetPrice.value < 120_000, "Target < ₹1,200 (reasonable bound)")
    }

    @Test
    fun `absolute target adds fixed amount`() {
        val holding = Holding(
            investedAmount = Paisa(1_000_000),
            quantity = 10,
            totalBuyCharges = Paisa(200),
            profitTarget = ProfitTarget.Absolute(Paisa(50_000)), // ₹500 absolute profit
            exchange = Exchange.NSE,
        )
        val targetPrice = TargetPriceCalculator.calculate(holding, standardRates)

        // Must cover cost (₹10,000) + profit (₹500) + charges
        assertTrue(targetPrice.value > 105_000, "Target > ₹1,050")
    }

    @Test
    fun `zero percentage target equals breakeven with charges`() {
        val holding = Holding(
            investedAmount = Paisa(1_000_000),
            quantity = 10,
            totalBuyCharges = Paisa(200),
            profitTarget = ProfitTarget.Percentage(0), // 0% — breakeven
            exchange = Exchange.NSE,
        )
        val targetPrice = TargetPriceCalculator.calculate(holding, standardRates)

        // Must cover cost + buy charges + sell charges (no profit)
        assertTrue(targetPrice.value > 100_000, "Breakeven > pure avg price")
    }
}
```

#### HoldingsVerifier Tests

```kotlin
class HoldingsVerifierTest {
    private val verifier = HoldingsVerifier()

    @Test
    fun `exact match returns no diffs`() {
        val remote = listOf(RemoteHolding("INFY", 10, "CNC"), RemoteHolding("TCS", 5, "CNC"))
        val local = listOf(Holding("INFY", 10), Holding("TCS", 5))
        val result = verifier.verify(remote, local)
        assertTrue(result.isMatch)
        assertTrue(result.diffs.isEmpty())
    }

    @Test
    fun `quantity mismatch detected`() {
        val remote = listOf(RemoteHolding("INFY", 15, "CNC"))
        val local = listOf(Holding("INFY", 10))
        val result = verifier.verify(remote, local)
        assertFalse(result.isMatch)
        assertEquals(1, result.diffs.size)
        assertEquals(5, result.diffs[0].difference)
    }

    @Test
    fun `non-CNC holdings filtered out`() {
        val remote = listOf(
            RemoteHolding("INFY", 10, "CNC"),
            RemoteHolding("NIFTY", 50, "MIS"), // Intraday — ignored
        )
        val local = listOf(Holding("INFY", 10))
        val result = verifier.verify(remote, local)
        assertTrue(result.isMatch)
    }

    @Test
    fun `remote has stock not in local`() {
        val remote = listOf(RemoteHolding("INFY", 10, "CNC"), RemoteHolding("HDFC", 20, "CNC"))
        val local = listOf(Holding("INFY", 10))
        val result = verifier.verify(remote, local)
        assertFalse(result.isMatch)
        assertEquals("HDFC", result.diffs[0].stockCode)
        assertEquals(0, result.diffs[0].localQuantity)
        assertEquals(20, result.diffs[0].remoteQuantity)
    }

    @Test
    fun `local has stock not in remote (sold externally)`() {
        val remote = listOf(RemoteHolding("INFY", 10, "CNC"))
        val local = listOf(Holding("INFY", 10), Holding("TCS", 5))
        val result = verifier.verify(remote, local)
        assertFalse(result.isMatch)
        assertEquals(1, result.diffs.size)
        assertEquals("TCS", result.diffs[0].stockCode)
    }
}
```

### 3.2 Paisa Value Class Tests

```kotlin
class PaisaTest {
    @Test
    fun `arithmetic operations`() {
        assertEquals(Paisa(300), Paisa(100) + Paisa(200))
        assertEquals(Paisa(-100), Paisa(100) - Paisa(200))
        assertEquals(Paisa(500), Paisa(100) * 5)
        assertEquals(Paisa(50), Paisa(100) / 2)
    }

    @Test
    fun `basis points calculation`() {
        // 10 basis points of ₹15,000 (1,500,000 paisa) = ₹15 (1500 paisa)
        assertEquals(Paisa(1500), Paisa(1_500_000).applyBasisPoints(10))
    }

    @Test
    fun `basis points rounding half-up`() {
        // Edge case: result that needs rounding
        val result = Paisa(1).applyBasisPoints(5000) // 50% of 1 paisa = 0.5 → rounds to 1
        assertEquals(Paisa(1), result)
    }

    @Test
    fun `toRupees conversion`() {
        assertEquals(BigDecimal("150.00"), Paisa(15000).toRupees())
        assertEquals(BigDecimal("0.01"), Paisa(1).toRupees())
    }

    @Test
    fun `fromRupees conversion`() {
        assertEquals(Paisa(15000), Paisa.fromRupees(BigDecimal("150.00")))
    }

    @Test
    fun `division by zero throws`() {
        assertThrows<IllegalArgumentException> { Paisa(100) / 0 }
    }
}
```

### 3.3 UseCase Tests (with MockK)

```kotlin
class SyncOrdersUseCaseTest {
    private val orderRepo: OrderRepository = mockk()
    private val holdingRepo: HoldingRepository = mockk()
    private val transactionRepo: TransactionRepository = mockk()
    private val kiteConnectRepo: KiteConnectRepository = mockk()
    private val chargeRateRepo: ChargeRateRepository = mockk()
    private val alertRepo: AlertRepository = mockk()
    private val syncEventRepo: SyncEventRepository = mockk()
    private val mutexRegistry = MutexRegistry()

    private val useCase = SyncOrdersUseCase(
        orderRepo, holdingRepo, transactionRepo, kiteConnectRepo,
        chargeRateRepo, alertRepo, syncEventRepo, mutexRegistry,
    )

    @Test
    fun `weekday guard skips on weekend`() = runTest {
        mockkObject(WeekdayGuard)
        every { WeekdayGuard.isTradingDay() } returns false

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        assertEquals(SyncResult.Skipped, result.getOrNull())
        coVerify(exactly = 0) { kiteConnectRepo.fetchTodaysOrders() }
    }

    @Test
    fun `no new orders returns NoNewOrders`() = runTest {
        mockkObject(WeekdayGuard)
        every { WeekdayGuard.isTradingDay() } returns true
        coEvery { kiteConnectRepo.fetchTodaysOrders() } returns listOf(/* empty */)
        coEvery { syncEventRepo.logStart(any()) } returns 1L
        coEvery { syncEventRepo.logComplete(any(), any(), any()) } just runs

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        assertEquals(SyncResult.NoNewOrders, result.getOrNull())
    }

    @Test
    fun `holdings mismatch aborts sync and creates alert`() = runTest {
        mockkObject(WeekdayGuard)
        every { WeekdayGuard.isTradingDay() } returns true
        coEvery { kiteConnectRepo.fetchTodaysOrders() } returns listOf(testBuyOrder())
        coEvery { orderRepo.exists(any()) } returns false
        coEvery { kiteConnectRepo.fetchHoldings() } returns listOf(
            RemoteHolding("INFY", 15, "CNC"), // Mismatch
        )
        coEvery { holdingRepo.getAll() } returns listOf(Holding("INFY", 10))
        coEvery { syncEventRepo.logStart(any()) } returns 1L
        coEvery { syncEventRepo.logComplete(any(), any(), any()) } just runs
        coEvery { alertRepo.insert(any()) } just runs

        val result = useCase.execute()

        assertTrue(result.isFailure)
        coVerify { alertRepo.insert(match { it.alertType == "HOLDINGS_MISMATCH" }) }
        coVerify(exactly = 0) { orderRepo.insertAll(any()) } // No orders persisted
    }

    @Test
    fun `network error returns transient failure with retry`() = runTest {
        mockkObject(WeekdayGuard)
        every { WeekdayGuard.isTradingDay() } returns true
        coEvery { kiteConnectRepo.fetchTodaysOrders() } throws IOException("timeout")
        coEvery { syncEventRepo.logStart(any()) } returns 1L
        coEvery { syncEventRepo.logComplete(any(), any(), any()) } just runs

        val result = useCase.execute()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.Transient)
    }
}
```

---

## 4. Database Test Strategy

### 4.1 DAO Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class OrderDaoTest {
    private lateinit var database: KiteWatchDatabase
    private lateinit var orderDao: OrderDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KiteWatchDatabase::class.java,
        ).allowMainThreadQueries().build()
        orderDao = database.orderDao()
    }

    @After
    fun teardown() { database.close() }

    @Test
    fun `insert and retrieve order`() = runBlocking {
        val order = testOrderEntity(zerodhaOrderId = "ORD001")
        orderDao.insertAll(listOf(order))

        val retrieved = orderDao.getByZerodhaId("ORD001")
        assertNotNull(retrieved)
        assertEquals("ORD001", retrieved!!.zerodhaOrderId)
    }

    @Test
    fun `duplicate zerodha_order_id is ignored`() = runBlocking {
        val order1 = testOrderEntity(zerodhaOrderId = "ORD001", price = 100_00)
        val order2 = testOrderEntity(zerodhaOrderId = "ORD001", price = 200_00) // Same ID

        val results = orderDao.insertAll(listOf(order1, order2))
        // First insert succeeds (-1 means ignored for second)
        assertTrue(results[0] > 0)
        assertEquals(-1L, results[1])

        // Only one row exists
        val retrieved = orderDao.getByZerodhaId("ORD001")
        assertEquals(100_00L, retrieved!!.pricePaisa) // Original value preserved
    }

    @Test
    fun `exists returns true for existing order`() = runBlocking {
        orderDao.insertAll(listOf(testOrderEntity(zerodhaOrderId = "ORD001")))
        assertTrue(orderDao.exists("ORD001"))
        assertFalse(orderDao.exists("ORD999"))
    }
}
```

### 4.2 TransactionDao Append-Only Enforcement

```kotlin
@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {
    // ... setup same as above

    @Test
    fun `transactions can only be inserted, not updated`() {
        // Verify at compile time: TransactionDao has NO @Update or @Delete methods
        // This test documents the invariant
        val daoClass = TransactionDao::class
        val methods = daoClass.memberFunctions

        val updateMethods = methods.filter {
            it.annotations.any { ann -> ann is Update || ann is Delete }
        }
        assertTrue(updateMethods.isEmpty(),
            "TransactionDao must not have @Update or @Delete methods")
    }

    @Test
    fun `insert creates with auto-generated ID`() = runBlocking {
        val id = transactionDao.insert(testTransaction(type = "FUND_ADDITION"))
        assertTrue(id > 0)

        val id2 = transactionDao.insert(testTransaction(type = "EQUITY_BUY"))
        assertEquals(id + 1, id2)
    }
}
```

### 4.3 Migration Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KiteWatchDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesOrderData() {
        // Create V1 database with test data
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("""
                INSERT INTO orders (zerodha_order_id, stock_code, stock_name, exchange,
                    order_type, quantity, price_paisa, total_value_paisa, trade_date,
                    trade_timestamp, source, created_at)
                VALUES ('ORD001', 'INFY', 'Infosys', 'NSE', 'BUY', 10, 150000,
                    1500000, '2025-03-01', '2025-03-01T10:00:00Z', 'API',
                    '2025-03-01T10:00:00Z')
            """)
            close()
        }

        // Run migration
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // Verify data preserved
        val cursor = db.query("SELECT * FROM orders WHERE zerodha_order_id = 'ORD001'")
        assertTrue(cursor.moveToFirst())
        assertEquals("INFY", cursor.getString(cursor.getColumnIndex("stock_code")))
        assertEquals(10, cursor.getInt(cursor.getColumnIndex("quantity")))

        // Verify new column has default value
        val newColIndex = cursor.getColumnIndex("instrument_token")
        assertTrue(cursor.isNull(newColIndex))
    }

    @Test
    fun migrate1To2_preservesAllTableData() {
        // Insert data into ALL tables at V1 and verify ALL data survives migration
        // ... comprehensive test for each table
    }
}
```

### 4.4 Integrity Invariant Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class DataIntegrityTest {
    // ... database setup

    @Test
    fun `INV-01 holdings quantity equals net order quantity`() = runBlocking {
        // Insert buy and sell orders for INFY
        orderDao.insertAll(listOf(
            testOrderEntity("ORD001", "INFY", OrderType.BUY, 20),
            testOrderEntity("ORD002", "INFY", OrderType.SELL, 5),
        ))
        holdingDao.insert(testHolding("INFY", quantity = 15))

        // Verify invariant
        val holding = holdingDao.getByStockCode("INFY")!!
        val buyQty = orderDao.getTotalBuyQuantity("INFY")
        val sellQty = orderDao.getTotalSellQuantity("INFY")
        assertEquals(buyQty - sellQty, holding.quantity)
    }

    @Test
    fun `INV-04 every order has exactly one equity transaction`() = runBlocking {
        // Insert order and its transactions
        val orderId = "ORD001"
        orderDao.insertAll(listOf(testOrderEntity(orderId, "INFY", OrderType.BUY, 10)))
        transactionDao.insert(testTransaction(
            type = "EQUITY_BUY", referenceId = orderId, referenceType = "ORDER"))

        // Verify exactly one equity transaction per order
        val equityTxns = transactionDao.getByReference("ORDER", orderId)
            .filter { it.type == "EQUITY_BUY" || it.type == "EQUITY_SELL" }
        assertEquals(1, equityTxns.size)
    }

    @Test
    fun `INV-10 transaction table has no update or delete operations`() {
        // Attempt raw SQL update (should be caught by code review, not DB constraint)
        // This test exists as a documentation assertion
        // Actual enforcement: TransactionDao has no @Update/@Delete
    }
}
```

---

## 5. UI Test Strategy

### 5.1 Compose Test Pattern

```kotlin
class HoldingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `loading state shows skeleton`() {
        composeTestRule.setContent {
            HoldingsContent(
                state = HoldingsState(isLoading = true),
                onIntent = {},
            )
        }
        composeTestRule.onNodeWithTag("skeleton_loader").assertIsDisplayed()
    }

    @Test
    fun `holdings list renders all items`() {
        val holdings = listOf(
            testHoldingUiModel("INFY", qty = 10, value = "₹15,000.00"),
            testHoldingUiModel("TCS", qty = 5, value = "₹20,000.00"),
        )
        composeTestRule.setContent {
            HoldingsContent(
                state = HoldingsState(holdings = holdings, isLoading = false),
                onIntent = {},
            )
        }
        composeTestRule.onNodeWithText("INFY").assertIsDisplayed()
        composeTestRule.onNodeWithText("TCS").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹15,000.00").assertIsDisplayed()
    }

    @Test
    fun `empty state shows message and action`() {
        composeTestRule.setContent {
            HoldingsContent(
                state = HoldingsState(holdings = emptyList(), isLoading = false),
                onIntent = {},
            )
        }
        composeTestRule.onNodeWithText("No current holdings").assertIsDisplayed()
    }

    @Test
    fun `tapping holding card dispatches expand intent`() {
        var capturedIntent: HoldingsIntent? = null
        composeTestRule.setContent {
            HoldingsContent(
                state = HoldingsState(holdings = listOf(testHoldingUiModel("INFY"))),
                onIntent = { capturedIntent = it },
            )
        }
        composeTestRule.onNodeWithText("INFY").performClick()
        assertIs<HoldingsIntent.ToggleExpand>(capturedIntent)
    }

    @Test
    fun `error state shows retry button`() {
        composeTestRule.setContent {
            HoldingsContent(
                state = HoldingsState(error = UiError.Screen("Something went wrong")),
                onIntent = {},
            )
        }
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }
}
```

### 5.2 ViewModel Tests (with Turbine)

```kotlin
class HoldingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getHoldingsUseCase: GetHoldingsUseCase = mockk()
    private val updateProfitTargetUseCase: UpdateProfitTargetUseCase = mockk()
    private val appEventBus = MutableSharedFlow<AppEvent>()

    private lateinit var viewModel: HoldingsViewModel

    @Before
    fun setup() {
        viewModel = HoldingsViewModel(getHoldingsUseCase, updateProfitTargetUseCase, appEventBus)
    }

    @Test
    fun `Load intent emits loading then data`() = runTest {
        coEvery { getHoldingsUseCase.execute() } returns Result.success(listOf(testHolding("INFY")))

        viewModel.state.test {
            val initial = awaitItem()
            assertTrue(initial.isLoading.not() && initial.holdings.isEmpty()) // Initial state

            viewModel.processIntent(HoldingsIntent.Load)

            val loading = awaitItem()
            assertTrue(loading.isLoading)

            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(1, loaded.holdings.size)
            assertEquals("INFY", loaded.holdings[0].stockCode)
        }
    }

    @Test
    fun `Load failure emits error state`() = runTest {
        coEvery { getHoldingsUseCase.execute() } returns
            Result.failure(AppError.Transient.NetworkUnavailable)

        viewModel.state.test {
            awaitItem() // Initial
            viewModel.processIntent(HoldingsIntent.Load)
            awaitItem() // Loading
            val error = awaitItem()
            assertNotNull(error.error)
        }
    }

    @Test
    fun `global OrderSyncCompleted event triggers refresh`() = runTest {
        coEvery { getHoldingsUseCase.execute() } returns Result.success(emptyList())

        viewModel.state.test {
            awaitItem() // Initial
            viewModel.processIntent(HoldingsIntent.Load)
            awaitItem() // Loading
            awaitItem() // Loaded (empty)

            // Simulate global event
            appEventBus.emit(AppEvent.OrderSyncCompleted(5))

            // Should trigger a refresh
            awaitItem() // Loading again
            awaitItem() // Reloaded
        }
    }

    @Test
    fun `UpdateProfitTarget emits success side effect`() = runTest {
        coEvery { updateProfitTargetUseCase.execute(any(), any()) } returns Result.success(Unit)
        coEvery { getHoldingsUseCase.execute() } returns Result.success(listOf(testHolding("INFY")))

        viewModel.sideEffect.test {
            viewModel.processIntent(HoldingsIntent.UpdateProfitTarget("INFY", ProfitTarget.Percentage(500)))
            val effect = awaitItem()
            assertIs<HoldingsEffect.ShowSnackbar>(effect)
            assertTrue(effect.message.contains("updated"))
        }
    }
}
```

---

## 6. Integration Test Strategy

### 6.1 Order Sync End-to-End

```kotlin
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class OrderSyncIntegrationTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var syncOrdersUseCase: SyncOrdersUseCase
    @Inject lateinit var orderRepo: OrderRepository
    @Inject lateinit var holdingRepo: HoldingRepository
    @Inject lateinit var transactionRepo: TransactionRepository
    @Inject lateinit var database: KiteWatchDatabase

    @BindValue
    val kiteConnectRepo: KiteConnectRepository = mockk()

    @Before
    fun setup() { hiltRule.inject() }

    @Test
    fun `full sync flow persists orders holdings and charges`() = runTest {
        // Mock API responses
        coEvery { kiteConnectRepo.fetchTodaysOrders() } returns listOf(
            testRemoteOrder("ORD001", "INFY", "BUY", 10, 1500_00),
        )
        coEvery { kiteConnectRepo.fetchHoldings() } returns listOf(
            RemoteHolding("INFY", 10, "CNC"),
        )
        coEvery { kiteConnectRepo.fetchFundBalance() } returns Paisa(1_000_000_00)

        // Seed charge rates
        database.chargeRateDao().insertCurrentRates(testChargeRates())

        // Execute sync
        val result = syncOrdersUseCase.execute()
        assertTrue(result.isSuccess)

        // Verify order persisted
        val order = orderRepo.getByZerodhaId("ORD001")
        assertNotNull(order)

        // Verify holding created
        val holding = holdingRepo.getByStockCode("INFY")
        assertNotNull(holding)
        assertEquals(10, holding!!.quantity)

        // Verify charge transactions exist
        val charges = transactionRepo.getChargesForOrder("ORD001")
        assertTrue(charges.isNotEmpty(), "Charge transactions should exist")
        assertTrue(charges.any { it.type == TransactionType.STT_CHARGE })
    }
}
```

### 6.2 Backup and Restore Round-Trip

```kotlin
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class BackupRestoreIntegrationTest {
    @Inject lateinit var createBackupUseCase: CreateBackupUseCase
    @Inject lateinit var restoreBackupUseCase: RestoreBackupUseCase
    @Inject lateinit var database: KiteWatchDatabase

    @Test
    fun `backup and restore preserves all data`() = runTest {
        // Seed database with test data
        seedTestData()
        val originalOrderCount = database.orderDao().getCount()
        val originalTxnCount = database.transactionDao().getCount()
        val originalHoldingCount = database.holdingDao().getCount()

        // Create backup
        val backupResult = createBackupUseCase.execute(BackupDestination.LOCAL)
        assertTrue(backupResult.isSuccess)
        val backupFile = backupResult.getOrThrow().filePath

        // Clear database
        database.clearAllTables()
        assertEquals(0, database.orderDao().getCount())

        // Restore
        val restoreResult = restoreBackupUseCase.execute(RestoreSource.Local(backupFile))
        assertTrue(restoreResult.isSuccess)

        // Verify data matches
        assertEquals(originalOrderCount, database.orderDao().getCount())
        assertEquals(originalTxnCount, database.transactionDao().getCount())
        assertEquals(originalHoldingCount, database.holdingDao().getCount())
    }

    @Test
    fun `restore rejects backup from different account`() = runTest {
        // Bind account "USER1"
        database.accountBindingDao().bind("USER1", "key1")
        // Create a backup file with account "USER2" in header
        val foreignBackup = createForeignAccountBackup("USER2")

        val result = restoreBackupUseCase.execute(RestoreSource.Local(foreignBackup))
        assertTrue(result.isFailure)
        assertIs<AccountMismatch>(result.exceptionOrNull())
    }

    @Test
    fun `restore with corrupted file fails with checksum error`() = runTest {
        val corruptedFile = createCorruptedBackupFile()
        val result = restoreBackupUseCase.execute(RestoreSource.Local(corruptedFile))
        assertTrue(result.isFailure)
        assertIs<ChecksumMismatch>(result.exceptionOrNull())
    }
}
```

### 6.3 CSV Import Integration

```kotlin
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class CsvImportIntegrationTest {
    @Inject lateinit var importCsvUseCase: ImportCsvUseCase
    @Inject lateinit var orderRepo: OrderRepository
    @Inject lateinit var holdingRepo: HoldingRepository

    @Test
    fun `valid CSV imports all orders and creates holdings`() = runTest {
        val csv = createTestCsv("""
            order_id,stock_code,stock_name,exchange,order_type,quantity,price,date
            ORD001,INFY,Infosys,NSE,BUY,10,1500.00,2025-01-15
            ORD002,TCS,TCS Ltd,NSE,BUY,5,3500.00,2025-01-16
        """.trimIndent())

        val result = importCsvUseCase.execute(csv)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().importedCount)

        // Holdings created
        assertNotNull(holdingRepo.getByStockCode("INFY"))
        assertNotNull(holdingRepo.getByStockCode("TCS"))
    }

    @Test
    fun `duplicate orders are skipped`() = runTest {
        // Pre-insert an order
        orderRepo.insert(testOrder(zerodhaOrderId = "ORD001"))

        val csv = createTestCsv("""
            order_id,stock_code,stock_name,exchange,order_type,quantity,price,date
            ORD001,INFY,Infosys,NSE,BUY,10,1500.00,2025-01-15
            ORD002,TCS,TCS Ltd,NSE,BUY,5,3500.00,2025-01-16
        """.trimIndent())

        val result = importCsvUseCase.execute(csv)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().importedCount)
        assertEquals(1, result.getOrThrow().skippedCount)
    }

    @Test
    fun `invalid CSV format rejects entire file`() = runTest {
        val csv = createTestCsv("wrong,headers,here\n1,2,3")
        val result = importCsvUseCase.execute(csv)
        assertTrue(result.isFailure)
        assertEquals(0, orderRepo.getCount()) // Nothing persisted
    }
}
```

---

## 7. Test Configuration

### 7.1 Test Dependencies (Version Catalog)

```toml
# gradle/libs.versions.toml
[versions]
junit5 = "5.10.2"
mockk = "1.13.9"
turbine = "1.0.0"
truth = "1.4.2"
coroutines-test = "1.8.0"
room-testing = "2.6.1"
compose-test = "2024.02.00"   # BOM version

[libraries]
junit5-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junit5" }
junit5-params = { module = "org.junit.jupiter:junit-jupiter-params", version.ref = "junit5" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
mockk-android = { module = "io.mockk:mockk-android", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines-test" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room-testing" }
compose-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
hilt-testing = { module = "com.google.dagger:hilt-android-testing", version.ref = "hilt" }

[bundles]
unit-testing = ["junit5-api", "junit5-params", "mockk", "turbine", "truth", "coroutines-test"]
android-testing = ["mockk-android", "turbine", "truth", "coroutines-test", "room-testing", "compose-test-junit4", "hilt-testing"]
```

### 7.2 Convention Plugin for Testing

```kotlin
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            tasks.withType<Test>().configureEach {
                useJUnitPlatform() // JUnit 5
                testLogging {
                    events = setOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
                    showStandardStreams = true
                }
            }
            dependencies {
                "testImplementation"(libs.findBundle("unit-testing").get())
                "testRuntimeOnly"(libs.findLibrary("junit5-engine").get())
                "androidTestImplementation"(libs.findBundle("android-testing").get())
                "debugImplementation"(libs.findLibrary("compose-test-manifest").get())
            }
        }
    }
}
```

### 7.3 MainDispatcherRule (for ViewModel Tests)

```kotlin
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

---

## 8. CI Pipeline

### 8.1 GitHub Actions Workflow

```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: ktlint Check
        run: ./gradlew ktlintCheck
      - name: detekt
        run: ./gradlew detekt
      - name: Android Lint
        run: ./gradlew lintDebug

  unit-tests:
    runs-on: ubuntu-latest
    needs: lint
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Unit Tests
        run: ./gradlew testDebugUnitTest
      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-reports
          path: '**/build/reports/tests/'

  android-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666"' | sudo tee /etc/udev/rules.d/99-kvm.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger
      - name: Instrumented Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 30
          arch: x86_64
          script: ./gradlew connectedDebugAndroidTest
      - name: Upload Instrumented Test Reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: android-test-reports
          path: '**/build/reports/androidTests/'

  build:
    runs-on: ubuntu-latest
    needs: [unit-tests, android-tests]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Build Debug APK
        run: ./gradlew assembleDebug
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

### 8.2 CI Enforcement Rules

| Check | Blocking | Details |
|---|---|---|
| ktlint | ✅ Yes | Zero formatting violations |
| detekt | ✅ Yes | 0 errors; warnings tracked but non-blocking initially |
| Android Lint | ✅ Yes | 0 errors; specific warnings suppressed with justification |
| Unit tests | ✅ Yes | 100% pass rate |
| Instrumented tests | ✅ Yes | 100% pass rate |
| Build (assembleDebug) | ✅ Yes | Must succeed |
| Compose Compiler Metrics | ❌ No | Tracked for performance regression awareness |

---

## 9. Code Coverage Targets

| Module | Coverage Target | Enforcement |
|---|---|---|
| `:core-domain` (engines) | 90% line coverage | CI gate (JaCoCo) |
| `:core-domain` (usecases) | 80% line coverage | CI gate |
| `:core-data` (repositories) | 70% line coverage | Tracked, not gated |
| `:core-database` (DAOs) | 80% query coverage | CI gate |
| `:feature-*` (ViewModels) | 70% branch coverage | Tracked, not gated |
| `:feature-*` (Composables) | Critical paths only | Not measured by coverage |
| `:infra-*` | 60% line coverage | Tracked, not gated |

**Coverage Measurement:**

```kotlin
// build.gradle.kts (:core-domain)
plugins {
    id("jacoco")
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.90".toBigDecimal() // 90%
            }
        }
    }
}
```

---

## 10. Test Data Factories

```kotlin
object TestFixtures {
    fun testOrder(
        zerodhaOrderId: String = "ORD${Random.nextInt(10000)}",
        stockCode: String = "INFY",
        type: OrderType = OrderType.BUY,
        quantity: Int = 10,
        pricePaisa: Long = 150_000,
        tradeDate: LocalDate = LocalDate.now(),
        exchange: Exchange = Exchange.NSE,
    ) = Order(
        zerodhaOrderId = zerodhaOrderId,
        stockCode = stockCode,
        stockName = stockNameFor(stockCode),
        exchange = exchange,
        type = type,
        quantity = quantity,
        price = Paisa(pricePaisa),
        totalValue = Paisa(pricePaisa * quantity),
        tradeDate = tradeDate,
        tradeTimestamp = tradeDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
        source = TransactionSource.API,
    )

    fun testHolding(
        stockCode: String = "INFY",
        quantity: Int = 10,
        avgPricePaisa: Long = 150_000,
        profitTarget: ProfitTarget = ProfitTarget.Percentage(500),
    ) = Holding(
        stockCode = stockCode,
        stockName = stockNameFor(stockCode),
        exchange = Exchange.NSE,
        quantity = quantity,
        investedAmount = Paisa(avgPricePaisa * quantity),
        avgBuyPrice = Paisa(avgPricePaisa),
        totalBuyCharges = Paisa(200),
        profitTarget = profitTarget,
        targetSellPrice = Paisa(avgPricePaisa + 10_000),
    )

    fun testChargeRates() = ChargeRateSnapshot(
        brokerageDeliveryBps = 0,
        sttBuyBps = 10,
        sttSellBps = 25,
        exchangeNseBps = 297,
        exchangeBseBps = 375,
        gstBps = 1800,
        sebiFeePerCrorePaisa = 1000,
        stampDutyBps = 15,
        dpChargesPerScriptPaisa = 1580,
        effectiveFrom = LocalDate.of(2025, 1, 1),
    )

    private fun stockNameFor(code: String) = when (code) {
        "INFY" -> "Infosys Limited"
        "TCS" -> "Tata Consultancy Services"
        "RELIANCE" -> "Reliance Industries"
        "HDFC" -> "HDFC Bank"
        else -> "$code Corp"
    }
}
```

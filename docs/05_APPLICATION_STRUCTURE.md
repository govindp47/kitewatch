# 05 — Application Structure

**Version:** 1.0
**Product:** KiteWatch — Android Local-First Portfolio Management
**Last Updated:** 2026-03-10

---

## 1. Module Structure

KiteWatch uses a **multi-module Gradle project** with convention plugins to enforce layer boundaries and minimize build times via parallel compilation. Modules are organized into four tiers: `:app`, `:feature-*`, `:core-*`, and `:infra-*`.

### 1.1 Module Dependency Graph

```
                              ┌─────────┐
                              │  :app   │
                              └────┬────┘
            ┌──────────┬──────────┬┼──────────┬──────────┬──────────┐
            ▼          ▼          ▼▼          ▼          ▼          ▼
     ┌────────────┐┌────────────┐┌────────────┐┌────────────┐┌────────────┐
     │ :feature-  ││ :feature-  ││ :feature-  ││ :feature-  ││ :feature-  │
     │ portfolio  ││ holdings   ││  orders    ││transactions││ settings   │
     └─────┬──────┘└─────┬──────┘└─────┬──────┘└─────┬──────┘└─────┬──────┘
           │             │             │             │             │
           │      ┌──────┘             │             │      ┌──────┘
           │      │         ┌──────────┘             │      │
           ▼      ▼         ▼                        ▼      ▼
     ┌────────────┐  ┌────────────┐           ┌────────────┐
     │ :feature-  │  │ :feature-  │           │ :feature-  │
     │    gtt     │  │ onboarding │           │    auth    │
     └─────┬──────┘  └─────┬──────┘           └─────┬──────┘
           │               │                        │
           └───────────┬───┴────────────────────────┘
                       ▼
              ┌────────────────┐
              │   :core-ui     │ ◀─── Shared composables, theme, design system
              └────────┬───────┘
                       │
              ┌────────▼───────┐
              │  :core-domain  │ ◀─── Pure Kotlin: models, usecases, engines
              └────────┬───────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
  ┌──────────┐  ┌──────────────┐ ┌──────────────┐
  │:core-data│  │:core-network │ │:core-database│
  └─────┬────┘  └──────┬───────┘ └──────┬───────┘
        │              │               │
        │    ┌─────────┘               │
        ▼    ▼                         ▼
  ┌──────────────┐              ┌──────────────┐
  │ :infra-worker│              │ :infra-backup│
  └──────────────┘              └──────────────┘
        │
   ┌────┴────┐
   ▼         ▼
┌────────┐┌────────┐
│:infra- ││:infra- │
│ auth   ││  csv   │
└────────┘└────────┘
```

### 1.2 Module Responsibility Matrix

| Module | Type | Responsibility | Dependencies |
|---|---|---|---|
| `:app` | `com.android.application` | Entry point, DI wiring, navigation host, `Application` class | All feature + core + infra modules |
| `:feature-portfolio` | `com.android.library` | Portfolio/home screen, charts, P&L dashboard | `:core-domain`, `:core-ui` |
| `:feature-holdings` | `com.android.library` | Holdings list, stock detail, profit target editing | `:core-domain`, `:core-ui` |
| `:feature-orders` | `com.android.library` | Orders list, filtering, CSV import trigger | `:core-domain`, `:core-ui` |
| `:feature-transactions` | `com.android.library` | Transaction log, filtering | `:core-domain`, `:core-ui` |
| `:feature-gtt` | `com.android.library` | GTT orders list screen | `:core-domain`, `:core-ui` |
| `:feature-settings` | `com.android.library` | All settings screens, schedule config, backup/restore, about, guidebook | `:core-domain`, `:core-ui` |
| `:feature-onboarding` | `com.android.library` | T&C, biometric setup, Zerodha login, Gmail/Drive setup | `:core-domain`, `:core-ui` |
| `:feature-auth` | `com.android.library` | Biometric lock screen, app lock gating | `:core-domain`, `:core-ui` |
| `:core-domain` | `java-library` (pure Kotlin) | Domain models, UseCases, engines, repository interfaces, error types | None |
| `:core-data` | `com.android.library` | Repository implementations, data source aggregation | `:core-domain`, `:core-network`, `:core-database` |
| `:core-network` | `com.android.library` | Retrofit clients, API DTOs, remote data source implementations | `:core-domain` |
| `:core-database` | `com.android.library` | Room database, DAOs, entities, migrations, type converters | `:core-domain` |
| `:core-ui` | `com.android.library` | Design system, shared composables, theme, icons, formatters | `:core-domain` (for model display types) |
| `:infra-worker` | `com.android.library` | WorkManager workers, scheduler, weekday guard | `:core-domain`, `:core-data` |
| `:infra-auth` | `com.android.library` | BiometricManager, lock timeout logic, credential storage | `:core-domain` |
| `:infra-backup` | `com.android.library` | Backup engine, protobuf serialization, Drive upload/download | `:core-domain`, `:core-data` |
| `:infra-csv` | `com.android.library` | CSV parser, validator, Excel exporter | `:core-domain` |
| `:build-logic` | Gradle convention plugins | Shared build configuration (Android library, Compose, testing) | Gradle API |

---

## 2. Package Hierarchy

### 2.1 `:core-domain` Package Structure

```
com.kitewatch.domain/
├── model/
│   ├── Paisa.kt
│   ├── Order.kt
│   ├── OrderType.kt
│   ├── Exchange.kt
│   ├── Holding.kt
│   ├── Transaction.kt
│   ├── TransactionType.kt
│   ├── TransactionSource.kt
│   ├── FundEntry.kt
│   ├── FundEntryType.kt
│   ├── GttRecord.kt
│   ├── GttStatus.kt
│   ├── ChargeRateSnapshot.kt
│   ├── ChargeBreakdown.kt
│   ├── PnlSummary.kt
│   ├── ProfitTarget.kt
│   ├── AccountBinding.kt
│   ├── SyncResult.kt
│   ├── ReconciliationResult.kt
│   ├── DateRange.kt
│   ├── PersistentAlert.kt
│   └── BackupMetadata.kt
├── engine/
│   ├── ChargeCalculator.kt
│   ├── PnlCalculator.kt
│   ├── HoldingsVerifier.kt
│   ├── TargetPriceCalculator.kt
│   ├── DuplicateDetector.kt
│   └── FundBalanceCalculator.kt
├── usecase/
│   ├── sync/
│   │   ├── SyncOrdersUseCase.kt
│   │   └── RefreshChargeRatesUseCase.kt
│   ├── fund/
│   │   ├── AddFundEntryUseCase.kt
│   │   ├── ReconcileFundUseCase.kt
│   │   └── GetFundBalanceUseCase.kt
│   ├── gtt/
│   │   ├── PlaceGttUseCase.kt
│   │   ├── UpdateProfitTargetUseCase.kt
│   │   └── ResolveGttOverrideUseCase.kt
│   ├── portfolio/
│   │   ├── CalculatePnlUseCase.kt
│   │   └── GetPortfolioSummaryUseCase.kt
│   ├── holdings/
│   │   ├── GetHoldingsUseCase.kt
│   │   └── GetHoldingDetailUseCase.kt
│   ├── orders/
│   │   ├── GetOrdersPagedUseCase.kt
│   │   └── ImportCsvUseCase.kt
│   ├── transactions/
│   │   └── GetTransactionsPagedUseCase.kt
│   ├── backup/
│   │   ├── CreateBackupUseCase.kt
│   │   ├── RestoreBackupUseCase.kt
│   │   └── ExportExcelUseCase.kt
│   ├── auth/
│   │   ├── AuthenticateUseCase.kt
│   │   └── BindAccountUseCase.kt
│   └── gmail/
│       ├── ScanGmailUseCase.kt
│       └── ConfirmGmailEntryUseCase.kt
├── repository/
│   ├── OrderRepository.kt
│   ├── HoldingRepository.kt
│   ├── TransactionRepository.kt
│   ├── FundRepository.kt
│   ├── GttRepository.kt
│   ├── ChargeRateRepository.kt
│   ├── KiteConnectRepository.kt
│   ├── AlertRepository.kt
│   ├── SyncEventRepository.kt
│   ├── PnlCacheRepository.kt
│   ├── GmailRepository.kt
│   ├── GoogleDriveRepository.kt
│   ├── BackupRepository.kt
│   └── PreferencesRepository.kt
├── error/
│   └── AppError.kt
└── event/
    └── AppEvent.kt
```

### 2.2 `:core-data` Package Structure

```
com.kitewatch.data/
├── repository/
│   ├── OrderRepositoryImpl.kt
│   ├── HoldingRepositoryImpl.kt
│   ├── TransactionRepositoryImpl.kt
│   ├── FundRepositoryImpl.kt
│   ├── GttRepositoryImpl.kt
│   ├── ChargeRateRepositoryImpl.kt
│   ├── KiteConnectRepositoryImpl.kt
│   ├── AlertRepositoryImpl.kt
│   ├── SyncEventRepositoryImpl.kt
│   ├── PnlCacheRepositoryImpl.kt
│   ├── GmailRepositoryImpl.kt
│   ├── GoogleDriveRepositoryImpl.kt
│   ├── BackupRepositoryImpl.kt
│   └── PreferencesRepositoryImpl.kt
├── mapper/
│   ├── OrderMapper.kt              // Entity ↔ Domain model
│   ├── HoldingMapper.kt
│   ├── TransactionMapper.kt
│   ├── GttRecordMapper.kt
│   ├── ChargeRateMapper.kt
│   ├── AlertMapper.kt
│   └── ApiDtoMapper.kt             // API DTO → Domain model
├── di/
│   ├── RepositoryModule.kt         // Hilt @Module binding interfaces to impls
│   └── DataModule.kt               // DataStore, preferences
└── sync/
    └── MutexRegistry.kt
```

### 2.3 `:core-network` Package Structure

```
com.kitewatch.network/
├── kiteconnect/
│   ├── KiteConnectApiService.kt    // Retrofit interface
│   ├── KiteConnectAuthInterceptor.kt
│   ├── KiteConnectRateLimitInterceptor.kt
│   ├── KiteConnectCertificatePinner.kt
│   ├── dto/
│   │   ├── OrderDto.kt
│   │   ├── HoldingDto.kt
│   │   ├── FundBalanceDto.kt
│   │   ├── GttDto.kt
│   │   ├── ChargeRateDto.kt
│   │   └── AuthResponseDto.kt
│   └── KiteConnectRemoteDataSource.kt
├── gmail/
│   ├── GmailApiClient.kt
│   ├── GmailMessageParser.kt
│   └── GmailRemoteDataSource.kt
├── drive/
│   ├── GoogleDriveApiClient.kt
│   └── GoogleDriveRemoteDataSource.kt
├── di/
│   ├── NetworkModule.kt            // Retrofit, OkHttp, API services
│   └── GoogleApiModule.kt          // Gmail, Drive clients
└── util/
    ├── NetworkMonitor.kt           // Connectivity observer
    └── ApiResultAdapter.kt         // Retrofit call adapter for Result<T>
```

### 2.4 `:core-database` Package Structure

```
com.kitewatch.database/
├── KiteWatchDatabase.kt
├── entity/
│   ├── AccountBindingEntity.kt
│   ├── OrderEntity.kt
│   ├── HoldingEntity.kt
│   ├── OrderHoldingsLinkEntity.kt
│   ├── TransactionEntity.kt
│   ├── FundEntryEntity.kt
│   ├── ChargeRateEntity.kt
│   ├── GttRecordEntity.kt
│   ├── PersistentAlertEntity.kt
│   ├── SyncEventLogEntity.kt
│   ├── PnlMonthlyCacheEntity.kt
│   ├── GmailScanCacheEntity.kt
│   ├── GmailFilterEntity.kt
│   ├── BackupHistoryEntity.kt
│   └── WorkerHandoffEntity.kt
├── dao/
│   ├── AccountBindingDao.kt
│   ├── OrderDao.kt
│   ├── HoldingDao.kt
│   ├── TransactionDao.kt
│   ├── FundEntryDao.kt
│   ├── ChargeRateDao.kt
│   ├── GttRecordDao.kt
│   ├── PersistentAlertDao.kt
│   ├── SyncEventLogDao.kt
│   ├── PnlMonthlyCacheDao.kt
│   ├── GmailScanCacheDao.kt
│   ├── GmailFilterDao.kt
│   ├── BackupHistoryDao.kt
│   └── WorkerHandoffDao.kt
├── converter/
│   └── Converters.kt
├── migration/
│   ├── Migration1To2.kt
│   └── MigrationHelper.kt
├── di/
│   └── DatabaseModule.kt           // Room DB instance, DAOs
└── view/
    └── HoldingWithGttView.kt        // @DatabaseView for joined queries
```

### 2.5 Feature Module Package Structure (Example: `:feature-holdings`)

```
com.kitewatch.feature.holdings/
├── HoldingsScreen.kt               // Root composable
├── HoldingsViewModel.kt            // MVI ViewModel
├── HoldingsContract.kt             // Intent, State, SideEffect definitions
├── component/
│   ├── HoldingCard.kt              // Expandable stock card
│   ├── HoldingCardExpanded.kt      // Expanded detail view
│   ├── ProfitTargetEditSheet.kt    // Bottom sheet for editing target
│   └── ChargeBreakdownRow.kt       // Individual charge display
├── mapper/
│   └── HoldingUiMapper.kt          // Domain model → UI model
├── model/
│   └── HoldingUiModel.kt           // UI-specific data class (formatted strings)
├── navigation/
│   └── HoldingsNavigation.kt       // Route definitions, NavGraph extension
└── di/
    └── HoldingsModule.kt           // Feature-specific Hilt module (if needed)
```

### 2.6 `:core-ui` Package Structure

```
com.kitewatch.ui/
├── theme/
│   ├── Theme.kt                    // KiteWatchTheme composable
│   ├── Color.kt                    // Light and dark color palettes
│   ├── Typography.kt               // Type scale definitions
│   ├── Shape.kt                    // Shape definitions
│   └── Dimens.kt                   // Spacing, sizing constants
├── component/
│   ├── AlertBanner.kt              // Red/amber/green alert banners
│   ├── SkeletonLoader.kt           // Shimmer loading placeholders
│   ├── FilterChipGroup.kt          // Reusable date range chip selector
│   ├── EmptyStateWidget.kt         // Generic empty state with icon + message
│   ├── SetupChecklist.kt           // Onboarding checklist widget
│   ├── ConfirmationDialog.kt       // Standard confirmation dialog
│   ├── ErrorStateWidget.kt         // Error with retry action
│   ├── CurrencyText.kt             // Formatted ₹ amount display
│   ├── PercentageText.kt           // Formatted % display
│   ├── DateRangeSelector.kt        // Date picker with presets
│   ├── PaginatedLazyColumn.kt      // Infinite scroll list wrapper
│   └── StatusIndicator.kt          // Sync status dot indicator
├── chart/
│   ├── PnlLineChart.kt            // Cumulative P&L line chart (Vico)
│   ├── MonthlyBarChart.kt         // Monthly P&L bar chart (Vico)
│   ├── ChargesBreakdownChart.kt   // Charges stacked column (Vico)
│   └── PnlPieChart.kt            // P&L vs charges pie chart (custom Canvas)
├── formatter/
│   ├── CurrencyFormatter.kt       // Paisa → "₹1,00,000.00" with Indian numbering
│   ├── DateFormatter.kt           // ISO-8601 → user-facing date strings
│   └── PercentageFormatter.kt     // Basis points → "5.00%"
├── icon/
│   └── KiteWatchIcons.kt          // Centralized Material Icon references
├── preview/
│   └── PreviewParameterProviders.kt // Compose preview data providers
└── util/
    ├── CollectSideEffect.kt        // Composable helper for SideEffect collection
    └── ModifierExtensions.kt       // Common modifier extensions
```

---

## 3. Feature Module Layout Pattern

Every feature module follows an identical internal structure:

```
:feature-{name}/
├── {Name}Screen.kt              // Root @Composable, entry point
├── {Name}ViewModel.kt           // @HiltViewModel, MVI implementation
├── {Name}Contract.kt            // Intent, State, SideEffect sealed types
├── component/                   // Screen-specific composables
│   └── {ComponentName}.kt
├── mapper/                      // Domain model → UI model transformations
│   └── {Name}UiMapper.kt
├── model/                       // UI-specific data classes
│   └── {Name}UiModel.kt
├── navigation/                  // NavGraph contributions
│   └── {Name}Navigation.kt
└── di/                          // Feature-specific Hilt bindings (optional)
    └── {Name}Module.kt
```

**Rules:**

1. Feature modules never depend on other feature modules. Cross-feature navigation uses route strings defined in `:app`.
2. Feature modules depend only on `:core-domain` and `:core-ui`. They never import from `:core-data`, `:core-database`, or `:core-network`.
3. Each feature provides a `{Name}Navigation.kt` extension function that adds its routes to a `NavGraphBuilder`.

---

## 4. Navigation Architecture

### 4.1 Navigation Host

```kotlin
// :app/src/main/java/com/kitewatch/app/navigation/KiteWatchNavHost.kt

@Composable
fun KiteWatchNavHost(
    navController: NavHostController,
    isAuthenticated: Boolean,
    isOnboardingComplete: Boolean,
) {
    NavHost(
        navController = navController,
        startDestination = when {
            !isAuthenticated      -> AuthRoute
            !isOnboardingComplete -> OnboardingRoute
            else                  -> MainRoute
        },
    ) {
        authScreen(onAuthenticated = { navController.navigate(MainRoute) })

        onboardingGraph(
            onComplete = { navController.navigate(MainRoute) { popUpTo(OnboardingRoute) { inclusive = true } } }
        )

        mainGraph(navController)
    }
}
```

### 4.2 Bottom Navigation Structure

```kotlin
// Main graph with bottom navigation
fun NavGraphBuilder.mainGraph(navController: NavHostController) {
    navigation(startDestination = PortfolioRoute, route = MainRoute) {
        portfolioScreen()
        holdingsScreen(
            onNavigateToGtt = { navController.navigate(GttRoute) },
            onNavigateToStockDetail = { stockId -> navController.navigate(StockDetailRoute(stockId)) }
        )
        ordersScreen()
        transactionsScreen()
        settingsGraph(navController)
    }
}

// Bottom navigation tabs
enum class BottomNavTab(val route: String, val icon: ImageVector, val label: String) {
    PORTFOLIO(PortfolioRoute, Icons.Outlined.PieChart, "Portfolio"),
    HOLDINGS(HoldingsRoute, Icons.Outlined.AccountBalance, "Holdings"),
    ORDERS(OrdersRoute, Icons.Outlined.Receipt, "Orders"),
    TRANSACTIONS(TransactionsRoute, Icons.Outlined.SwapVert, "Transactions"),
    SETTINGS(SettingsRoute, Icons.Outlined.Settings, "Settings"),
}
```

### 4.3 Route Definitions (Type-Safe)

```kotlin
// Using Kotlin Serialization-based type-safe navigation (Navigation Compose 2.8+)
@Serializable object AuthRoute
@Serializable object OnboardingRoute
@Serializable object MainRoute
@Serializable object PortfolioRoute
@Serializable object HoldingsRoute
@Serializable data class StockDetailRoute(val stockCode: String)
@Serializable object OrdersRoute
@Serializable object TransactionsRoute
@Serializable object GttRoute
@Serializable object SettingsRoute
@Serializable object ScheduleConfigRoute
@Serializable object BackupRestoreRoute
@Serializable object ChargeRatesRoute
@Serializable object AboutRoute
@Serializable object GuidebookRoute
@Serializable object PrivacyRoute
@Serializable object TermsRoute
```

### 4.4 Navigation Flow Diagram

```
App Launch
  │
  ├── Not Authenticated ──▶ AuthScreen (Biometric/PIN)
  │                              │
  │                              ▼ Authenticated
  │                         ┌─ Not Onboarded ──▶ OnboardingGraph
  │                         │                         │
  │                         │   ┌─────────────────────┘
  │                         │   ▼ Onboarding Complete
  │                         └─▶ MainGraph
  │                               │
  ▼ Authenticated + Onboarded     │
  MainGraph ◀─────────────────────┘
    │
    ├── Portfolio (Home Tab)
    │     └── DateRangeFilterSheet (modal bottom sheet)
    │
    ├── Holdings Tab
    │     ├── StockDetail (in-list expansion or separate screen)
    │     │     └── ProfitTargetEditSheet (modal bottom sheet)
    │     └── GTT Orders (navigated from Holdings)
    │
    ├── Orders Tab
    │     └── FilterSheet (modal bottom sheet)
    │
    ├── Transactions Tab
    │     └── FilterSheet (modal bottom sheet)
    │
    └── Settings Tab
          ├── Schedule Configuration
          ├── Fund Tolerance
          ├── Charge Rates
          ├── Backup & Restore
          │     ├── Import CSV (file picker)
          │     └── Export Excel (share sheet)
          ├── Notifications
          ├── Theme
          ├── About
          ├── Full Guidebook
          ├── Privacy & Security
          └── Terms & Conditions
```

---

## 5. State Management Approach

### 5.1 MVI Pattern Implementation

Each screen has a strict MVI contract:

```kotlin
// Pattern template — every feature follows this
class {Name}ViewModel @Inject constructor(
    private val useCases: ...,
    private val appEventBus: SharedFlow<AppEvent>,
) : ViewModel() {

    private val _state = MutableStateFlow({Name}State())
    val state: StateFlow<{Name}State> = _state.asStateFlow()

    private val _sideEffect = Channel<{Name}SideEffect>(Channel.BUFFERED)
    val sideEffect: Flow<{Name}SideEffect> = _sideEffect.receiveAsFlow()

    init {
        // Observe global events that affect this screen
        viewModelScope.launch {
            appEventBus.collect { event ->
                handleGlobalEvent(event)
            }
        }
    }

    fun processIntent(intent: {Name}Intent) {
        viewModelScope.launch {
            when (intent) {
                is {Name}Intent.Load -> handleLoad()
                is {Name}Intent.Refresh -> handleRefresh()
                // ... exhaustive when
            }
        }
    }

    private suspend fun handleLoad() {
        _state.update { it.copy(isLoading = true) }
        when (val result = useCases.getData()) {
            is Result.Success -> _state.update { it.copy(data = result.value, isLoading = false) }
            is Result.Failure -> _state.update { it.copy(error = result.error.toUiError(), isLoading = false) }
        }
    }

    private fun reduce(transform: {Name}State.() -> {Name}State) {
        _state.update { it.transform() }
    }

    private suspend fun emit(effect: {Name}SideEffect) {
        _sideEffect.send(effect)
    }
}
```

### 5.2 State Composition in Composables

```kotlin
@Composable
fun {Name}Screen(
    viewModel: {Name}ViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Side effects (one-shot)
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is {Name}SideEffect.ShowSnackbar -> { /* snackbar host */ }
                is {Name}SideEffect.Navigate -> { /* navigation */ }
            }
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        viewModel.processIntent({Name}Intent.Load)
    }

    // Render
    {Name}Content(
        state = state,
        onIntent = viewModel::processIntent,
    )
}

@Composable
private fun {Name}Content(
    state: {Name}State,
    onIntent: ({Name}Intent) -> Unit,
) {
    // Pure stateless rendering — easily previewable
    when {
        state.isLoading -> SkeletonLoader()
        state.error != null -> ErrorStateWidget(error = state.error, onRetry = { onIntent({Name}Intent.Load) })
        else -> { /* main content */ }
    }
}
```

### 5.3 Global State (Cross-Screen)

| State Type | Mechanism | Scope |
|---|---|---|
| Authentication status | `SessionManager` (Singleton, `StateFlow<SessionState>`) | Application |
| Active alerts | `AlertRepository.getActiveAlerts(): Flow<List<Alert>>` | Database → ReactiveRepository |
| Theme preference | `DataStore<Preferences>` emitting `Flow<ThemeMode>` | Application |
| Sync status | `SyncEventRepository.getLatestStatus(): Flow<SyncStatus>` | Database → ReactiveRepository |
| Network connectivity | `NetworkMonitor.isOnline: StateFlow<Boolean>` | Application |

Global state is **never** stored in a shared ViewModel. Instead, each ViewModel independently observes the relevant repository `Flow` or the `AppEvent` bus. This prevents tight coupling between features.

---

## 6. Reusable Component Strategy

### 6.1 Design System Components (`:core-ui`)

All shared components live in `:core-ui` and are self-contained:

| Component | Props | Used By |
|---|---|---|
| `AlertBanner` | `severity`, `message`, `action`, `onDismiss` | Portfolio, Holdings, Orders, GTT |
| `SkeletonLoader` | `shape`, `count`, `height` | All list screens |
| `FilterChipGroup` | `chips: List<FilterChip>`, `selectedChip`, `onSelect` | Portfolio, Orders, Transactions |
| `EmptyStateWidget` | `icon`, `title`, `subtitle`, `actionLabel`, `onAction` | All list screens |
| `SetupChecklist` | `items: List<ChecklistItem>`, `onItemClick` | Portfolio (empty state) |
| `CurrencyText` | `amount: Paisa`, `style`, `color` | Portfolio, Holdings, Orders, Transactions |
| `PercentageText` | `basisPoints: Int`, `style`, `color` | Portfolio, Holdings |
| `PaginatedLazyColumn` | `pagingItems`, `itemContent`, `loadingContent` | Orders, Transactions |
| `ConfirmationDialog` | `title`, `message`, `confirmLabel`, `onConfirm`, `onDismiss` | Settings, Holdings, Import |
| `StatusIndicator` | `status: SyncStatus` | Portfolio toolbar, Settings |

### 6.2 Component Design Rules

1. **No ViewModel references in shared components.** Components accept data and callbacks as parameters.
2. **Preview-first development.** Every component has at least one `@Preview` composable.
3. **Theme-aware.** All components read colors and typography from `MaterialTheme`. No hardcoded values.
4. **Accessibility-first.** All interactive components have `contentDescription`, minimum 48dp touch targets, and semantic labels.

---

## 7. Design System Architecture

### 7.1 Theme Definition

```kotlin
@Composable
fun KiteWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KiteWatchTypography,
        shapes = KiteWatchShapes,
        content = content,
    )
}
```

### 7.2 Color Palette

```kotlin
// Light Theme
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),         // Blue — primary actions
    onPrimary = Color.White,
    secondary = Color(0xFF34A853),        // Green — positive P&L, inflows
    tertiary = Color(0xFFEA4335),         // Red — negative P&L, outflows, errors
    surface = Color(0xFFFAFAFA),
    background = Color.White,
    error = Color(0xFFD32F2F),
    // Custom extended colors via CompositionLocal:
    // profitGreen, lossRed, warningAmber, chargeOrange
)

// Dark Theme
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF81C995),
    tertiary = Color(0xFFF28B82),
    surface = Color(0xFF1E1E1E),
    background = Color(0xFF121212),
    error = Color(0xFFCF6679),
)

// Extended colors via CompositionLocal
data class ExtendedColors(
    val profitGreen: Color,
    val lossRed: Color,
    val warningAmber: Color,
    val chargeOrange: Color,
    val neutralGray: Color,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
```

### 7.3 Typography

```kotlin
val KiteWatchTypography = Typography(
    displayLarge = TextStyle(fontFamily = GoogleFont("Inter"), fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = GoogleFont("Inter"), fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = GoogleFont("Inter"), fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = GoogleFont("Inter"), fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = GoogleFont("Inter"), fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = GoogleFont("Inter"), fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = GoogleFont("Inter"), fontSize = 11.sp, letterSpacing = 0.5.sp),
)
```

### 7.4 Spacing System

```kotlin
object KiteWatchDimens {
    val spacingXs = 4.dp
    val spacingSm = 8.dp
    val spacingMd = 16.dp
    val spacingLg = 24.dp
    val spacingXl = 32.dp
    val spacingXxl = 48.dp

    val cardElevation = 2.dp
    val cardCornerRadius = 12.dp
    val chipCornerRadius = 8.dp
    val buttonCornerRadius = 8.dp

    val minTouchTarget = 48.dp       // WCAG minimum
    val bottomNavHeight = 80.dp
    val toolbarHeight = 64.dp
}
```

---

## 8. Theming System

### 8.1 Theme Persistence

Theme preference is stored in Jetpack DataStore:

```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class ThemePreference @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[THEME_KEY] ?: ThemeMode.SYSTEM.name)
    }

    suspend fun setTheme(mode: ThemeMode) {
        dataStore.edit { it[THEME_KEY] = mode.name }
    }

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_mode")
    }
}
```

### 8.2 Theme Application

```kotlin
// In :app/MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by themePreference.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            KiteWatchTheme(darkTheme = darkTheme) {
                KiteWatchApp()
            }
        }
    }
}
```

---

## 9. Accessibility Strategy

### 9.1 Implementation Requirements

| Requirement | Implementation |
|---|---|
| WCAG AA contrast ratios | Color palette validated via `ColorContrastChecker`. Both light and dark palettes tested. |
| 48×48dp touch targets | All `IconButton`, `Chip`, `ListItem` row clicks use `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)` |
| Visible labels (no placeholder-only) | All `TextField` composables use `label` parameter. Placeholder is supplementary, not primary. |
| Color + icon/text for alerts | `AlertBanner` includes both color background AND an icon (`Error`, `Warning`, `CheckCircle`) AND text label. |
| System font scaling | No fixed `sp` sizes below 12sp. All text uses `MaterialTheme.typography`. Layout tested at 200% font scale. |
| Content descriptions | All `Image` and `Icon` composables have non-null `contentDescription`. Decorative icons use `contentDescription = null` with `Modifier.semantics { invisibleToUser() }`. |
| Screen reader ordering | `Modifier.semantics { traversalOrder }` used for logical reading order in complex layouts (e.g., holdings cards). |

### 9.2 Accessibility Testing

- **Automated:** TalkBack traversal tests via Compose `onNode().assertIsDisplayed()` + `assertContentDescriptionContains()`.
- **Manual:** Periodic testing with TalkBack enabled on a physical device. Each release requires an accessibility pass.
- **CI:** Android Lint accessibility checks enabled (`a11y` checks are not suppressed).

---

## 10. Localization Readiness

### 10.1 String Externalization

All user-facing strings are in `res/values/strings.xml`:

```xml
<resources>
    <string name="portfolio_title">Portfolio</string>
    <string name="holdings_title">Holdings</string>
    <string name="realized_pnl_label">Realized P&amp;L</string>
    <string name="fund_balance_label">Fund Balance</string>
    <string name="profit_target_label">Profit Target</string>
    <string name="error_holdings_mismatch">Holdings mismatch detected for %1$s: local %2$d, expected %3$d</string>
    <!-- ... -->
</resources>
```

### 10.2 Locale-Aware Formatting

```kotlin
// Currency: uses Indian numbering (1,00,000)
object CurrencyFormatter {
    private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        currency = Currency.getInstance("INR")
        maximumFractionDigits = 2
    }

    fun format(paisa: Paisa): String = inrFormat.format(paisa.toRupees())
}

// Dates: respects user locale
object DateFormatter {
    private val displayFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")
    private val displayDateTimeFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

    fun formatDate(date: LocalDate): String = date.format(displayFormat)
    fun formatDateTime(instant: Instant): String =
        instant.atZone(ZoneId.of("Asia/Kolkata")).format(displayDateTimeFormat)
}
```

### 10.3 RTL Support

- `android:supportsRtl="true"` in `AndroidManifest.xml`.
- All layouts use `start`/`end` instead of `left`/`right` (Compose handles this natively).
- `LayoutDirection` tested in previews.

---

## 11. Error State UX Patterns

### 11.1 Error State Hierarchy

| Level | Visual | Behavior | Examples |
|---|---|---|---|
| **Screen-level** | Full-screen error with retry button | Replaces main content | Network error on initial load, database corruption |
| **Banner-level** | Colored banner at top of screen | Persistent or dismissible, coexists with content | Holdings mismatch, fund mismatch, GTT failure |
| **Inline-level** | Per-item warning indicator | Small icon/text within a list item | Charge rates missing on a holding card, GTT override detected |
| **Snackbar** | Bottom snackbar, auto-dismiss 3s | One-shot feedback | "GTT updated for INFY", "Backup complete" |
| **Dialog** | Modal dialog requiring action | Blocks interaction until dismissed | Confirm CSV import, resolve GTT override |

### 11.2 Error-to-UI Mapping

```kotlin
fun AppError.toUiError(): UiError = when (this) {
    is AppError.Transient.NetworkUnavailable -> UiError.Banner(
        severity = Severity.WARNING,
        message = "No network connection. Some features may be unavailable.",
        action = UiAction.Retry,
    )
    is AppError.Recoverable.HoldingsMismatch -> UiError.Banner(
        severity = Severity.CRITICAL,
        message = "Holdings mismatch detected. Order sync paused.",
        action = UiAction.NavigateTo(Route.HoldingsMismatchDetail(diffs)),
    )
    is AppError.Recoverable.FundMismatchExceedsTolerance -> UiError.Banner(
        severity = Severity.CRITICAL,
        message = "Fund balance differs by ₹${(remoteBalance - localBalance).toRupees()}",
        action = UiAction.NavigateTo(Route.Transactions),
    )
    is AppError.Critical.DatabaseCorruption -> UiError.FullScreen(
        icon = Icons.Error,
        title = "Database Error",
        message = "The local database may be corrupted. Please restore from backup.",
        action = UiAction.NavigateTo(Route.BackupRestore),
    )
    // ... exhaustive mapping
}
```

---

## 12. Empty State UX Patterns

### 12.1 Empty State Definitions

| Screen | Condition | Display |
|---|---|---|
| Portfolio (no data at all) | No orders AND no holdings | Setup Checklist widget with tappable items |
| Portfolio (filtered, no results) | Date range returns no data | "No activity in this period" with filter label |
| Holdings | No current holdings | "No current holdings. Sync orders to populate holdings." with sync button |
| Orders (no filter) | No orders in database | "No orders recorded. Sync today's orders or import historical data." with action buttons |
| Orders (filtered) | Filter returns no results | "No orders match the selected filter." with active filter chips |
| Transactions (filtered) | Filter returns no results | "No transactions match the selected filter." |
| GTT Orders | No active GTTs | "No active GTT orders." |

### 12.2 Setup Checklist (Portfolio Empty State)

```kotlin
data class ChecklistItem(
    val label: String,
    val isComplete: Boolean,
    val route: String,
)

@Composable
fun SetupChecklist(items: List<ChecklistItem>, onItemClick: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(KiteWatchDimens.spacingMd)) {
        Column(modifier = Modifier.padding(KiteWatchDimens.spacingMd)) {
            Text("Get Started", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(KiteWatchDimens.spacingSm))
            items.forEach { item ->
                ChecklistRow(
                    label = item.label,
                    isComplete = item.isComplete,
                    onClick = { onItemClick(item.route) },
                )
            }
        }
    }
}

// Checklist items:
// ☐ Charge rates fetched       → navigates to ChargeRatesRoute
// ☐ First order sync completed → navigates to OrdersRoute (with sync trigger)
// ☐ Profit targets reviewed    → navigates to HoldingsRoute
// ☐ Backup configured          → navigates to BackupRestoreRoute
```

---

## 13. Folder Tree Summary

```
kitewatch/
├── app/
│   └── src/main/java/com/kitewatch/app/
│       ├── KiteWatchApplication.kt
│       ├── MainActivity.kt
│       ├── navigation/
│       │   ├── KiteWatchNavHost.kt
│       │   ├── Routes.kt
│       │   └── BottomNavBar.kt
│       └── di/
│           └── AppModule.kt
│
├── feature/
│   ├── portfolio/src/main/java/com/kitewatch/feature/portfolio/
│   ├── holdings/src/main/java/com/kitewatch/feature/holdings/
│   ├── orders/src/main/java/com/kitewatch/feature/orders/
│   ├── transactions/src/main/java/com/kitewatch/feature/transactions/
│   ├── gtt/src/main/java/com/kitewatch/feature/gtt/
│   ├── settings/src/main/java/com/kitewatch/feature/settings/
│   ├── onboarding/src/main/java/com/kitewatch/feature/onboarding/
│   └── auth/src/main/java/com/kitewatch/feature/auth/
│
├── core/
│   ├── domain/src/main/java/com/kitewatch/domain/
│   ├── data/src/main/java/com/kitewatch/data/
│   ├── network/src/main/java/com/kitewatch/network/
│   ├── database/src/main/java/com/kitewatch/database/
│   └── ui/src/main/java/com/kitewatch/ui/
│
├── infra/
│   ├── worker/src/main/java/com/kitewatch/infra/worker/
│   ├── auth/src/main/java/com/kitewatch/infra/auth/
│   ├── backup/src/main/java/com/kitewatch/infra/backup/
│   └── csv/src/main/java/com/kitewatch/infra/csv/
│
├── build-logic/
│   └── convention/
│       └── src/main/kotlin/
│           ├── AndroidApplicationConventionPlugin.kt
│           ├── AndroidLibraryConventionPlugin.kt
│           ├── AndroidComposeConventionPlugin.kt
│           ├── AndroidHiltConventionPlugin.kt
│           └── KotlinLibraryConventionPlugin.kt
│
├── gradle/
│   └── libs.versions.toml          // Version catalog
│
├── docs/
│   ├── 00_PRODUCT_SPECIFICATION.md
│   ├── 01_SYSTEM_ARCHITECTURE.md
│   ├── 02_TECH_DECISIONS.md
│   ├── 03_DATABASE_SCHEMA.md
│   ├── 04_DOMAIN_ENGINE_DESIGN.md
│   └── 05_APPLICATION_STRUCTURE.md
│
├── schemas/                         // Room schema exports for migration testing
│   └── com.kitewatch.database.KiteWatchDatabase/
│       └── 1.json
│
├── build.gradle.kts                 // Root build file
├── settings.gradle.kts              // Module registration
└── gradle.properties                // Build config flags
```

---

## 14. Convention Plugin Examples

### 14.1 Android Library Convention

```kotlin
// build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }
            extensions.configure<LibraryExtension> {
                compileSdk = 35
                defaultConfig.minSdk = 26
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                    allWarningsAsErrors.set(true)
                }
            }
        }
    }
}
```

### 14.2 Compose Convention

```kotlin
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<LibraryExtension> {
                buildFeatures.compose = true
            }
            dependencies {
                val bom = platform(libs.findLibrary("compose-bom").get())
                "implementation"(bom)
                "implementation"(libs.findLibrary("compose-ui").get())
                "implementation"(libs.findLibrary("compose-material3").get())
                "implementation"(libs.findLibrary("compose-ui-tooling-preview").get())
                "debugImplementation"(libs.findLibrary("compose-ui-tooling").get())
            }
        }
    }
}
```

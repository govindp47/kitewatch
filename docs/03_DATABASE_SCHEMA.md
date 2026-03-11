# 03 — Database Schema Design

**Version:** 1.0
**Product:** KiteWatch — Android Local-First Portfolio Management
**Last Updated:** 2026-03-10

---

## 1. Schema Design Philosophy

- **Append-only transaction log.** The `transactions` table is never updated or deleted programmatically. Every financial event is an immutable ledger entry.
- **Monetary values as Long (paisa).** All monetary amounts are stored as `Long` representing paisa (1/100 INR). This eliminates floating-point precision errors in aggregation queries.
- **UTC timestamps.** All `_at` columns store ISO-8601 strings in UTC. Display-layer converts to `Asia/Kolkata`.
- **Soft delete via archive flag.** Entities that may become inactive (GTT records, alerts) use an `is_archived` flag rather than physical deletion.
- **Bound-account isolation.** The `account_binding` table stores the single bound Zerodha account. All data implicitly belongs to this account. No per-row account foreign key is needed (single-tenant on-device DB).

---

## 2. Text-Based ER Diagram

```
┌──────────────────┐       ┌──────────────────┐
│  account_binding │       │   charge_rates   │
│──────────────────│       │──────────────────│
│  id (PK)         │       │  id (PK)         │
│  zerodha_user_id │       │  rate_type       │
│  bound_at        │       │  rate_value      │
│  api_key         │       │  effective_from  │
│  access_token    │       │  fetched_at      │
└──────────────────┘       └──────────────────┘

┌──────────────────┐       ┌─────────────────────┐       ┌──────────────────┐
│     orders       │       │     holdings        │       │   gtt_records    │
│──────────────────│       │─────────────────────│       │──────────────────│
│  id (PK)         │       │  id (PK)            │       │  id (PK)         │
│  zerodha_order_id│       │  stock_code         │       │  zerodha_gtt_id  │
│  stock_code      │──┐    │  stock_name         │    ┌──│  stock_code (FK) │
│  stock_name      │  │    │  quantity           │    │  │  trigger_price   │
│  order_type      │  │    │  invested_amount    │    │  │  quantity        │
│  quantity        │  │    │  avg_buy_price      │    │  │  gtt_status      │
│  price           │  │    │  total_buy_charges  │    │  │  is_app_managed  │
│  total_value     │  │    │  profit_target_type │    │  │  last_synced_at  │
│  trade_date      │  │    │  profit_target_value│    │  │  is_archived     │
│  exchange        │  │    │  target_sell_price  │    │  │  created_at      │
│  settlement_id   │  │    │  created_at         │    │  │  updated_at      │
│  source          │  │    │  updated_at         │    │  └──────────────────┘
│  created_at      │  │    └─────────────────────┘    │
└──────────────────┘  │              │                  │
         │            │              │                  │
         │            │    ┌─────────┴──────────┐      │
         │            └───▶│   order_holdings   │◀─────┘
         │                 │    (junction)       │
         │                 │────────────────────│
         │                 │  order_id (FK)     │
         │                 │  holding_id (FK)   │
         │                 │  quantity          │
         │                 └────────────────────┘
         │
         ▼
┌──────────────────────┐
│     transactions     │
│──────────────────────│
│  id (PK)             │
│  type                │
│  sub_type            │
│  reference_id        │
│  stock_code          │
│  amount              │
│  running_fund_balance│
│  description         │
│  transaction_date    │
│  source              │
│  created_at          │
└──────────────────────┘

┌──────────────────────┐     ┌──────────────────────┐
│   fund_entries       │     │   gmail_scan_cache    │
│──────────────────────│     │──────────────────────│
│  id (PK)             │     │  id (PK)             │
│  entry_type          │     │  gmail_message_id    │
│  amount              │     │  detected_type       │
│  entry_date          │     │  detected_amount     │
│  note                │     │  email_date          │
│  is_gmail_detected   │     │  email_subject       │
│  gmail_message_id    │     │  status              │
│  reconciliation_id   │     │  linked_fund_entry_id│
│  created_at          │     │  scanned_at          │
└──────────────────────┘     └──────────────────────┘

┌──────────────────────┐     ┌──────────────────────┐
│  persistent_alerts   │     │   sync_event_log     │
│──────────────────────│     │──────────────────────│
│  id (PK)             │     │  id (PK)             │
│  alert_type          │     │  event_type          │
│  payload             │     │  started_at          │
│  acknowledged        │     │  completed_at        │
│  created_at          │     │  status              │
│  resolved_at         │     │  details             │
└──────────────────────┘     │  error_message       │
                             └──────────────────────┘

┌──────────────────────┐     ┌──────────────────────┐
│  pnl_monthly_cache   │     │  worker_handoff      │
│──────────────────────│     │──────────────────────│
│  id (PK)             │     │  id (PK)             │
│  year_month          │     │  worker_tag          │
│  total_sell_value    │     │  payload             │
│  total_buy_cost_sold │     │  created_at          │
│  total_buy_charges   │     │  consumed            │
│  total_sell_charges  │     └──────────────────────┘
│  realized_pnl        │
│  invested_value      │
│  last_updated_at     │
└──────────────────────┘

┌──────────────────────┐
│  gmail_filters       │
│──────────────────────│
│  id (PK)             │
│  filter_type         │
│  filter_value        │
│  is_active           │
│  created_at          │
└──────────────────────┘

┌──────────────────────┐
│  backup_history      │
│──────────────────────│
│  id (PK)             │
│  backup_type         │
│  destination         │
│  file_name           │
│  file_size_bytes     │
│  schema_version      │
│  created_at          │
│  status              │
└──────────────────────┘
```

---

## 3. Complete Entity Definitions (SQL DDL)

### 3.1 `account_binding`

Stores the single permanently bound Zerodha account. Only one row ever exists.

```sql
CREATE TABLE account_binding (
    id                  INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    zerodha_user_id     TEXT    NOT NULL,
    api_key             TEXT    NOT NULL,      -- Kite Connect API key (encrypted at DB level via SQLCipher)
    access_token        TEXT,                  -- Current session token; NULL when expired
    token_expires_at    TEXT,                  -- ISO-8601 UTC; NULL when no active session
    bound_at            TEXT    NOT NULL,      -- ISO-8601 UTC; timestamp of initial binding
    last_auth_at        TEXT,                  -- ISO-8601 UTC; last successful authentication
    
    CONSTRAINT single_row CHECK (id = 1)
);
```

**Notes:**

- `CHECK (id = 1)` enforces the single-row invariant at the database level.
- `access_token` and `api_key` are stored in the SQLCipher-encrypted database. Additionally, the access token is duplicated in `EncryptedSharedPreferences` for quick access during API calls without opening a DB connection.

---

### 3.2 `orders`

Every executed equity delivery order, from API sync or CSV import.

```sql
CREATE TABLE orders (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    zerodha_order_id    TEXT    NOT NULL UNIQUE,   -- Zerodha's unique order ID
    stock_code          TEXT    NOT NULL,           -- Trading symbol (e.g., 'INFY')
    stock_name          TEXT    NOT NULL,           -- Full name (e.g., 'Infosys Limited')
    exchange            TEXT    NOT NULL,           -- 'NSE' or 'BSE'
    order_type          TEXT    NOT NULL CHECK (order_type IN ('BUY', 'SELL')),
    quantity            INTEGER NOT NULL CHECK (quantity > 0),
    price_paisa         INTEGER NOT NULL CHECK (price_paisa > 0),  -- Per-unit price in paisa
    total_value_paisa   INTEGER NOT NULL CHECK (total_value_paisa > 0),  -- quantity × price in paisa
    trade_date          TEXT    NOT NULL,           -- ISO-8601 date (YYYY-MM-DD)
    trade_timestamp     TEXT    NOT NULL,           -- ISO-8601 UTC datetime of execution
    settlement_id       TEXT,                       -- Exchange settlement ID, nullable
    instrument_token    INTEGER,                    -- Kite instrument token for cross-referencing
    source              TEXT    NOT NULL DEFAULT 'API' CHECK (source IN ('API', 'CSV_IMPORT')),
    created_at          TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX idx_orders_stock_code ON orders(stock_code);
CREATE INDEX idx_orders_trade_date ON orders(trade_date DESC);
CREATE INDEX idx_orders_type_date ON orders(order_type, trade_date DESC);
CREATE INDEX idx_orders_stock_date ON orders(stock_code, trade_date DESC);
```

**Design Decisions:**

- `zerodha_order_id UNIQUE` prevents duplicate order insertion from both API sync and CSV import.
- `price_paisa` and `total_value_paisa` are `INTEGER` (Long in Kotlin) to avoid floating-point precision issues.
- `instrument_token` is nullable because CSV-imported orders may not have this field.
- No `updated_at` — orders are immutable after insertion.

---

### 3.3 `holdings`

Current equity positions, derived from cumulative order history.

```sql
CREATE TABLE holdings (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code              TEXT    NOT NULL UNIQUE,
    stock_name              TEXT    NOT NULL,
    exchange                TEXT    NOT NULL,
    quantity                INTEGER NOT NULL CHECK (quantity >= 0),
    invested_amount_paisa   INTEGER NOT NULL CHECK (invested_amount_paisa >= 0),  -- Total cost basis in paisa
    avg_buy_price_paisa     INTEGER NOT NULL CHECK (avg_buy_price_paisa >= 0),    -- Weighted average buy price
    total_buy_charges_paisa INTEGER NOT NULL DEFAULT 0,                           -- Sum of all buy charges
    profit_target_type      TEXT    NOT NULL DEFAULT 'PERCENTAGE'
                            CHECK (profit_target_type IN ('PERCENTAGE', 'ABSOLUTE')),
    profit_target_value     INTEGER NOT NULL DEFAULT 500,  -- 500 = 5.00% or ₹5.00 depending on type
                                                           -- Stored as basis points (% × 100) or paisa
    target_sell_price_paisa INTEGER NOT NULL DEFAULT 0,    -- Computed: (invested + target + est_sell_charges) / qty
    instrument_token        INTEGER,
    created_at              TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    updated_at              TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE UNIQUE INDEX idx_holdings_stock_code ON holdings(stock_code);
```

**Design Decisions:**

- `quantity = 0` is allowed temporarily during sync processing. After verification, zero-quantity holdings are cleaned up.
- `profit_target_value` encoding: for `PERCENTAGE` type, value `500` = 5.00% (basis points). For `ABSOLUTE` type, value is in paisa. This avoids floating-point for percentages.
- `target_sell_price_paisa` is denormalized (computed from other fields) for display performance. Recalculated whenever holdings are updated or profit targets change.
- `stock_code UNIQUE` enforces one holding row per stock. Multiple purchases accumulate into the same row with updated averages.

---

### 3.4 `order_holdings_link`

Junction table linking orders to holdings for lot tracking and P&L attribution.

```sql
CREATE TABLE order_holdings_link (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id        INTEGER NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    holding_id      INTEGER NOT NULL REFERENCES holdings(id) ON DELETE RESTRICT,
    quantity        INTEGER NOT NULL CHECK (quantity > 0),  -- Qty from this order contributing to this holding
    
    CONSTRAINT uq_order_holding UNIQUE (order_id, holding_id)
);

CREATE INDEX idx_ohl_order ON order_holdings_link(order_id);
CREATE INDEX idx_ohl_holding ON order_holdings_link(holding_id);
```

**Purpose:** When a stock is bought in multiple lots, this table tracks which orders contributed which quantities. This enables FIFO-based cost attribution for sells and accurate per-lot charge tracking.

---

### 3.5 `transactions`

Immutable financial event log — the core audit trail.

```sql
CREATE TABLE transactions (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    type                        TEXT    NOT NULL CHECK (type IN (
                                    'FUND_ADDITION',
                                    'FUND_WITHDRAWAL',
                                    'EQUITY_BUY',
                                    'EQUITY_SELL',
                                    'BROKERAGE_CHARGE',
                                    'STT_CHARGE',
                                    'EXCHANGE_CHARGE',
                                    'GST_CHARGE',
                                    'SEBI_CHARGE',
                                    'STAMP_DUTY_CHARGE',
                                    'MISC_ADJUSTMENT',
                                    'DP_CHARGE'
                                )),
    sub_type                    TEXT,            -- Optional sub-classification (e.g., 'AUTO_RECONCILIATION')
    reference_id                TEXT,            -- FK-like: order's zerodha_order_id or fund_entry id
    reference_type              TEXT CHECK (reference_type IN ('ORDER', 'FUND_ENTRY', 'RECONCILIATION', NULL)),
    stock_code                  TEXT,            -- Null for fund/adjustment transactions
    amount_paisa                INTEGER NOT NULL, -- Positive = inflow, Negative = outflow
    running_fund_balance_paisa  INTEGER,         -- Running balance AFTER this transaction (for fund-affecting txns)
    description                 TEXT    NOT NULL, -- Human-readable: "Buy 10 INFY @ ₹1,500.00"
    transaction_date            TEXT    NOT NULL, -- ISO-8601 date
    source                      TEXT    NOT NULL DEFAULT 'SYSTEM'
                                CHECK (source IN ('SYSTEM', 'MANUAL', 'GMAIL', 'CSV_IMPORT', 'RECONCILIATION')),
    created_at                  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX idx_txn_type ON transactions(type);
CREATE INDEX idx_txn_date ON transactions(transaction_date DESC);
CREATE INDEX idx_txn_type_date ON transactions(type, transaction_date DESC);
CREATE INDEX idx_txn_stock ON transactions(stock_code, transaction_date DESC);
CREATE INDEX idx_txn_reference ON transactions(reference_type, reference_id);
```

**Design Decisions:**

- **No UPDATE, no DELETE.** This table is append-only. Corrections are modeled as new compensating entries (e.g., a `MISC_ADJUSTMENT` to offset an incorrect entry).
- `amount_paisa` sign convention: positive = money in (fund addition, equity sell proceeds), negative = money out (fund withdrawal, equity buy, all charges).
- `running_fund_balance_paisa` is maintained only for fund-affecting transactions (`FUND_ADDITION`, `FUND_WITHDRAWAL`, `MISC_ADJUSTMENT`). Equity and charge transactions don't affect the fund balance directly (the fund balance decreases on buy, increases on sell — but this is tracked separately via order values). **Clarification:** Buy orders and sell orders DO affect fund balance. The running balance is updated for ALL transaction types that change the cash position.
- Each charge type gets its own row per order, enabling granular charge breakdowns in the Portfolio and Transactions screens.

---

### 3.6 `fund_entries`

User-initiated fund additions and withdrawals (manual or Gmail-detected).

```sql
CREATE TABLE fund_entries (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    entry_type          TEXT    NOT NULL CHECK (entry_type IN ('ADDITION', 'WITHDRAWAL')),
    amount_paisa        INTEGER NOT NULL CHECK (amount_paisa > 0),  -- Always positive; type determines direction
    entry_date          TEXT    NOT NULL,        -- ISO-8601 date
    note                TEXT,                    -- User-provided note, optional
    is_gmail_detected   INTEGER NOT NULL DEFAULT 0,  -- Boolean: 1 if detected via Gmail
    gmail_message_id    TEXT,                    -- Gmail message ID if detected; for dedup
    reconciliation_id   TEXT,                    -- Links to a reconciliation event if auto-generated
    created_at          TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX idx_fund_type_date ON fund_entries(entry_type, entry_date DESC);
CREATE INDEX idx_fund_gmail ON fund_entries(gmail_message_id) WHERE gmail_message_id IS NOT NULL;
```

**Duplicate Detection Query:**

```sql
-- Check for potential duplicates before insert
SELECT id, entry_type, amount_paisa, entry_date 
FROM fund_entries
WHERE entry_type = :type
  AND amount_paisa = :amount
  AND entry_date BETWEEN date(:date, '-1 day') AND date(:date, '+1 day');
```

---

### 3.7 `charge_rates`

Locally stored Zerodha charge rate table, versioned by fetch timestamp.

```sql
CREATE TABLE charge_rates (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    rate_type       TEXT    NOT NULL CHECK (rate_type IN (
                        'BROKERAGE_DELIVERY',
                        'STT_BUY',
                        'STT_SELL',
                        'EXCHANGE_NSE',
                        'EXCHANGE_BSE',
                        'GST',
                        'SEBI',
                        'STAMP_DUTY',
                        'DP_CHARGES_PER_SCRIPT'
                    )),
    rate_value      INTEGER NOT NULL,   -- Basis points (for %), or paisa (for flat fees)
    rate_unit       TEXT    NOT NULL CHECK (rate_unit IN ('BASIS_POINTS', 'PAISA_FLAT', 'PAISA_PER_UNIT')),
    effective_from  TEXT    NOT NULL,    -- ISO-8601 date
    fetched_at      TEXT    NOT NULL,    -- ISO-8601 UTC timestamp of fetch
    is_current      INTEGER NOT NULL DEFAULT 1,  -- Boolean: 1 = active rate set
    
    CONSTRAINT uq_rate_type_effective UNIQUE (rate_type, effective_from)
);

CREATE INDEX idx_charge_current ON charge_rates(is_current) WHERE is_current = 1;
```

**Rate Value Encoding:**

| Rate Type | Unit | Example Value | Meaning |
|---|---|---|---|
| `BROKERAGE_DELIVERY` | `BASIS_POINTS` | `0` | 0.00% (Zerodha zero brokerage on delivery) |
| `STT_BUY` | `BASIS_POINTS` | `10` | 0.10% of buy value |
| `STT_SELL` | `BASIS_POINTS` | `25` | 0.25% of sell value |
| `EXCHANGE_NSE` | `BASIS_POINTS` | `297` | 0.00297% (stored as value/100000 in calc) |
| `GST` | `BASIS_POINTS` | `1800` | 18.00% of (brokerage + exchange charges) |
| `SEBI` | `BASIS_POINTS` | `1` | ₹10 per crore (special handling in calculator) |
| `STAMP_DUTY` | `BASIS_POINTS` | `15` | 0.015% on buy side |
| `DP_CHARGES_PER_SCRIPT` | `PAISA_FLAT` | `1580` | ₹15.80 per sell script per day |

**Versioning Strategy:** When new rates are fetched, existing rows are marked `is_current = 0` and new rows are inserted with `is_current = 1`. Historical rates are never deleted — this preserves the ability to audit past charge calculations.

---

### 3.8 `gtt_records`

Local mirror of GTT orders in Zerodha.

```sql
CREATE TABLE gtt_records (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    zerodha_gtt_id          INTEGER UNIQUE,         -- Zerodha's GTT ID; NULL if pending creation
    stock_code              TEXT    NOT NULL,
    trigger_type            TEXT    NOT NULL DEFAULT 'SINGLE' CHECK (trigger_type = 'SINGLE'),
    trigger_price_paisa     INTEGER NOT NULL CHECK (trigger_price_paisa > 0),
    sell_quantity           INTEGER NOT NULL CHECK (sell_quantity > 0),
    gtt_status              TEXT    NOT NULL DEFAULT 'PENDING_CREATION' CHECK (gtt_status IN (
                                'PENDING_CREATION',
                                'ACTIVE',
                                'TRIGGERED',
                                'CANCELLED',
                                'REJECTED',
                                'EXPIRED',
                                'PENDING_UPDATE'
                            )),
    is_app_managed          INTEGER NOT NULL DEFAULT 1,  -- 1 = created/managed by KiteWatch
    app_calculated_price    INTEGER,                     -- Expected trigger price (paisa); used for override detection
    manual_override_detected INTEGER NOT NULL DEFAULT 0, -- 1 = Zerodha value differs from app-calculated
    holding_id              INTEGER REFERENCES holdings(id) ON DELETE SET NULL,
    last_synced_at          TEXT,                         -- Last verified against Zerodha API
    is_archived             INTEGER NOT NULL DEFAULT 0,   -- 1 = holding fully sold or GTT completed
    created_at              TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    updated_at              TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX idx_gtt_stock ON gtt_records(stock_code);
CREATE INDEX idx_gtt_status ON gtt_records(gtt_status) WHERE is_archived = 0;
CREATE INDEX idx_gtt_holding ON gtt_records(holding_id);
```

**Override Detection Logic:**

```sql
-- After fetching GTT list from Zerodha, detect manual overrides:
UPDATE gtt_records
SET manual_override_detected = 1,
    updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
WHERE zerodha_gtt_id = :zerodhaGttId
  AND app_calculated_price IS NOT NULL
  AND trigger_price_paisa != app_calculated_price;
```

---

### 3.9 `persistent_alerts`

Alerts that survive app restarts and require user acknowledgment or automatic resolution.

```sql
CREATE TABLE persistent_alerts (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    alert_type      TEXT    NOT NULL CHECK (alert_type IN (
                        'HOLDINGS_MISMATCH',
                        'FUND_MISMATCH',
                        'GTT_VERIFICATION_FAILED',
                        'GTT_MANUAL_OVERRIDE',
                        'SYNC_FAILED',
                        'CHARGE_RATES_OUTDATED',
                        'SESSION_EXPIRED'
                    )),
    severity        TEXT    NOT NULL CHECK (severity IN ('CRITICAL', 'WARNING', 'INFO')),
    payload         TEXT    NOT NULL,    -- JSON blob with alert-specific structured data
    acknowledged    INTEGER NOT NULL DEFAULT 0,
    created_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    resolved_at     TEXT,
    resolved_by     TEXT                 -- 'USER_ACK', 'AUTO_RESOLVED', 'SUPERSEDED'
);

CREATE INDEX idx_alerts_active ON persistent_alerts(alert_type) 
    WHERE acknowledged = 0 AND resolved_at IS NULL;
```

**Payload Examples:**

```json
// HOLDINGS_MISMATCH
{
  "diffs": [
    {"stock_code": "INFY", "local_qty": 10, "remote_qty": 15},
    {"stock_code": "TCS", "local_qty": 5, "remote_qty": 5}
  ],
  "sync_event_id": 142
}

// FUND_MISMATCH
{
  "local_balance_paisa": 5000000,
  "remote_balance_paisa": 5250000,
  "difference_paisa": 250000,
  "tolerance_paisa": 5000
}

// GTT_MANUAL_OVERRIDE
{
  "stock_code": "RELIANCE",
  "zerodha_gtt_id": 98765,
  "app_calculated_price_paisa": 260000,
  "zerodha_price_paisa": 270000
}
```

---

### 3.10 `sync_event_log`

Audit trail for every background operation.

```sql
CREATE TABLE sync_event_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type      TEXT    NOT NULL CHECK (event_type IN (
                        'ORDER_SYNC',
                        'FUND_RECONCILIATION',
                        'GTT_UPDATE',
                        'GTT_CREATE',
                        'CHARGE_RATE_REFRESH',
                        'BACKUP',
                        'RESTORE',
                        'CSV_IMPORT',
                        'GMAIL_SCAN'
                    )),
    started_at      TEXT    NOT NULL,
    completed_at    TEXT,
    status          TEXT    NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL', 'SKIPPED')),
    details         TEXT,           -- JSON blob: {"new_orders": 3, "stocks_affected": ["INFY", "TCS"]}
    error_message   TEXT,
    worker_tag      TEXT            -- WorkManager unique work name for correlation
);

CREATE INDEX idx_sync_type_date ON sync_event_log(event_type, started_at DESC);
CREATE INDEX idx_sync_status ON sync_event_log(status) WHERE status IN ('RUNNING', 'FAILED');
```

---

### 3.11 `pnl_monthly_cache`

Pre-aggregated P&L data per month for fast Portfolio screen rendering.

```sql
CREATE TABLE pnl_monthly_cache (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    year_month                  TEXT    NOT NULL UNIQUE,  -- 'YYYY-MM' format
    total_sell_value_paisa      INTEGER NOT NULL DEFAULT 0,
    total_buy_cost_sold_paisa   INTEGER NOT NULL DEFAULT 0,  -- Cost basis of sold positions
    total_buy_charges_paisa     INTEGER NOT NULL DEFAULT 0,  -- Buy charges for sold positions
    total_sell_charges_paisa    INTEGER NOT NULL DEFAULT 0,
    realized_pnl_paisa          INTEGER NOT NULL DEFAULT 0,  -- sell - buy_cost - buy_charges - sell_charges
    invested_value_paisa        INTEGER NOT NULL DEFAULT 0,  -- Total buy value for period (including unsold)
    order_count                 INTEGER NOT NULL DEFAULT 0,
    last_updated_at             TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE UNIQUE INDEX idx_pnl_month ON pnl_monthly_cache(year_month);
```

**Incremental Update Strategy:**
When new orders are synced, only the monthly row for the order's `trade_date` month is recalculated. Full recalculation is triggered only on CSV import (bulk data) or restore operations.

---

### 3.12 `gmail_scan_cache`

Tracks Gmail messages already scanned to prevent re-processing.

```sql
CREATE TABLE gmail_scan_cache (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    gmail_message_id    TEXT    NOT NULL UNIQUE,
    detected_type       TEXT    CHECK (detected_type IN ('ADDITION', 'WITHDRAWAL', NULL)),
    detected_amount_paisa INTEGER,
    email_date          TEXT    NOT NULL,
    email_subject       TEXT,
    email_sender        TEXT,
    status              TEXT    NOT NULL CHECK (status IN ('PENDING_REVIEW', 'CONFIRMED', 'REJECTED', 'IGNORED')),
    linked_fund_entry_id INTEGER REFERENCES fund_entries(id),
    scanned_at          TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE UNIQUE INDEX idx_gmail_msg ON gmail_scan_cache(gmail_message_id);
CREATE INDEX idx_gmail_status ON gmail_scan_cache(status) WHERE status = 'PENDING_REVIEW';
```

---

### 3.13 `gmail_filters`

User-defined email filters for fund transaction detection.

```sql
CREATE TABLE gmail_filters (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    filter_type     TEXT    NOT NULL CHECK (filter_type IN ('SENDER', 'SUBJECT_CONTAINS')),
    filter_value    TEXT    NOT NULL,
    is_active       INTEGER NOT NULL DEFAULT 1,
    created_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);
```

---

### 3.14 `backup_history`

Records of all backup operations for display in Settings.

```sql
CREATE TABLE backup_history (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    backup_type     TEXT    NOT NULL CHECK (backup_type IN ('FULL', 'INCREMENTAL')),
    destination     TEXT    NOT NULL CHECK (destination IN ('GOOGLE_DRIVE', 'LOCAL')),
    file_name       TEXT    NOT NULL,
    file_size_bytes INTEGER,
    schema_version  INTEGER NOT NULL,
    drive_file_id   TEXT,           -- Google Drive file ID for Drive backups
    status          TEXT    NOT NULL CHECK (status IN ('SUCCESS', 'FAILED', 'LOCAL_FALLBACK')),
    error_message   TEXT,
    created_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX idx_backup_date ON backup_history(created_at DESC);
```

---

### 3.15 `worker_handoff`

Transient data transfer between chained WorkManager workers.

```sql
CREATE TABLE worker_handoff (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    worker_tag  TEXT    NOT NULL,
    payload     TEXT    NOT NULL,    -- JSON: {"affected_stock_codes": ["INFY", "TCS"]}
    consumed    INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX idx_handoff_tag ON worker_handoff(worker_tag) WHERE consumed = 0;
```

---

## 4. Relationships Summary

| Relationship | Type | Constraint |
|---|---|---|
| `orders` → `transactions` | One-to-Many | Via `reference_id` + `reference_type = 'ORDER'` (soft FK) |
| `orders` → `holdings` | Many-to-Many | Via `order_holdings_link` junction table |
| `fund_entries` → `transactions` | One-to-One | Via `reference_id` + `reference_type = 'FUND_ENTRY'` (soft FK) |
| `holdings` → `gtt_records` | One-to-One | Via `gtt_records.holding_id` FK |
| `gmail_scan_cache` → `fund_entries` | One-to-One | Via `linked_fund_entry_id` FK (nullable) |
| `charge_rates` — standalone | Lookup table | Referenced by charge calculator logic, not by FK |
| `pnl_monthly_cache` — standalone | Derived/aggregate | Computed from `orders` + `transactions` data |

**Soft FK Pattern:** The `transactions` table uses `reference_id` + `reference_type` as a polymorphic reference instead of hard foreign keys. This keeps the transactions table decoupled as an append-only ledger — it can reference orders, fund entries, or reconciliation events without schema-level coupling. Integrity is enforced at the application layer.

---

## 5. Index Strategy

### Index Purpose Matrix

| Index | Table | Purpose | Query Pattern |
|---|---|---|---|
| `idx_orders_stock_code` | orders | Stock-specific order history lookup | `WHERE stock_code = ?` |
| `idx_orders_trade_date` | orders | Chronological order listing | `ORDER BY trade_date DESC` |
| `idx_orders_type_date` | orders | Buy/Sell filtered views | `WHERE order_type = ? ORDER BY trade_date DESC` |
| `idx_orders_stock_date` | orders | Stock + date range queries | `WHERE stock_code = ? AND trade_date BETWEEN ? AND ?` |
| `idx_holdings_stock_code` | holdings | Stock lookup for sync | `WHERE stock_code = ?` |
| `idx_txn_type` | transactions | Type-filtered transaction list | `WHERE type = ?` |
| `idx_txn_date` | transactions | Chronological listing | `ORDER BY transaction_date DESC` |
| `idx_txn_type_date` | transactions | Filtered + sorted | `WHERE type IN (?) ORDER BY transaction_date DESC` |
| `idx_txn_stock` | transactions | Per-stock charge breakdown | `WHERE stock_code = ? ORDER BY transaction_date DESC` |
| `idx_txn_reference` | transactions | Lookup charges for a specific order | `WHERE reference_type = 'ORDER' AND reference_id = ?` |
| `idx_gtt_stock` | gtt_records | GTT lookup by stock | `WHERE stock_code = ?` |
| `idx_gtt_status` | gtt_records | Active GTT listing | `WHERE gtt_status = ? AND is_archived = 0` |
| `idx_charge_current` | charge_rates | Current rates for calculation | `WHERE is_current = 1` |
| `idx_alerts_active` | persistent_alerts | Active alerts for UI display | `WHERE acknowledged = 0 AND resolved_at IS NULL` |
| `idx_sync_type_date` | sync_event_log | Last sync status per type | `WHERE event_type = ? ORDER BY started_at DESC LIMIT 1` |
| `idx_pnl_month` | pnl_monthly_cache | Monthly P&L lookup | `WHERE year_month = ?` |
| `idx_gmail_msg` | gmail_scan_cache | Dedup check on Gmail scan | `WHERE gmail_message_id = ?` |

---

## 6. Constraints & Integrity Rules

### 6.1 Database-Level Constraints

| Constraint | Table | Rule |
|---|---|---|
| `account_binding.id = 1` | account_binding | Enforces single-row table |
| `zerodha_order_id UNIQUE` | orders | Prevents duplicate order insertion |
| `stock_code UNIQUE` | holdings | One row per stock |
| `order_type IN ('BUY', 'SELL')` | orders | Enum enforcement |
| `quantity > 0` | orders, gtt_records | No zero-quantity records |
| `price_paisa > 0` | orders | No zero-price orders |
| `amount_paisa > 0` | fund_entries | Fund entries are always positive |
| `rate_type, effective_from UNIQUE` | charge_rates | One rate value per type per effective date |
| `gmail_message_id UNIQUE` | gmail_scan_cache | No re-processing of same email |

### 6.2 Application-Level Integrity Invariants

These invariants are enforced by the Domain layer and verified by consistency tests:

| ID | Invariant | Enforcement |
|---|---|---|
| INV-01 | `holdings.quantity = SUM(buy orders qty) - SUM(sell orders qty)` for each stock | Verified after every order sync |
| INV-02 | `holdings.invested_amount_paisa` = cost basis of remaining lots (FIFO) | Recalculated on every holdings update |
| INV-03 | `holdings.avg_buy_price_paisa = invested_amount / quantity` (when quantity > 0) | Derived field; recalculated on update |
| INV-04 | Every order has exactly one `EQUITY_BUY` or `EQUITY_SELL` transaction with matching value | Enforced in order sync transaction |
| INV-05 | Every BUY order has charge transactions (STT, exchange, GST, SEBI, stamp duty) | Enforced in charge calculator |
| INV-06 | Every SELL order has charge transactions + DP charge if applicable | Enforced in charge calculator |
| INV-07 | `gtt_records.sell_quantity = holdings.quantity` for app-managed GTTs | Verified after every GTT update |
| INV-08 | `pnl_monthly_cache` is consistent with raw order + transaction data | Full recalculation validation on demand |
| INV-09 | Running fund balance = initial_balance + SUM(fund_additions) - SUM(fund_withdrawals) - SUM(buy_values) + SUM(sell_values) - SUM(charges) + SUM(adjustments) | Verified during reconciliation |
| INV-10 | No transaction row is ever updated or deleted | Enforced by DAO (no `@Update` or `@Delete` on TransactionDao) |

---

## 7. Migration Strategy

### 7.1 Room Migration Framework

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Example: adding a new column
        db.execSQL("ALTER TABLE holdings ADD COLUMN instrument_token INTEGER")
    }
}

val database = Room.databaseBuilder(context, KiteWatchDatabase::class.java, "kitewatch.db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3 /* ... */)
    .build()
```

### 7.2 Migration Rules

1. **No destructive migration.** `fallbackToDestructiveMigration()` is never called. Every schema change has a corresponding `Migration` object.
2. **Every migration is tested.** `MigrationTestHelper` verifies data preservation across each migration path.
3. **Additive-only changes preferred.** New columns with defaults, new tables, new indexes. Column renames and type changes are avoided when possible.
4. **Migration scripts are idempotent** where feasible (e.g., `CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` via try-catch).
5. **Schema version is embedded in backup files.** On restore, the app applies any necessary migrations to bring the restored data to the current schema before merging.

### 7.3 Migration Testing Pattern

```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KiteWatchDatabase::class.java
    )

    @Test
    fun migrate1To2() {
        // Create DB at version 1
        val db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO holdings (...) VALUES (...)")
            close()
        }
        // Migrate to version 2
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        // Verify data is preserved
        val cursor = migratedDb.query("SELECT instrument_token FROM holdings")
        assertThat(cursor.count).isEqualTo(1)
        cursor.moveToFirst()
        assertThat(cursor.isNull(0)).isTrue() // New column should be NULL
    }
}
```

---

## 8. Versioning Strategy

| Component | Strategy |
|---|---|
| Room schema version | Integer, incremented with every schema change |
| Backup format version | Separate integer in backup header; independent of Room schema |
| Charge rate version | Implicit via `fetched_at` timestamp; no explicit version number |
| App version | SemVer in `build.gradle.kts`; displayed in About screen |

**Schema Export:** Room's `exportSchema = true` generates JSON schema files per version in `schemas/` directory. These files are checked into version control for migration test validation.

---

## 9. Audit Trail Model

The `transactions` table serves as the primary audit trail. Additionally:

| Audit Concern | Mechanism |
|---|---|
| Who created a fund entry? | `fund_entries.source` = `MANUAL` / `GMAIL` |
| When was a charge calculated? | `transactions.created_at` for charge transactions |
| What triggered a reconciliation adjustment? | `transactions.sub_type = 'AUTO_RECONCILIATION'` + `fund_entries.reconciliation_id` |
| Was a GTT modified manually? | `gtt_records.manual_override_detected` flag |
| What happened during a sync? | `sync_event_log` with structured `details` JSON |
| When was the last backup? | `backup_history.created_at` |

**No data is ever silently modified.** Every automated action (reconciliation adjustment, GTT update, charge calculation) produces a visible record in either `transactions`, `sync_event_log`, or `persistent_alerts`.

---

## 10. Soft Delete Strategy

Physical deletion is avoided for all business-critical entities. The strategy varies by entity:

| Entity | Soft Delete Mechanism | Cleanup Policy |
|---|---|---|
| Holdings | `quantity = 0` → removed from UI; row retained for historical reference | Never physically deleted |
| GTT records | `is_archived = 1` | Never physically deleted |
| Persistent alerts | `resolved_at IS NOT NULL` | Pruned after 90 days via maintenance task |
| Sync event log | No soft delete | Pruned after 180 days (configurable) |
| Transactions | **Never deleted or soft-deleted** | Permanent append-only |
| Orders | **Never deleted** | Permanent |
| Fund entries | **Never deleted** | Permanent |
| Gmail scan cache | `status = 'IGNORED'` or `'REJECTED'` | Pruned after 90 days |
| Worker handoff | `consumed = 1` | Pruned after 7 days |

---

## 11. Critical Queries

### 11.1 Realized P&L for Date Range

```sql
-- Realized P&L for a given date range
-- Logic: sell proceeds - buy cost of sold positions - all charges for sold positions
SELECT
    COALESCE(SUM(CASE WHEN t.type = 'EQUITY_SELL' THEN t.amount_paisa ELSE 0 END), 0) 
        AS total_sell_proceeds,
    COALESCE(SUM(CASE WHEN t.type = 'EQUITY_BUY' AND o.order_type = 'SELL' THEN 0
                      WHEN t.type = 'EQUITY_BUY' THEN ABS(t.amount_paisa) ELSE 0 END), 0)
        AS total_buy_cost,
    COALESCE(SUM(CASE WHEN t.type IN ('BROKERAGE_CHARGE','STT_CHARGE','EXCHANGE_CHARGE',
                                       'GST_CHARGE','SEBI_CHARGE','STAMP_DUTY_CHARGE','DP_CHARGE')
                      THEN ABS(t.amount_paisa) ELSE 0 END), 0)
        AS total_charges
FROM transactions t
LEFT JOIN orders o ON t.reference_id = o.zerodha_order_id AND t.reference_type = 'ORDER'
WHERE t.transaction_date BETWEEN :startDate AND :endDate;

-- Realized P&L = total_sell_proceeds - total_buy_cost - total_charges
-- (Computed in Kotlin, not in SQL, for clarity and testability)
```

> **Note:** The actual P&L calculation uses the `pnl_monthly_cache` table for performance. The raw query above is the source-of-truth validation query.

### 11.2 Holdings with Computed Fields

```sql
-- Current holdings with all display fields
SELECT 
    h.id,
    h.stock_code,
    h.stock_name,
    h.quantity,
    h.invested_amount_paisa,
    h.avg_buy_price_paisa,
    h.total_buy_charges_paisa,
    h.profit_target_type,
    h.profit_target_value,
    h.target_sell_price_paisa,
    g.zerodha_gtt_id,
    g.gtt_status,
    g.trigger_price_paisa AS gtt_trigger_price,
    g.manual_override_detected
FROM holdings h
LEFT JOIN gtt_records g ON g.holding_id = h.id AND g.is_archived = 0
WHERE h.quantity > 0
ORDER BY h.stock_code;
```

### 11.3 Paginated Orders

```sql
-- Orders screen: paginated, newest first, with optional stock filter
SELECT 
    o.id,
    o.zerodha_order_id,
    o.stock_code,
    o.stock_name,
    o.order_type,
    o.quantity,
    o.price_paisa,
    o.total_value_paisa,
    o.trade_date,
    o.exchange,
    COALESCE(
        (SELECT SUM(ABS(t.amount_paisa))
         FROM transactions t
         WHERE t.reference_id = o.zerodha_order_id 
           AND t.reference_type = 'ORDER'
           AND t.type IN ('BROKERAGE_CHARGE','STT_CHARGE','EXCHANGE_CHARGE',
                          'GST_CHARGE','SEBI_CHARGE','STAMP_DUTY_CHARGE','DP_CHARGE')),
        0
    ) AS total_charges_paisa
FROM orders o
WHERE (:stockCode IS NULL OR o.stock_code = :stockCode)
  AND (:startDate IS NULL OR o.trade_date >= :startDate)
  AND (:endDate IS NULL OR o.trade_date <= :endDate)
ORDER BY o.trade_date DESC, o.id DESC
LIMIT :pageSize OFFSET :offset;
```

### 11.4 Fund Balance Computation

```sql
-- Current local fund balance
SELECT COALESCE(SUM(
    CASE 
        WHEN type = 'FUND_ADDITION' THEN amount_paisa
        WHEN type = 'FUND_WITHDRAWAL' THEN -amount_paisa
        WHEN type = 'EQUITY_BUY' THEN amount_paisa          -- Negative (outflow)
        WHEN type = 'EQUITY_SELL' THEN amount_paisa          -- Positive (inflow)
        WHEN type = 'MISC_ADJUSTMENT' THEN amount_paisa      -- Can be positive or negative
        WHEN type IN ('BROKERAGE_CHARGE','STT_CHARGE','EXCHANGE_CHARGE',
                      'GST_CHARGE','SEBI_CHARGE','STAMP_DUTY_CHARGE','DP_CHARGE')
             THEN amount_paisa                                -- Negative (outflow)
        ELSE 0
    END
), 0) AS current_balance_paisa
FROM transactions;
```

### 11.5 Duplicate Order Detection (CSV Import)

```sql
-- Check if an order already exists before CSV import
SELECT EXISTS(
    SELECT 1 FROM orders WHERE zerodha_order_id = :orderId
) AS already_exists;
```

### 11.6 Active Alerts Query

```sql
-- Unresolved alerts for UI rendering
SELECT id, alert_type, severity, payload, created_at
FROM persistent_alerts
WHERE acknowledged = 0
  AND resolved_at IS NULL
ORDER BY 
    CASE severity 
        WHEN 'CRITICAL' THEN 1 
        WHEN 'WARNING' THEN 2 
        WHEN 'INFO' THEN 3 
    END,
    created_at DESC;
```

### 11.7 Monthly P&L for Charts

```sql
-- Monthly P&L data for line/bar chart on Portfolio screen
SELECT 
    year_month,
    realized_pnl_paisa,
    total_sell_charges_paisa + total_buy_charges_paisa AS total_charges_paisa,
    invested_value_paisa,
    order_count
FROM pnl_monthly_cache
WHERE year_month BETWEEN :startMonth AND :endMonth
ORDER BY year_month;
```

---

## 12. Room Database Class

```kotlin
@Database(
    entities = [
        AccountBinding::class,
        Order::class,
        Holding::class,
        OrderHoldingsLink::class,
        Transaction::class,
        FundEntry::class,
        ChargeRate::class,
        GttRecord::class,
        PersistentAlert::class,
        SyncEventLog::class,
        PnlMonthlyCache::class,
        GmailScanCache::class,
        GmailFilter::class,
        BackupHistory::class,
        WorkerHandoff::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KiteWatchDatabase : RoomDatabase() {
    abstract fun accountBindingDao(): AccountBindingDao
    abstract fun orderDao(): OrderDao
    abstract fun holdingDao(): HoldingDao
    abstract fun transactionDao(): TransactionDao
    abstract fun fundEntryDao(): FundEntryDao
    abstract fun chargeRateDao(): ChargeRateDao
    abstract fun gttRecordDao(): GttRecordDao
    abstract fun alertDao(): PersistentAlertDao
    abstract fun syncEventLogDao(): SyncEventLogDao
    abstract fun pnlCacheDao(): PnlMonthlyCacheDao
    abstract fun gmailScanCacheDao(): GmailScanCacheDao
    abstract fun gmailFilterDao(): GmailFilterDao
    abstract fun backupHistoryDao(): BackupHistoryDao
    abstract fun workerHandoffDao(): WorkerHandoffDao
}
```

---

## 13. Type Converters

```kotlin
object Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): String? = value?.toString()

    @TypeConverter
    fun toInstant(value: String?): Instant? = value?.let { Instant.parse(it) }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): Long? = value?.multiply(BigDecimal(100))?.toLong()

    @TypeConverter
    fun toBigDecimal(value: Long?): BigDecimal? = value?.let { BigDecimal(it).divide(BigDecimal(100)) }
}
```

> **Note:** The `BigDecimal` converter is available but should be used sparingly. The preferred pattern is to store as `Long` (paisa) and convert at the domain/display layer.

---

## 14. DAO Interface Patterns

### 14.1 TransactionDao (Append-Only)

```kotlin
@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    // NO @Update — transactions are immutable
    // NO @Delete — transactions are never deleted

    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE type IN (:types) ORDER BY transaction_date DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getPageByTypes(types: List<String>, limit: Int, offset: Int): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions 
        WHERE transaction_date BETWEEN :startDate AND :endDate 
        ORDER BY transaction_date DESC, id DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPageByDateRange(startDate: String, endDate: String, limit: Int, offset: Int): List<TransactionEntity>
}
```

### 14.2 OrderDao (Insert-Only, Read-Heavy)

```kotlin
@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(orders: List<OrderEntity>): List<Long>
    // Returns -1 for rows that were ignored (duplicate zerodha_order_id)

    @Query("SELECT * FROM orders WHERE zerodha_order_id = :orderId")
    suspend fun getByZerodhaId(orderId: String): OrderEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM orders WHERE zerodha_order_id = :orderId)")
    suspend fun exists(orderId: String): Boolean

    @RawQuery(observedEntities = [OrderEntity::class])
    fun getPagedOrders(query: SupportSQLiteQuery): PagingSource<Int, OrderEntity>
}
```

---

## 15. Storage Footprint Estimation

| Table | Row Size (est.) | 3-Year Rows | Total Size |
|---|---|---|---|
| orders | ~300 bytes | 3,750 | ~1.1 MB |
| transactions | ~250 bytes | 11,250 | ~2.8 MB |
| holdings | ~200 bytes | 50 | ~10 KB |
| fund_entries | ~150 bytes | 180 | ~27 KB |
| charge_rates | ~100 bytes | 100 | ~10 KB |
| gtt_records | ~200 bytes | 100 | ~20 KB |
| sync_event_log | ~300 bytes | 2,250 | ~675 KB |
| pnl_monthly_cache | ~150 bytes | 36 | ~5.4 KB |
| gmail_scan_cache | ~200 bytes | 500 | ~100 KB |
| **Total estimated** | | | **~5 MB** |

With SQLCipher overhead (~10%) and indexes, total DB size after 3 years of active use: **~6–8 MB**. Well within acceptable limits for on-device storage.

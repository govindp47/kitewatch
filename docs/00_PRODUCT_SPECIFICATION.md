# KiteWatch — Product Specification (PRD)

**Version:** 1.0  
**Product Category:** Android Mobile App — Personal Finance & Portfolio Management  
**Status:** Draft

---

## Table of Contents

1. [Product Overview](#1-product-overview)
2. [Problem Definition](#2-problem-definition)
3. [Product Goals](#3-product-goals)
4. [Target Users & Personas](#4-target-users--personas)
5. [Product Scope](#5-product-scope)
6. [Core Product Features](#6-core-product-features)
7. [UX Architecture](#7-ux-architecture)
8. [Detailed User Flows](#8-detailed-user-flows)
9. [UI / UX Specifications](#9-ui--ux-specifications)
10. [Functional Requirements](#10-functional-requirements)
11. [Non-Functional Product Requirements](#11-non-functional-product-requirements)
12. [External Integrations (Product Perspective)](#12-external-integrations-product-perspective)
13. [Edge Cases & Error Scenarios](#13-edge-cases--error-scenarios)
14. [Privacy & User Data Considerations](#14-privacy--user-data-considerations)
15. [Development Phases (Product Roadmap)](#15-development-phases-product-roadmap)
16. [Future Enhancements](#16-future-enhancements)
17. [Open Questions / Assumptions](#17-open-questions--assumptions)

---

## 1. Product Overview

### Product Summary

KiteWatch is an Android-exclusive, local-first portfolio management utility built for Zerodha (Kite) retail investors and traders. It operates entirely on-device with no external servers, integrating with the Zerodha Kite Connect API (free tier), Gmail, and the user's own Google Drive. It tracks fund balances, fetches and locally processes executed orders, calculates precise transaction charges, visualizes realized P&L, and automates GTT (Good Till Triggered) sell order management.

### Value Proposition

- Tracks true realized profit and loss inclusive of all brokerage and transaction charges, calculated locally without relying on broker-provided summaries.
- Automates GTT profit-target order creation and updates so users never miss placing a sell target after a buy.
- Eliminates the need to upload sensitive financial data to any third-party cloud service.
- Provides fund balance reconciliation by cross-referencing local records with live Zerodha balances, with optional Gmail-based deposit/withdrawal detection.

### Target Users

Zerodha retail investors and traders who:

- Prioritize strict data privacy and refuse to share financial data with third-party services.
- Require accurate, charge-adjusted realized P&L rather than broker-approximated summaries.
- Hold equity delivery positions and use GTT orders for profit-target exits.
- Are comfortable sideloading an Android APK and connecting it to Zerodha's developer API.

---

## 2. Problem Definition

### Current User Pain Points

- Zerodha's native Kite app does not provide automated GTT placement tied to configurable profit targets after each buy.
- Realized P&L in broker apps does not deduct actual brokerage and transaction charges, producing inaccurate profit figures.
- Third-party portfolio trackers (Smallcase, Tickertape, etc.) require users to upload or sync sensitive trade data to external servers.
- Fund balance tracking relies entirely on manual effort; there is no lightweight way to cross-reference current Zerodha fund balances against a personal ledger without exporting data.
- Historical transaction charges are difficult to aggregate manually from contract notes.

### Why Solving This Problem Matters

Retail investors making hold-and-exit decisions based on inaccurate P&L figures risk misjudging actual profitability. Charge leakage across many trades is non-trivial. For privacy-conscious users, no existing product satisfies both the accuracy requirement and the local-only data residency requirement simultaneously.

---

## 3. Product Goals

### Primary Goals

- Provide a locally-calculated, charge-accurate realized P&L view for all completed trades.
- Automate GTT profit-target order placement and updates triggered by executed buy orders.
- Reconcile local fund balance records with live Zerodha fund balances, surfacing discrepancies clearly.

### Secondary Goals

- Enable optional Gmail-based detection of fund deposits/withdrawals to reduce manual entry.
- Provide Google Drive backup as a user-controlled, private data safety mechanism.
- Deliver a complete historical view of orders, transactions, and holdings without dependence on broker history retention.

### Success Metrics (Product-Level KPIs)

| Metric | Target |
|---|---|
| Fund balance reconciliation accuracy | Local balance matches Zerodha balance within configurable tolerance after every reconciliation cycle |
| GTT auto-placement success rate | GTT created or updated within one sync cycle after a confirmed buy order |
| Charge calculation accuracy | Calculated charges match Zerodha contract note charges within defined rounding tolerance |
| Holdings verification pass rate | No mismatch between local holdings and Zerodha holdings after order sync |
| User-reported data discrepancy incidents | Zero silent mismatches; all discrepancies surface as visible alerts |

---

## 4. Target Users & Personas

### Persona 1 — The Privacy-First Equity Investor

**Description:** A salaried professional who invests in equity delivery positions for medium-to-long-term holds. Has a Zerodha account and developer API access. Uncomfortable sharing trade history with any third-party app.

**Motivations:**

- Wants to know the exact rupee profit after all charges before deciding to exit a position.
- Wants peace of mind that no financial data leaves their device.

**Problems They Face:**

- Manually calculating charge-adjusted P&L per stock is tedious.
- Forgets to place GTT sell orders after buying, missing planned exit targets.
- Has no quick way to verify whether Zerodha's reported fund balance matches their personal records.

**Typical Usage Scenarios:**

- Opens the app in the evening to review the day's portfolio status.
- Checks the Holdings screen to see if any stock is approaching the configured profit target.
- Reviews the Portfolio screen monthly to confirm realized P&L against expectations.

---

### Persona 2 — The Active Delivery Trader

**Description:** A more active retail trader who buys and sells multiple stocks per week using delivery orders. Tracks charges as a meaningful cost center.

**Motivations:**

- Understands that charges erode profits significantly across many trades.
- Wants automated exit management to focus on buying decisions rather than monitoring sell targets.

**Problems They Face:**

- Managing GTT orders manually across many positions is error-prone and time-consuming.
- Broker apps do not aggregate charge costs in a single, filterable view.

**Typical Usage Scenarios:**

- Uses the Transactions screen to review monthly charge totals.
- Relies on the automated 4 PM order sync to ensure GTT orders are placed or updated without manual intervention.
- Imports historical order CSVs on first setup to establish a complete P&L baseline.

---

### Persona 3 — The Technically Comfortable Setup User (Onboarding Persona)

**Description:** A user who is willing to sideload an APK and configure Kite Connect API credentials. May need guidance through the initial setup but is not a developer.

**Motivations:**

- Wants a one-time setup that then runs reliably in the background.
- Values the in-app guidebook heavily because there is no Play Store listing.

**Problems They Face:**

- Initial configuration (API keys, Gmail permissions, Google Drive setup) can be confusing without a structured onboarding flow.
- Importing historical CSV data requires understanding the app's expected format.

**Typical Usage Scenarios:**

- Follows the onboarding checklist step by step on first install.
- References the in-app Full Guidebook whenever they encounter a new feature.
- Performs a manual backup to Google Drive after completing initial data import.

---

## 5. Product Scope

### In Scope

- Zerodha Kite Connect API integration (free tier only) for: authentication, fund balance, executed orders, holdings, GTT orders, and transaction charge rates.
- Local-only data storage. All financial data resides on-device.
- Fund balance tracking with manual entry, Gmail-based optional detection, and reconciliation against live Zerodha balances.
- Local charge calculation for buy and sell orders using stored Zerodha charge rates.
- Executed orders history with local storage, pagination, filtering, and CSV import for historical data.
- Current holdings display with profit target configuration per stock.
- Automated GTT single-trigger sell order creation and modification based on profit targets.
- Portfolio analytics screen with realized P&L, charge breakdowns, and date-range filtering.
- Transactions screen showing all financial activities with filtering.
- Background task scheduling (Mon–Fri) for order syncs, fund reconciliation, and charge rate refresh.
- Google Drive backup (user-owned account), local backup, and Excel export.
- Biometric/PIN app lock.
- Dark and Light theme support.
- Gmail API integration for optional fund transaction detection via user-defined filters.
- In-app About, Full Guidebook, Privacy & Security, and Terms & Conditions pages.
- Single Zerodha account binding per device installation.

### Out of Scope (Non-Goals)

- Google Play Store publication.
- iOS or web versions.
- Real-time market data (live price streaming, websocket ticks).
- Manual buy/sell order placement from within the app.
- Intraday trade tracking.
- Multi-broker support (any broker other than Zerodha).
- External server, cloud database, or any third-party backend.
- Options, futures, or derivatives tracking.
- Tax computation or tax filing assistance.
- Price alerts or push notifications for price movements.

---

## 6. Core Product Features

---

### Feature 1 — Zerodha Account Binding & Authentication

**Description:** The app authenticates the user with Zerodha via the Kite Connect API during onboarding and permanently binds to that account for the lifetime of the installation.

**User Value:** Ensures all local data is unambiguously tied to one trading account, preventing accidental data mixing.

**Functional Behavior:**

- On first launch, the user is directed to log in via Zerodha's OAuth/API login flow.
- Upon successful authentication, the app stores the account identifier locally and marks the device as bound to that account.
- All subsequent API calls use the stored credentials for that account.
- The user cannot log in with a different Zerodha account without uninstalling and reinstalling the app.
- The local database is keyed to the bound account.

**Feature Rules and Constraints:**

- One account per installation, enforced permanently.
- No account switching UI exists anywhere in the app.
- Backups and CSV imports are validated against the bound account identifier; mismatched files are rejected.

**Edge Cases:**

- If authentication fails during onboarding (wrong credentials, API error), the user is shown an error and allowed to retry. The account is not bound until authentication succeeds.
- If the Kite Connect API session expires, the user is prompted to re-authenticate. The app does not auto-submit credentials.
- If the user attempts to restore a backup from a different Zerodha account, the restore is blocked with an explanation.

---

### Feature 2 — Fund Balance Management

**Description:** Tracks the user's available Zerodha fund balance locally through a combination of manual entries, optional Gmail-detected transactions, and periodic reconciliation against the live Zerodha balance.

**User Value:** Provides an accurate, private ledger of fund movements without relying on the broker's transaction history.

**Functional Behavior:**

**Manual Entry:**

- User can add a fund addition or withdrawal entry specifying amount, date, and optional note.
- Entry is immediately saved to the local transaction log.

**Gmail Detection (Optional):**

- User grants Gmail read access and defines one or more filters (e.g., sender address, subject keyword) to narrow which emails are scanned.
- The app scans only emails matching user-defined filters.
- Detected transactions are presented to the user for review before being added to the transaction log.
- User must explicitly confirm each detected transaction. Confirmed entries are logged; rejected entries are discarded.

**Reconciliation:**

- A reconciliation check fetches the live Zerodha fund balance via API and compares it to the locally tracked balance.
- If the difference is within the user-configurable tolerance (default: ₹50), the discrepancy is automatically logged as a miscellaneous credit or charge entry without user action.
- If the difference exceeds the tolerance, a prominent alert is shown with the discrepancy amount and both balance values. No automatic adjustment is made.
- Reconciliation triggers: (a) after any manual fund entry is saved, (b) once daily Mon–Fri at a user-configurable time, (c) manually via a Refresh button.

**Feature Rules and Constraints:**

- Fund balance shown in the app reflects the locally tracked balance, not a live API value (except during reconciliation).
- Gmail scanning scope is strictly limited to emails matching user-defined filters.
- The tolerance threshold is configurable in Settings with a default of ₹50.
- Automatic adjustments for within-tolerance mismatches are always logged; they are never silent.

**Edge Cases:**

- If Gmail access is not granted, the optional detection feature is unavailable. Manual entry is always available as a fallback.
- If the reconciliation API call fails, the app shows a warning and schedules a retry. The local balance is not modified.
- If a Gmail-detected amount matches an already-logged manual entry (same amount, same approximate date), the app flags a potential duplicate for user review before adding.
- If the user has no fund entries yet, reconciliation compares the live balance against ₹0 and alerts accordingly.

---

### Feature 3 — Charge Rate Tracking

**Description:** Fetches and stores the latest Zerodha transaction charge rates locally for use in all charge calculations within the app.

**User Value:** Ensures that locally calculated brokerage and transaction charges reflect current Zerodha rates, producing accurate P&L figures.

**Functional Behavior:**

- On first setup, charge rates are fetched from the Zerodha API.
- Charge rates are stored locally and used for all buy/sell charge calculations.
- Automatic refresh occurs every 15 days (default, configurable in Settings).
- User can manually trigger a charge rate refresh from Settings at any time.
- The date of the last successful charge rate fetch is displayed in Settings.

**Feature Rules and Constraints:**

- All charge calculations in Holdings, Transactions, and Portfolio screens use the locally stored charge rates, not live API rates per calculation.
- The refresh interval is configurable but has a minimum of 1 day to avoid unnecessary API usage.

**Edge Cases:**

- If a charge rate refresh fails, the app continues using the last successfully fetched rates and shows a warning indicating the rates may be outdated, along with the date of the last successful fetch.
- If charge rates have never been fetched (e.g., fresh install with no network access at setup), charge-dependent calculations display a placeholder and prompt the user to complete charge rate setup.

---

### Feature 4 — Executed Orders Management

**Description:** Fetches, stores, and displays historical buy and sell equity delivery orders. Verifies local holdings against Zerodha holdings when new orders are detected.

**User Value:** Provides a complete, locally-owned order history and forms the data foundation for all P&L and charge calculations.

**Functional Behavior:**

**Fetching:**

- Automatic fetch occurs Mon–Fri at 4 PM (default, configurable).
- Manual refresh available via a button on the Orders screen.
- On first-time setup, only today's executed orders are fetched automatically. Historical data is not back-fetched automatically.

**Historical Import:**

- User can import historical orders via a CSV file from local device storage.
- The CSV must match the app-defined format (documented in the in-app Guidebook).
- Similarly, historical fund additions/withdrawals and adjustments can be imported via formatted CSV.

**Verification Flow (on new orders detected):**

1. App fetches current Zerodha holdings.
2. App compares fetched holdings quantities against locally stored holdings quantities.
3. If quantities match: local holdings are updated, order records are added, transaction entries are generated, and buy/sell charges are calculated locally.
4. If quantities mismatch: a detailed error alert is shown specifying which stock(s) differ and by how much. All updates are halted until the user resolves or acknowledges the issue.

**Display:**

- Orders are listed newest to oldest.
- Pagination loads 50 orders per page; older orders load on scroll.
- Filtering by: stock code, current month, last 3 months, custom date range.

**Feature Rules and Constraints:**

- Holdings verification only occurs when new orders are fetched, not as a standalone periodic check.
- No automatic back-fill of historical orders from the Zerodha API.
- CSV import merges with existing data; duplicate detection prevents the same order from being imported twice (matched by order ID or equivalent unique identifier).

**Edge Cases:**

- If the 4 PM scheduled fetch finds no new orders, no holdings verification is triggered and no UI change occurs.
- If the CSV import file is malformed or does not match the defined format, the import is rejected entirely with an error describing the format issue. No partial imports.
- If a partial sell is detected in new orders, the holdings verification flow handles the updated quantity, and the GTT update is triggered downstream.
- If the API returns orders but the holdings fetch subsequently fails, the entire sync is rolled back and an alert is shown.

---

### Feature 5 — Current Holdings Display

**Description:** A read-only view of all currently held equity positions, enriched with locally calculated cost data and configurable profit targets.

**User Value:** Gives users a clear, charge-inclusive picture of each holding's true cost basis and how far the current position is from its profit target.

**Functional Behavior:**

**Display Per Holding:**

- Stock symbol and name.
- Quantity held.
- Invested amount (sum of buy order values contributing to current holdings).
- Calculated buy charges (derived from stored charge rates applied to buy orders).
- Profit target (configurable per stock; default 5% of invested amount).
- Target sell price (calculated as: (invested amount + profit target value + estimated sell charges) / quantity held, using the average buy price for multi-purchase holdings).
- Projected P&L at target: expected profit minus total buy charges minus projected sell charges at target price.

**Profit Target Configuration:**

- User can edit the profit target for each stock via:
  - Percentage of invested amount (e.g., 5%).
  - Exact rupee profit amount (e.g., ₹500).
- The profit target value is fixed per stock. If the stock is purchased again at a different price, the profit target value stays fixed but the target sell price is recalculated using the new average buy price.
- Changing the profit target triggers an automatic GTT update for that stock in the background.

**Feature Rules and Constraints:**

- Holdings data is read-only (no direct editing of quantities or costs). Data is updated only through order sync.
- Default profit target on new holdings: 5% of invested amount.
- Target sell price calculation always uses average buy price when multiple purchase lots exist for the same stock.

**Edge Cases:**

- If a holding has zero remaining quantity (fully sold), it is removed from the Holdings screen after the next successful order sync and holdings verification.
- If charge rates are not yet fetched, buy charge and projected sell charge fields display a placeholder with a prompt to refresh charge rates.
- If the user sets a profit target of 0% or ₹0, the app accepts this value and calculates a target sell price equal to the break-even sell price (covering buy charges and projected sell charges only). A soft warning is shown.

---

### Feature 6 — GTT Order Automation

**Description:** Automatically creates and updates single-trigger GTT sell orders in Zerodha based on each holding's configured profit target, triggered by order sync events.

**User Value:** Eliminates the need for users to manually place or adjust GTT sell orders after every buy, ensuring exit targets are always active.

**Functional Behavior:**

**Trigger:** GTT logic runs after every successful order sync that detects new buy orders.

**GTT Creation:**

- If a new holding (or addition to an existing holding) is detected and no GTT exists for that stock: a new single-trigger GTT sell order is created at the calculated target sell price for the full holding quantity.

**GTT Update:**

- If a GTT already exists for a stock: the GTT is updated to reflect the current target sell price and current holding quantity.
- If the user partially sells a holding: the GTT quantity is updated to match the remaining quantity.

**Manual Override Respect:**

- If the user manually modified a GTT directly in Zerodha (changed price or quantity outside of KiteWatch), the app detects this during the next GTT fetch.
- The app does not blindly overwrite manual Zerodha GTT changes. Detected manual overrides are surfaced to the user with options to either keep the Zerodha value or revert to the app-calculated value.

**Verification:**

- After every GTT create or update action, the app fetches the GTT list from Zerodha.
- If the fetched GTT matches the expected state: local GTT records are updated.
- If the fetched GTT does not match: an error alert is shown. Local records are not updated.

**Display:**

- Dedicated GTT screen showing all current GTT orders (read from local database).
- Displays: stock symbol, GTT trigger price, GTT quantity, GTT status (as reported by Zerodha), and whether the value matches the app-calculated target.

**Feature Rules and Constraints:**

- Only single-trigger GTT orders (profit-target sell only) are managed by the app.
- GTT orders for stocks not in current local holdings are displayed as informational but not modified.
- The app never places buy GTT orders.
- GTT automation operates strictly on delivery equity holdings.

**Edge Cases:**

- If GTT placement fails due to an API error or rate limit: the app shows an error alert and retries at the next scheduled sync.
- If a holding is fully sold and the GTT has already been triggered (i.e., GTT is in a triggered/completed state): no update is attempted; the GTT record is archived locally.
- If the holding quantity is 0 but the GTT still shows as active (e.g., a sell order is placed but not yet confirmed): the app flags this state and does not modify the GTT until the next order sync confirms the final state.
- If Zerodha's GTT API is unavailable during sync: the order sync proceeds, but GTT creation/update is deferred and flagged as pending.

---

### Feature 7 — Portfolio & Analytics Screen

**Description:** The home screen of the app, presenting high-level performance metrics and visualizations for the user's realized P&L, charges, and trading activity over configurable date ranges.

**User Value:** Gives users a single, accurate summary of true trading profitability after all charges.

**Functional Behavior:**

**Metrics Displayed:**

- Realized P&L (calculation below).
- Total invested buy value (for the selected period).
- P&L percentage.
- Total charges (brokerage + transaction charges for the period).
- Charges as a percentage of invested value.

**Realized P&L Calculation:**

```
Realized P&L =
  (Sum of all sell order values in period)
  - (Sum of all buy order values for sold positions in period)
  - (Sum of all logged buy charges for sold positions in period)
  - (Sum of all logged sell charges for sell orders in period)
  + (Invested buy value of current holdings still held at end of period)
  + (Buy charges for current holdings)
```

*Net result: the actual realized profit/loss after all charges, with current holdings at cost basis so they do not inflate or deflate the realized figure.*

**Date Range Filters:**

- Current month
- Last 3 months
- Last 6 months
- Current year
- Overall (all time)
- Custom date range (date picker)

**Visualizations:**

- Pie chart: P&L vs. total charges breakdown.
- Line graph: cumulative P&L over the selected date range.
- Monthly P&L percentage bar/line graph (showing profitability per month).
- Charges breakdown chart (brokerage vs. other transaction charges).

**Empty State:**

- If no orders or holdings exist, the screen shows a setup checklist guiding the user to: complete charge rate setup, import or sync orders, configure profit targets, and set up backup.

**Feature Rules and Constraints:**

- All figures are derived entirely from local database records; no live price data is used.
- Unrealized P&L (open positions valued at market price) is not shown, as the app does not fetch live prices.

**Edge Cases:**

- If date range filtering returns no data: each metric displays ₹0 / 0% and charts show empty states with a label.
- If charge rates were never fetched, charge figures display as unavailable with a prompt.

---

### Feature 8 — Transactions Screen

**Description:** A chronological log of all financial activities recorded in the app.

**User Value:** Provides a complete, filterable audit trail of every financial event recorded locally.

**Functional Behavior:**

**Transaction Types Displayed:**

- Fund additions
- Fund withdrawals
- Equity buy values (per order)
- Equity sell values (per order)
- Brokerage charges (per order)
- Other transaction charges (per order)
- Miscellaneous adjustments (auto-logged from fund reconciliation)

**Display:**

- Chronological order, newest first.
- Pagination: 50 transactions per load, older entries load on scroll.

**Filtering Options:**

- Charges only
- Fund additions and withdrawals only
- Current month
- Last 3 months
- Custom date range

**Feature Rules and Constraints:**

- Transactions are read-only; no editing or deletion from this screen.
- All transaction types are generated by the app based on user actions or automated syncs, not entered free-form (except fund manual entries).

**Edge Cases:**

- If a filter returns no results, the list shows an empty state with the active filter label.

---

### Feature 9 — Background Scheduling

**Description:** Allows users to configure automated background task schedules for order sync, fund reconciliation, and charge rate refresh.

**User Value:** Ensures the app stays up to date automatically without requiring daily manual interaction.

**Functional Behavior:**

- Users can set one or more specific times per day for automated tasks.
- Each scheduled task can be individually enabled or disabled.
- All automated schedules are restricted to Monday–Friday. Weekend execution is not configurable or available.
- Default schedules:
  - Executed orders fetch: 4 PM.
  - Fund reconciliation: once daily at a user-configurable time.
  - Charge rate refresh: every 15 days.

**Feature Rules and Constraints:**

- Weekend restriction is hardcoded and cannot be overridden by the user.
- If a scheduled task fails (e.g., API rate limit), it retries automatically at the next available scheduled time and shows a non-blocking warning notification if notifications are enabled.

**Edge Cases:**

- If the device is off or in battery saver mode at the scheduled time, the task runs at the next available opportunity when the device is active.
- If multiple tasks are scheduled at the same time, they are queued and executed sequentially.

---

### Feature 10 — Backup, Restore & Export

**Description:** Enables users to back up their local database to Google Drive or local storage, restore from backups, and export data to Excel.

**User Value:** Protects against data loss from device issues and provides a portable data format for personal record-keeping.

**Functional Behavior:**

**Backup:**

- Manual backup: user triggers from Settings. Destination: Google Drive (same Gmail account used for Zerodha/Gmail integration) or local device storage.
- Scheduled backup: user can configure an optional recurring backup schedule.
- Backup files are bound to the Zerodha account identifier.

**Restore:**

- Sources: Google Drive backup file, local backup file, or formatted Excel file.
- Restore behavior: merges imported data with existing local database. Existing records are never modified or deleted. Only new records (not already present) are added.
- Backup files from a different Zerodha account are rejected before any data is processed.

**Export:**

- User can export data to Excel format covering: orders, transactions, and holdings.
- Export is available for all data or for a selected date range.

**Feature Rules and Constraints:**

- Google Drive backup requires the Gmail/Google account linked to the device and used for optional Gmail fund detection. The same account is used to avoid requiring the user to authorize a second Google account.
- Backup files are not human-readable raw database dumps; they are structured files the app can parse on restore.
- The merge-only restore behavior means the app cannot be used to "undo" manually entered transactions via restore.

**Edge Cases:**

- If Google Drive upload fails (no connectivity, revoked permission), the user is shown an error and the backup is saved locally as a fallback, with a notification.
- If a local backup file is corrupted, the restore is aborted with an error. Existing data is unaffected.
- If an Excel restore file contains rows with formatting errors, valid rows are imported and invalid rows are listed in a post-import summary for user review.

---

### Feature 11 — Security & App Lock

**Description:** Requires biometric or PIN/passcode authentication before the app can be accessed.

**User Value:** Prevents unauthorized access to sensitive financial data stored on the device.

**Functional Behavior:**

- Biometric authentication (fingerprint or face unlock) is the primary lock method.
- If biometrics are unavailable or fail: falls back to device PIN or passcode.
- Authentication is required every time the app is opened or returned to from background after a configurable timeout.
- Biometric lock is set up during onboarding and cannot be disabled.

**Feature Rules and Constraints:**

- Biometric lock is mandatory; it cannot be turned off in Settings.
- If the device has no biometrics and no PIN set, the user is prompted to set a device PIN before the app can be used.

**Edge Cases:**

- If biometric hardware fails mid-session (unlikely but possible on some devices), the user is prompted for PIN/passcode fallback.
- If the user fails authentication multiple times, the app follows the device's standard lockout behavior.

---

### Feature 12 — In-App Content Pages

**Description:** Static informational pages included within the app covering guidance, legal, and privacy information.

**User Value:** Critical for sideloaded apps where no Play Store listing exists to communicate features, privacy practices, and terms.

**Pages Included:**

- **About:** App description, version, and Zerodha API attribution.
- **Full Guidebook / How-to:** Step-by-step documentation for every major feature, CSV format specifications, and setup instructions.
- **Privacy & Security:** Plain-language explanation of what data is stored, where it is stored, and what permissions are used and why.
- **Terms & Conditions:** Usage terms for the app.

**Feature Rules and Constraints:**

- All pages are bundled within the app. No external URLs are loaded for these pages.
- T&C and Privacy Policy must be accepted by the user during onboarding before any other setup step proceeds.

---

## 7. UX Architecture

### Screen Inventory

| Screen | Purpose |
|---|---|
| Onboarding — Welcome & T&C | First launch; accept terms before any setup |
| Onboarding — Biometric Setup | Configure device authentication |
| Onboarding — Zerodha Login | API authentication and account binding |
| Onboarding — Gmail Access | Optional Gmail permission grant |
| Onboarding — Google Drive Setup | Optional backup configuration |
| Onboarding — Setup Complete | Confirmation and entry to main app |
| Portfolio (Home) | Realized P&L dashboard with charts and filters |
| Holdings | List of current equity holdings with profit targets |
| Holdings — Stock Detail / Edit Target | Expandable/detail view to edit profit target for a stock |
| Orders | Historical executed orders list |
| Transactions | Full financial activity log |
| GTT Orders | Read-only list of current GTT orders |
| Settings | All configuration options |
| Settings — Schedule Configuration | Background task timing setup |
| Settings — Fund Tolerance | Reconciliation tolerance configuration |
| Settings — Charge Rates | View/refresh charge rates |
| Settings — Backup & Restore | Backup, restore, export controls |
| Settings — Notifications | Enable/disable notifications |
| Settings — Theme | Dark/Light toggle |
| About | App info and version |
| Full Guidebook | In-app documentation |
| Privacy & Security | Privacy information page |
| Terms & Conditions | Legal terms page |
| Error / Alert Screens | Inline banners and modal dialogs for mismatch states |

### Navigation Structure

```
App Launch
  └── Biometric / PIN Auth
        └── Bottom Navigation Bar
              ├── Portfolio (Home Tab)
              │     └── Date Range Filter Sheet
              ├── Holdings Tab
              │     └── Stock Detail / Edit Profit Target
              ├── Orders Tab
              │     └── Filter Sheet
              ├── Transactions Tab
              │     └── Filter Sheet
              └── Settings Tab
                    ├── Schedule Configuration
                    ├── Fund Tolerance
                    ├── Charge Rates
                    ├── Backup & Restore
                    ├── Notifications
                    ├── Theme
                    ├── About
                    ├── Full Guidebook
                    ├── Privacy & Security
                    └── Terms & Conditions

GTT Orders Screen
  └── Accessible from Holdings Tab or Settings (not a primary bottom nav tab)
       [Note: GTT screen placement in nav to be confirmed — see Open Questions]
```

---

## 8. Detailed User Flows

### Flow 1 — Onboarding (First Install)

1. User installs APK (sideloaded).
2. App launches to Welcome screen.
3. User reads and accepts Terms & Conditions and Privacy Policy. Cannot proceed without acceptance.
4. App prompts biometric setup. User enrolls fingerprint/face or confirms device PIN as fallback.
5. App presents Zerodha Login screen. User completes Kite Connect OAuth/API authentication.
6. On success, account is bound. App displays confirmation.
7. App presents optional Gmail access screen. User can grant Gmail read permission or skip.
8. If Gmail granted, user is prompted to define at least one email filter (sender/subject) for fund detection.
9. App presents Google Drive setup screen. User can connect their Google account for backup or skip.
10. App displays Setup Complete screen with a summary of what was configured and what was skipped.
11. User is taken to the Portfolio home screen. If no data exists, the setup checklist is shown.

---

### Flow 2 — Daily Order Sync (Automated, 4 PM)

1. Background task triggers at scheduled time (Mon–Fri only).
2. App fetches executed orders from Zerodha API for today.
3. If no new orders: task completes silently. No UI change.
4. If new orders found:
   a. App fetches current Zerodha holdings.
   b. App compares fetched holdings quantities against local holdings.
   c. If mismatch: error alert is shown with details. Sync halted.
   d. If match: local holdings updated, order records added, transaction entries generated, charges calculated locally.
5. GTT automation triggered:
   a. App fetches current GTT list from Zerodha.
   b. For each affected holding: app creates new GTT or updates existing GTT based on profit target.
   c. Verification fetch confirms GTT state. If mismatch: error alert shown.
6. Fund reconciliation triggered:
   a. App fetches live Zerodha fund balance.
   b. Comparison against local balance.
   c. Within tolerance: auto-log miscellaneous adjustment. Outside tolerance: alert shown.
7. If notifications enabled, a silent or summary notification is shown on completion.

---

### Flow 3 — Edit Profit Target for a Stock

1. User opens Holdings tab.
2. User taps on a stock's card to expand or open detail view.
3. User sees current profit target (% and ₹ amount) and target sell price.
4. User taps Edit Target.
5. User selects input mode: percentage or exact rupee amount.
6. User enters new value. App shows live preview of updated target sell price.
7. User confirms. App saves new target.
8. App triggers background GTT update for that stock.
9. GTT update confirmation or error is shown via a non-blocking banner.

---

### Flow 4 — Fund Manual Entry

1. User opens Transactions tab or Fund section.
2. User taps Add Fund Entry.
3. User selects type: Addition or Withdrawal.
4. User enters amount, date, and optional note.
5. User confirms. Entry is saved to local transaction log.
6. Fund reconciliation check triggers automatically.
7. If reconciliation finds a mismatch within tolerance: auto-adjustment logged, user notified via banner.
8. If mismatch exceeds tolerance: alert shown with discrepancy details.

---

### Flow 5 — Historical Data Import (CSV)

1. User opens Settings → Backup & Restore → Import CSV.
2. User selects import type: Orders, Fund Transactions, or Adjustments.
3. User is shown the expected CSV format (or directed to Guidebook).
4. User selects CSV file from device storage.
5. App validates file format and Zerodha account match.
6. If validation fails: error shown, no data imported.
7. If validation passes: app shows preview of rows to be imported (new only, duplicates excluded).
8. User confirms import.
9. Data is merged into local database. A summary of rows added and duplicates skipped is shown.

---

### Flow 6 — Backup to Google Drive

1. User opens Settings → Backup & Restore.
2. User taps Back Up Now → selects Google Drive.
3. App creates a backup file and uploads to the connected Google Drive account.
4. Success confirmation shown with timestamp.
5. If upload fails: error shown, option to save to local storage instead.

---

### Flow 7 — Error State: Fund Mismatch Alert

1. Reconciliation detects a balance discrepancy exceeding the tolerance.
2. App shows a prominent color-coded alert banner on the Portfolio screen (and/or as a notification if enabled).
3. Alert shows: local balance, Zerodha balance, and the discrepancy amount.
4. User can dismiss the alert (it re-appears at the next reconciliation cycle if unresolved) or navigate to Transactions to investigate.
5. User may manually add a fund adjustment entry to resolve the discrepancy.
6. Next reconciliation confirms resolution and clears the alert.

---

### Flow 8 — Empty State (First Use, No Data)

1. User completes onboarding and lands on Portfolio screen.
2. Screen shows a Setup Checklist with the following items and their completion status:
   - ☐ Charge rates fetched
   - ☐ First order sync completed (or CSV imported)
   - ☐ Profit targets reviewed
   - ☐ Backup configured
3. Each checklist item is tappable and navigates to the relevant screen or action.
4. Checklist dismisses automatically once all items are completed.

---

## 9. UI / UX Specifications

### General Design Principles

- Modern financial app aesthetic.
- Full Dark and Light mode support; respects system default with manual override in Settings.
- Bottom navigation bar: Portfolio, Holdings, Orders, Transactions, Settings (5 primary tabs).
- Color-coded alerts: Red for errors/mismatches, Amber for warnings, Green for success confirmations.
- No live market prices or real-time data indicators anywhere in the UI.

---

### Portfolio Screen (Home)

**Layout:** Full-screen scrollable view.

**Components:**

- Top: Date range filter selector (chip group: Current Month / Last 3M / Last 6M / This Year / All Time / Custom).
- Summary card: Realized P&L (large, prominent figure), P&L %, Invested Value, Total Charges, Charges %.
- Pie chart: P&L vs. Charges.
- Line graph: Cumulative P&L over selected range.
- Monthly P&L bar chart.
- Charges breakdown chart.

**States:**

- Loading: skeleton placeholders for summary card and charts.
- Empty: setup checklist widget replaces charts.
- Error: inline banner if last sync failed.

**Interactions:**

- Tapping chart segments shows a tooltip with exact values.
- Date range chip selection immediately refreshes all metrics and charts.

---

### Holdings Screen

**Layout:** Scrollable list of stock cards.

**Components:**

- Each card (collapsed): Stock symbol, quantity, invested amount, profit target (%), current target sell price.
- Each card (expanded): Full breakdown — buy charges, projected sell charges, projected net P&L at target.
- Edit Target button within expanded card.
- Profit target edit sheet: toggle between % and ₹ input, live preview of updated target sell price and projected P&L.

**States:**

- Loading: skeleton list.
- Empty: message "No current holdings. Sync orders to populate holdings."
- Error (charge rates missing): inline warning on cards using charge calculations.

**Interactions:**

- Tap card to expand/collapse.
- Edit Target opens a bottom sheet.
- Changes auto-save and trigger background GTT update; success/failure shown via snackbar.

---

### Orders Screen

**Layout:** Chronological list, newest first.

**Components:**

- Filter bar: Stock code search, date range chips.
- Order row: Date, stock symbol, type (BUY/SELL), quantity, price, total value, calculated charge.

**States:**

- Loading: skeleton rows.
- Empty (no filter): "No orders recorded. Sync today's orders or import historical data."
- Empty (filtered): "No orders match the selected filter."

**Interactions:**

- Infinite scroll loads 50 more orders on reaching list bottom.
- Filter chips are dismissible.

---

### Transactions Screen

**Layout:** Chronological list, newest first.

**Components:**

- Filter bar: transaction type chips, date range chips.
- Transaction row: Date, type label, description, amount (color-coded: green for inflows, red for outflows/charges).

**States:**

- Loading: skeleton rows.
- Empty (filtered): "No transactions match the selected filter."

**Interactions:**

- Infinite scroll loads 50 more entries on reaching list bottom.

---

### GTT Orders Screen

**Layout:** List of active GTT orders.

**Components:**

- GTT row: Stock symbol, trigger price, quantity, GTT status, indicator if value differs from app-calculated target.
- Warning indicator on rows where the GTT was manually modified in Zerodha.

**States:**

- Empty: "No active GTT orders."
- Sync in progress: loading indicator.
- Error: prominent banner if last GTT sync failed.

---

### Settings Screen

**Layout:** Grouped list of settings categories.

**Sections:**

- Automation: Order sync schedule, fund reconciliation schedule, charge rate refresh interval.
- Fund & Reconciliation: Tolerance threshold.
- Account: Bound Zerodha account (read-only display), Gmail connection status, Google Drive connection status.
- Backup & Restore: Manual backup, scheduled backup, restore, export.
- Security: Biometric lock status (read-only, always on).
- Notifications: Toggle.
- Appearance: Theme selector.
- Information: About, Guidebook, Privacy & Security, T&C.

---

### Onboarding Screens

**Layout:** Full-screen step-by-step cards with a progress indicator.

**Components:**

- Step title and explanation.
- Primary action button.
- Skip option (for optional steps: Gmail, Google Drive).
- Back navigation (except on T&C step).

**States:**

- Each step has a success state before advancing.
- Error state if authentication or permission fails, with retry option.

---

### Alert / Error Banners

**Placement:** Top of the relevant screen, below the navigation bar.

**Types:**

- Red banner: Critical mismatch (holdings, GTT failure, fund mismatch > tolerance).
- Amber banner: Warning (charge rates outdated, sync delayed, API rate limit hit).
- Green snackbar: Success confirmation (GTT updated, backup complete, target saved).

**Behavior:**

- Critical banners persist until the underlying issue is resolved.
- Warnings are dismissible but reappear at next relevant event.
- Success snackbars auto-dismiss after 3 seconds.

---

## 10. Functional Requirements

### Authentication

- FR-AUTH-01: The app must require successful biometric or device PIN authentication before displaying any app content.
- FR-AUTH-02: Authentication is required on every cold app open and after the app has been in background for more than [configurable timeout, default: 5 minutes].
- FR-AUTH-03: Biometric authentication cannot be disabled by the user.
- FR-AUTH-04: If the device has no biometric hardware and no device PIN, the app blocks use and prompts the user to set a device PIN.

### Account Binding

- FR-BIND-01: After first successful Zerodha authentication, the account identifier is permanently stored locally.
- FR-BIND-02: No UI exists within the app to change or remove the bound account.
- FR-BIND-03: Any backup or import file whose embedded account identifier does not match the bound account is rejected before processing.

### Fund Management

- FR-FUND-01: Manual fund entries require: type (addition/withdrawal), amount (non-zero positive number), and date. Note is optional.
- FR-FUND-02: The reconciliation tolerance threshold is a user-configurable value stored in Settings, defaulting to ₹50.
- FR-FUND-03: Automatic miscellaneous adjustments for within-tolerance reconciliation discrepancies are always written to the transaction log with a descriptive label.
- FR-FUND-04: Gmail-detected fund transactions must be individually confirmed by the user before logging. Bulk-confirm is not available.
- FR-FUND-05: The app must detect potential duplicate fund entries (same type, same amount, same date ±1 day) and warn the user before adding.

### Order Sync & Holdings Verification

- FR-ORD-01: Order sync fetches only today's orders from Zerodha API on each automated or manual trigger.
- FR-ORD-02: Holdings verification is mandatory before applying any new order data to local records.
- FR-ORD-03: If holdings verification fails, no local data is modified.
- FR-ORD-04: CSV imports must be fully validated before any data is written. Partial imports are not allowed.
- FR-ORD-05: Duplicate orders during CSV import are identified by order ID (or equivalent unique field) and skipped silently, with a count shown in the post-import summary.

### Charge Calculation

- FR-CHG-01: All charge calculations use the locally stored charge rates fetched from Zerodha.
- FR-CHG-02: Charges are calculated at the time an order is logged and stored as discrete transaction entries.
- FR-CHG-03: Charges are not recalculated retroactively if charge rates are updated. Only new orders use updated rates.

### GTT Automation

- FR-GTT-01: GTT orders created by the app are exclusively single-trigger sell orders.
- FR-GTT-02: GTT creation/update is triggered only after a successful order sync with new buy orders.
- FR-GTT-03: The app must perform a verification fetch after every GTT create or update action before updating local records.
- FR-GTT-04: If a GTT has been manually modified in Zerodha, the app must surface this to the user rather than silently overwriting.
- FR-GTT-05: GTT quantities are always set to match current local holding quantities.

### Scheduling

- FR-SCH-01: All automated background tasks are restricted to Mon–Fri. No task may execute on Saturday or Sunday.
- FR-SCH-02: Individual tasks can be enabled or disabled independently.
- FR-SCH-03: Multiple execution times per day are configurable per task.
- FR-SCH-04: Scheduling configuration changes take effect from the next day's schedule cycle.

### Backup & Restore

- FR-BAK-01: Backup files embed the bound Zerodha account identifier.
- FR-BAK-02: Restore operations are always additive. No existing local records are modified or deleted.
- FR-BAK-03: Excel export must include all orders, transactions, and holdings records for the selected scope.

### Notifications

- FR-NOT-01: Notifications are optional and can be toggled in Settings.
- FR-NOT-02: The following events generate notifications (if enabled): sync completion, GTT action result, fund mismatch alert, backup completion/failure.

---

## 11. Non-Functional Product Requirements

### Performance

- The Portfolio screen and all charts must render within 2 seconds for data sets up to 3 years of daily order history.
- List screens (Orders, Transactions) must load the first 50 items within 1 second from a local database.
- Background sync tasks must complete within a reasonable time window and not block app foreground use.

### Accessibility

- All text must meet WCAG AA contrast ratio requirements in both Dark and Light modes.
- Touch targets for interactive elements must be a minimum of 48×48dp.
- All form inputs must have visible labels (not placeholder-only labels).
- Color-coded alerts must also include icons or text labels so information is not conveyed by color alone.
- The app must support Android's system font size scaling.

### Privacy

- No user financial data is transmitted to any server other than: Zerodha API (for Kite Connect operations), Gmail API (for email scanning, read-only, filter-scoped), and the user's own Google Drive (for backup only).
- The app must not embed any third-party analytics SDKs, crash reporters, or advertising SDKs.
- Gmail scanning is strictly scoped to user-defined filters; the app must not read, store, or process email content outside of matching fund transaction detection.

### Reliability

- All API failures must be surfaced to the user. Silent failures that could result in mismatched local data are not acceptable.
- Background task failures must be logged locally and retried at the next scheduled opportunity.
- The app must never modify or delete existing local records in response to an error state.

### Usability

- Every automated action that results in data changes must produce a visible record (either a transaction log entry, a sync status indicator, or an alert).
- The in-app Full Guidebook must be accessible at all times from Settings, without requiring a network connection.
- Onboarding must be completable in under 10 minutes for a user with Kite Connect API credentials ready.

---

## 12. External Integrations (Product Perspective)

### Zerodha Kite Connect API (Free Tier)

**Purpose:** Core data source for fund balance, executed orders, holdings, GTT orders, and transaction charge rates.

**User Interaction:**

- User authenticates via Kite Connect OAuth during onboarding.
- Re-authentication is required when the API session expires (user-initiated).
- No persistent background credential storage that auto-renews without user action (subject to Kite Connect session model).

**Expected User-Visible Behavior:**

- After login, the app operates autonomously using the session until it expires.
- API errors or rate limit responses surface as amber warning banners with retry indicators.
- If an API endpoint is deprecated or returns an unexpected format, the app shows a critical alert instructing the user that a manual check is required and an app update may be needed.

---

### Gmail API

**Purpose:** Optional scanning of deposit/withdrawal confirmation emails to detect fund movements automatically.

**User Interaction:**

- User grants Gmail read permission during onboarding (optional, skippable).
- User defines one or more email filters (sender address, subject keyword) to scope what the app reads.
- Detected transactions are presented individually for user confirmation before logging.

**Expected User-Visible Behavior:**

- The Gmail connection status is visible in Settings.
- The user can revoke access and re-grant it from Settings.
- If Gmail permission is revoked externally (via Google account settings), the app detects this on next scan attempt and prompts the user to re-authorize or disable the feature.

---

### Google Drive API

**Purpose:** User-controlled cloud backup destination for the local database.

**User Interaction:**

- User connects their Google account during onboarding (optional, skippable).
- Backups are stored in a dedicated app folder within the user's Google Drive.
- Restore can pull backup files from Google Drive.

**Expected User-Visible Behavior:**

- Google Drive connection status visible in Settings.
- Backup history (dates and file names of previous backups) visible in Backup & Restore settings.
- If Drive permission is revoked, the next backup attempt fails with an error prompting re-authorization.

---

## 13. Edge Cases & Error Scenarios

| Scenario | Expected Product Behavior |
|---|---|
| API rate limit hit during automated sync | Show amber warning banner. Schedule retry at next task time. Log the event locally. Do not modify local data. |
| Holdings quantity mismatch after order sync | Show red alert with per-stock discrepancy details. Halt all updates. User must acknowledge. No data written. |
| GTT placement failure | Show red alert. Retry at next sync. Local GTT records not updated until verification confirms success. |
| Fund mismatch exceeds tolerance | Show red alert with both balance values and discrepancy amount. No automatic adjustment. Persists until resolved. |
| Fund mismatch within tolerance | Auto-log miscellaneous adjustment. Show amber snackbar. No user action required. |
| CSV import with malformed rows | Reject entire import (for Orders, Fund). For Excel restore: import valid rows, show list of failed rows. |
| CSV import with account ID mismatch | Reject entire import. Show error: "This file belongs to a different Zerodha account." |
| Backup upload to Google Drive fails | Show error. Offer to save backup to local storage instead. |
| Corrupted local backup file on restore | Abort restore entirely. Show error. Existing data unchanged. |
| User attempts to use app without Kite Connect session | Prompt re-authentication. Block all API-dependent features. Local read-only views remain accessible. |
| Biometric authentication fails repeatedly | Follow device's standard lockout behavior. Show PIN/passcode fallback. |
| Device has no biometrics and no PIN set | Block app use. Show setup prompt directing user to device security settings. |
| Kite Connect API endpoint deprecated | Show critical alert. Disable affected feature. Prompt user to check for app update. |
| Charge rates never fetched (fresh install, offline) | All charge-dependent fields show a placeholder and a prompt to refresh charge rates. |
| User sets profit target to 0% / ₹0 | Accept value. Calculate break-even target sell price. Show soft warning: "Target set to break-even." |
| Partial sell reduces holding to 0 (all sold via Zerodha before GTT triggers) | On next sync, holdings verification confirms 0 quantity. GTT archived locally. No new GTT created. |
| Manual GTT modification detected in Zerodha | Show notification/banner: "GTT for [STOCK] was modified in Zerodha." Prompt user to keep Zerodha value or revert to app-calculated target. |
| Scheduled task fires on a weekend (e.g., due to timezone edge) | Task is skipped. No execution. No alert. |
| Duplicate Gmail-detected fund transaction | Flag as potential duplicate before confirmation step. User decides whether to log or discard. |
| Network unavailable during background sync | Task fails gracefully. Amber warning shown. Retry at next scheduled time. No data modified. |

---

## 14. Privacy & User Data Considerations

### Data Collected and Why

| Data | Purpose |
|---|---|
| Zerodha account identifier | Account binding; data isolation per account |
| Executed order history | P&L calculation; holdings tracking; charge computation |
| Fund balance and transactions | Accurate local ledger; reconciliation |
| Transaction charge rates | Local charge calculation |
| GTT order details | Automated GTT management |
| Gmail email metadata and body (filter-matched only) | Fund transaction detection (optional) |
| Google Drive backup files | User-controlled data backup |

### Data Residency

- All financial data is stored exclusively on the user's device in the local database.
- Google Drive backups are stored in the user's own Google Drive account; the app has no access to any storage it does not write directly.
- No data is transmitted to any servers owned or operated by the app developer.

### User Controls

- Gmail integration is fully optional; the user can skip it at onboarding and disable it at any time from Settings.
- Google Drive backup is fully optional; the user can skip it at onboarding and disable it at any time.
- The user can export all local data to Excel at any time.
- The user can delete all app data by uninstalling the app (which also removes the account binding).
- Gmail email filter configuration is user-defined and adjustable at any time.

### Permissions Required

| Permission | Reason |
|---|---|
| Biometric / Device authentication | App lock (mandatory) |
| Internet | Zerodha API, Gmail API, Google Drive API calls |
| Gmail read access | Optional fund transaction detection (filter-scoped only) |
| Google Drive access | Optional backup and restore |
| Local storage (read/write) | CSV import, local backup files, Excel export |
| Background task execution | Scheduled order syncs and reconciliation |

### Consent

- Users must accept Terms & Conditions and Privacy Policy before any data is collected or any permission is requested.
- Each optional permission (Gmail, Google Drive) is requested at the specific onboarding step for that feature, with a clear explanation of why it is needed. Skipping is always available.
- The Privacy & Security in-app page provides a plain-language summary of all data handling at any time.

---

## 15. Development Phases (Product Roadmap)

### Phase 1 — MVP (Core Utility)

**Goal:** A functional, secure app that covers the primary use case: accurate local P&L tracking with GTT automation.

**Features included:**

- Onboarding: T&C acceptance, biometric setup, Zerodha authentication, account binding.
- Fund balance: manual entry only.
- Charge rate fetching and local storage.
- Executed orders: daily automated sync (4 PM), manual refresh, holdings verification flow.
- Holdings screen: read-only display with default 5% profit target, target sell price.
- GTT automation: auto-create and auto-update on order sync.
- Portfolio screen: realized P&L, charges, date range filters, pie chart and line graph.
- Transactions screen: full log with basic filtering.
- Dark/Light theme.
- Biometric/PIN lock.
- In-app About, Guidebook, Privacy & Security, T&C pages.
- Basic error handling and alert banners for all critical failure states.

---

### Phase 2 — Expansion (Data Completeness & Backup)

**Goal:** Add historical data coverage, backup safety, and optional automation enhancements.

**Features included:**

- CSV import for historical orders, fund transactions, and adjustments.
- Google Drive backup and restore.
- Local backup and restore.
- Excel export.
- Gmail-based fund detection with user-defined filters.
- Fund reconciliation (automated and manual).
- Configurable background scheduling (multiple times per day, per-task enable/disable).
- Charge rate auto-refresh scheduling.
- Notification system (optional, toggleable).
- Orders screen: filtering by stock code and date range.
- GTT screen: full read-only list with manual override detection.
- Setup checklist empty state on Portfolio screen.

---

### Phase 3 — Polish & Advanced Configuration

**Goal:** Refine the user experience, harden edge case handling, and add advanced configuration options.

**Features included:**

- Enhanced Portfolio visualizations: monthly P&L percentage graph, charges breakdown chart.
- Configurable app lock timeout.
- Scheduled Google Drive backup (recurring).
- Charge rate refresh interval configuration.
- Reconciliation tolerance configuration.
- Advanced CSV import validation with per-row error reporting.
- Full Excel restore (merge mode) with post-import summary.
- Comprehensive in-app Guidebook with CSV format documentation and step-by-step feature guides.
- Accessibility improvements: font scaling, contrast audit, touch target review.

---

## 16. Future Enhancements

- **Sector / Category Tagging:** Allow users to tag holdings by sector (e.g., IT, Pharma) and view P&L breakdowns by sector on the Portfolio screen.
- **Multiple Profit Target Strategies:** Support stepped profit targets (e.g., sell 50% at 5%, remainder at 10%) if Zerodha's GTT API supports OCO (One-Cancels-Other) triggers.
- **Dividend Tracking:** Allow users to manually log dividend income and include it in realized returns calculations.
- **Capital Gains Estimation:** Provide a local, non-advisory estimate of short-term and long-term capital gains based on logged order history and holding periods.
- **Watchlist:** A simple local watchlist of stocks the user is monitoring, with configurable price alert notes (no live price data; note-based only).
- **Widget:** Android home screen widget showing current realized P&L and last sync timestamp.
- **Per-Stock Historical P&L:** Drill-down view showing the complete trade history and realized P&L for a single stock across all time.
- **Backup Encryption:** Optional passphrase-based encryption for backup files before upload to Google Drive.
- **Multi-Language Support:** Localization for regional Indian languages.

---

## 17. Open Questions / Assumptions

### Assumptions Made

| # | Assumption |
|---|---|
| A1 | Zerodha Kite Connect free tier provides sufficient API endpoints for: fund balance, order history (today), holdings, GTT list, GTT create/update/delete, and transaction charge rates. Exact endpoint availability at free tier must be confirmed against Kite Connect documentation. |
| A2 | The Kite Connect session model requires periodic user re-authentication (not fully silent token refresh). The exact session lifetime is assumed to be documented in Kite Connect API docs; the app handles session expiry as a user-prompted re-auth event. |
| A3 | GTT orders in Zerodha are uniquely identifiable by a GTT ID that the app can store locally and reference for updates. |
| A4 | The charge rate information available via Kite Connect API provides sufficient data (brokerage %, STT, exchange charges, GST, SEBI charges) to calculate per-order charges locally with accuracy matching Zerodha contract notes. |
| A5 | Gmail API read access scoped to user-defined filters (label or query-based) is technically achievable without requesting full mailbox read access. |
| A6 | Google Drive API can be used to create an app-specific folder in the user's Drive, write backup files to it, and list/read those files for restore, within the scope of OAuth credentials the user grants. |
| A7 | The "same Gmail account linked to Zerodha" assumption for Google Drive backup is a UX convention, not a technical restriction. The user will simply use whichever Google account they authorize during the Google Drive setup step. |
| A8 | CSV import for historical orders is the only supported method for back-filling historical data; the Kite Connect free tier is assumed not to provide bulk historical order fetch endpoints. |
| A9 | Holdings tracking in this app covers equity delivery positions only. Intraday, F&O, or other instrument types, if returned by the holdings API, are ignored. |
| A10 | The app will be distributed as a sideloaded APK. Android's sideloading permissions (install from unknown sources) are handled by the user; the app does not need to guide this process beyond a note in the Guidebook. |

### Open Questions Requiring Stakeholder Clarification

| # | Question |
|---|---|
| Q1 | **GTT screen navigation placement:** Should the GTT Orders screen be a sixth item in the bottom navigation bar, or accessible from within the Holdings screen (as a contextual view)? The current 5-tab structure (Portfolio, Holdings, Orders, Transactions, Settings) is at a comfortable mobile maximum; adding a sixth may require rethinking navigation. |
| Q2 | **Manual GTT override handling:** When the app detects a GTT was manually modified in Zerodha, the spec says to "surface this to the user." Should the default action be to keep the Zerodha value (i.e., the user's manual change wins) or to prompt without a default? What happens if the user ignores this prompt? |
| Q3 | **Partial sell charge attribution:** When a user partially sells a holding, should sell charges be attributed to the sold portion only, using the average buy price? Is the remaining holding's buy cost recalculated, or does it retain the original per-lot cost basis? |
| Q4 | **Charge rate structure:** Does Zerodha's API return the actual charge rates (percentages and flat fees per charge type) in a structured format, or does the app need to hardcode rate tables and refresh them from a static source? This affects whether the "fetch charge rates" feature is an API call or a manually maintained configuration. |
| Q5 | **Reconciliation on weekends:** If the user opens the app on a weekend and has not had a Mon–Fri reconciliation recently, should reconciliation be available as a manual-only action on weekends, or is the Refresh button in the fund balance section always available regardless of day? |
| Q6 | **App lock timeout configurability:** Should the biometric/PIN re-authentication timeout (default 5 minutes in background) be user-configurable, or fixed? |
| Q7 | **Gmail filter minimum requirement:** Is the user required to define at least one Gmail filter before Gmail-based detection activates, or can the user opt in to "scan all emails" (not recommended for privacy but a valid UX question)? The spec assumes filters are required; confirm this is intentional. |
| Q8 | **Realized P&L formula edge case:** For stocks where the user has both sold some lots and still holds remaining lots, the P&L formula adds back the current holdings buy value. Should this "add back" use the specific lots that are still held (FIFO/LIFO costing) or the average cost across all purchase lots for that stock? |
| Q9 | **Notification content specificity:** Should sync completion notifications show detailed information (e.g., "3 orders synced, GTT updated for INFY and RELIANCE") or only a generic summary ("KiteWatch sync complete")? |
| Q10 | **Data deletion / factory reset flow:** If a user wants to clear all app data without uninstalling (e.g., to start fresh), should a "Reset App Data" option be available in Settings? This has significant implications given the permanent account binding model. |

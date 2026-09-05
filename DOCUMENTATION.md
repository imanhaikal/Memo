# Technical Documentation - Memo Budget

**Memo Budget** is a native Android expense tracking application designed with a "Fluid Pool" budgeting philosophy. This document serves as the primary technical reference for developers, detailing the architecture, core algorithms, data models, and design systems used in the project.

---

## 1. System Overview

The core purpose of Memo Budget is to provide real-time, actionable feedback on daily spending habits. Unlike static budget apps that just track totals, Memo dynamically recalculates a **Daily Spending Limit** based on:
1.  The remaining total budget (Pool).
2.  The number of days remaining in the cycle.

**Scope:**
*   **Platform:** Android (Min SDK 26)
*   **Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore.
*   **Privacy:** Local-first, offline-only architecture.

---

## 2. Architecture

The application follows the **Google Guide to App Architecture**, utilizing the **MVVM (Model-View-ViewModel)** pattern with a Unidirectional Data Flow (UDF).

### 2.1 Layers

*   **UI Layer (View):** Built entirely with **Jetpack Compose**. It is reactive and stateless where possible, observing state from the ViewModel.
    *   *Components:* Activities, Composables, Theme definitions.
*   **Presentation Layer (ViewModel):**
    *   **`MainViewModel`**: The primary state holder. It exposes a single `BudgetUiState` (or derived flows) to the UI.
    *   *Responsibilities:* Hosting the "Fluid Pool" calculation logic, transforming raw data from Repositories into UI-ready state, and handling user events.
*   **Domain/Data Layer (Repository):**
    *   **Manual DI (`AppContainer`)**: Coordinates dependencies like `TransactionDao` and `BudgetPreferences`.
*   **Data Source Layer:**
    *   **Room Database**: SQLite abstraction for structured data (Transactions).
    *   **Preferences DataStore**: For simple key-value pairs (Budget Settings).

---

## 3. Data Models & Schema

### 3.1 Transactions (Room Database)
Stored in a local SQLite database accessed via Room.

**Schema version 5.** Five entities: `transactions`, `budgets`, `budget_cycles`,
`category_caps` and `recurring_rules`.

```kotlin
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Long,               // Cents, always positive
    val note: String,
    val date: Long,                 // Epoch millis; local noon when hasTime is false
    val category: Category? = null,
    val description: String = "",
    val hasTime: Boolean = true,
    val budgetId: Long = 1,         // Owning budget
    val type: TransactionType = EXPENSE,
    val recurringRuleId: Long? = null
)
```

Amounts are a positive magnitude and `type` carries the sign. This keeps
`CurrencyUtils.parseAmountToCents`'s `> 0` rule intact — that same parser validates the
*total budget* field, where a negative value would break the pool arithmetic.

`transactions.budgetId` deliberately has **no** foreign key: adding one would have meant
rebuilding the table in the v4→v5 migration, and a `DROP TABLE` on the user's whole
spending history is the one operation worth designing around. `BudgetRepository.deleteBudget`
performs the cascade explicitly, inside a single database transaction.

**Cycle boundaries are epoch days, not millis.** A cycle is a range of calendar dates;
storing it as an instant makes a budget shift by a day when the user changes timezone.
Transactions keep real millis timestamps, and `CycleMath` owns every conversion between
the two.

`budget_cycles` stores **no** spend totals, only the budget amount as it stood. A stored
total becomes wrong the moment an expense is backdated into a closed cycle, so totals are
aggregated from the transactions that are still on file.

### 3.2 Preferences (DataStore)
Device-scoped settings only — budget values live in Room.

*   `ACTIVE_BUDGET_ID` (Long): which budget the dashboard is showing.
*   `MIGRATED_TO_ROOM` (Boolean): guards the one-time pre-v5 handoff.
*   `THEME_MODE`, `HAPTICS_ENABLED`, and the notification toggles.

Every `preferencesDataStore` delegate is declared in `data/MemoDataStore.kt` and nowhere
else: two delegates with the same file name throw at runtime, not compile time.

### 3.3 Upgrading from v4
A Room migration runs on raw SQLite and cannot read DataStore, so the handoff is two-part.
`MIGRATION_4_5` creates a placeholder budget row; `BudgetBootstrap` then fills it from the
old DataStore values and backfills a cycle row per elapsed period. `uiState` awaits that
work before deciding whether the user has a budget — otherwise an upgrading user would be
shown the setup dialog over their own history. `PreUpgradeSnapshot` copies the v4 database
file aside before Room ever opens it.

---

## 4. Core Logic: The "Fluid Pool" Algorithm

The heart of the application is the dynamic recalculation engine located in `MainViewModel.calculateBudget()`. This function runs reactively whenever the transaction list or current date changes.

### 4.1 Algorithm Steps

1.  **Determine Inputs:**
    *   `Total Budget` (T)
    *   `Cycle Start Date` (D_start)
    *   `Cycle Duration` (N_days)
    *   `Current Date` (D_now)
    *   `Transaction History`

2.  **Calculate Time Metrics:**
    *   `Days Passed` = (D_now - D_start)
    *   `Days Remaining` (R) = max(1, N_days - Days Passed).
    *   *Constraint:* `R` must be >= 1 (even on the last day).

3.  **Calculate Financial Metrics:**
    *   `Spent Before Today` (S_prev): Sum of transactions where `date < Today`.
    *   `Spent Today` (S_today): Sum of transactions where `date == Today`.
    *   **Remaining Pool (P):** `T - S_prev`. This is the cash currently on hand for the rest of the cycle (including today).

4.  **Derive Daily Limit:**
    *   **Baseline Limit (L):** `P / R`.
        *   This represents how much you can spend *every day* from now until the end of the cycle to land perfectly at 0.
    *   **Available Today (A):** `L - S_today`.
        *   This is the "One Big Number" shown to the user.
    *   **New Daily Limit (displayed):** normally `L`, but if `S_today > L` (over limit) and `R > 1`, the overspend is re-amortized immediately: `(P - S_today) / (R - 1)`, floored at `0`.

### 4.2 Edge Cases
*   **Bankruptcy:** If `P <= 0` (or today's overspend exhausts the pool), `New Daily Limit` is forced to `0`. The user is in debt to themselves.
*   **Last Day:** If `R == 1`, `New Daily Limit` equals the entire `Remaining Pool`.

---

## 5. UI/UX Design

The design language is defined as "Premium Hardware" or "Clean Tech," prioritizing tactility and focus.

### 5.1 Design System
*   **Typography:** `Inter` font family.
    *   *Critical:* The Hero Number uses `tnum` (Tabular Numbers) font feature settings to prevent jitter during rolling number animations.
*   **Color Palette:**
    *   `Surface`: Off-white (`#F9F9F9`) or warm gray.
    *   `Accent`: Sunday Yellow (`#F2E057`).
    *   `Ink`: Deep Black (`#111111`) for primary text.
*   **Components:**
    *   Custom Cards with soft, diffuse shadows (`elevation`) and subtle inner borders (1dp).
    *   **Rounded Shapes:** 32dp for major cards, 50% circle for FAB.

### 5.2 Motion & Haptics ("Sunday Feel")
*   **Physics-based Animations:**
    *   **Rolling Numbers:** `Animatable` transitions for currency values.
    *   **Springs:** Used for FAB scale (`Spring.DampingRatioMediumBouncy`) and dialog entrances.
*   **Haptic Feedback:**
    *   `LongPress` (Light Impact) triggered on FAB press, List Item clicks, and critical Button actions.
    *   Provides tactile confirmation of user intent.

### 5.3 Screen Flow
1.  **Setup Dialog:** Initial onboarding to set budget amount and duration.
2.  **Dashboard (Home):**
    *   **Hero:** Large "Available Today" display with status pill (On Track / Over Limit).
    *   **Stats:** Daily Limit and Days Remaining.
    *   **List:** Recent transactions.
    *   **FAB:** Triggers "Add Expense".
3.  **Add Expense Dialog:** A modal for quick entry.

---

## 6. Testing Strategy

*   **Unit Tests:** `MainViewModelTest` verifies the core budgeting algorithm logic across various scenarios (spending, new day, over limit).
*   **Instrumentation Tests:**
    *   `TransactionDaoTest`: Verifies database integrity on an Android device/emulator.
    *   `Phase5PolishTest`: Verifies UI polish features (FAB interactions, Input focus, Haptics triggers) using Compose Test Rule.

---

## 7. Background Work

`DayTicker` emits the current local date and again whenever it changes — at midnight, and
on time or timezone changes. It is an input to `uiState`, which is what keeps the hero
number honest in an app left open overnight, and it also drives cycle rollover.

Recurring expenses post from `PostRecurringUseCase`, which runs on **every app start** as
well as from `RecurringPostWorker`. The worker is the convenience; launch catch-up is the
guarantee, because vendor battery managers suppress periodic work for days at a time and a
rent expense that silently never posts is a data-integrity bug, not a missed reminder.

Scheduling is `PeriodicWorkRequest` only — no `AlarmManager`, no `SCHEDULE_EXACT_ALARM`,
no expedited work. A 9:00 reminder may arrive at 9:20; that is an acceptable trade for
staying out of the exact-alarm permission entirely.

## 8. Home-screen Widget

`MemoWidget` (Glance) reads only the snapshot in `WidgetSnapshotRepository`, never Room.
Glance can recompose in a process where the app's singletons are cold, and a widget is not
the place to be opening a database. `WidgetUpdater` recomputes that snapshot after any
write that changes the numbers.

Glance's `ColorProvider` is a different type from Compose's `Color` with no bridge between
them, so `WidgetColors` re-declares the palette and must be kept in step with
`ui/theme/Color.kt` by hand.

## 9. Future Roadmap

1.  **Localization:** every string is still hardcoded in Kotlin.
2.  **Release signing:** the release build is still signed with the debug keystore.
3.  **Gemini API key:** currently baked into `BuildConfig` and extractable from the APK.
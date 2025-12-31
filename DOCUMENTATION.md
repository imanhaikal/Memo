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
    *   **`BudgetRepository`**: A single source of truth that coordinates data from `Room` (Transactions) and `DataStore` (Preferences).
    *   *Responsibilities:* Exposing `Flow`s of data, handling IO dispatchers, and abstraction of data sources.
*   **Data Source Layer:**
    *   **Room Database**: SQLite abstraction for structured data (Transactions).
    *   **Proto DataStore / Preferences DataStore**: For simple key-value pairs (Budget Settings).

---

## 3. Data Models & Schema

### 3.1 Transactions (Room Database)
Stored in a local SQLite database accessed via Room.

**Entity:** `Transaction`
```kotlin
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,          // The cost of the item
    val note: String,            // User description (optional)
    val timestamp: Long          // Epoch timestamp for date calculations
)
```

**DAO:** `TransactionDao` provides methods for `insert`, `delete`, and reactive queries (`getAll()` returning `Flow<List<Transaction>>`).

### 3.2 Budget Settings (DataStore)
Stored using Jetpack DataStore (Preferences) for lightweight persistence.

**Keys:**
*   `TOTAL_BUDGET` (Double): The total amount allocated for the cycle.
*   `CYCLE_START_DATE` (Long): Epoch timestamp of the start date.
*   `CYCLE_DURATION_DAYS` (Int): Total length of the budget cycle.

---

## 4. Core Logic: The "Fluid Pool" Algorithm

The heart of the application is the dynamic recalculation engine located in `MainViewModel.calculate()`. This function runs reactively whenever the transaction list or current date changes.

### 4.1 Algorithm Steps

1.  **Determine Inputs:**
    *   `Total Budget` (T)
    *   `Cycle Start Date` (D_start)
    *   `Cycle Duration` (N_days)
    *   `Current Date` (D_now)
    *   `Transaction History`

2.  **Calculate Time Metrics:**
    *   `Days Passed` = (D_now - D_start)
    *   `Days Remaining` (R) = N_days - Days Passed + 1 (Includes "today").
    *   *Constraint:* `R` must be >= 1 (even on the last day).

3.  **Calculate Financial Metrics:**
    *   `Spent Before Today` (S_prev): Sum of transactions where `date < D_now`.
    *   `Spent Today` (S_today): Sum of transactions where `date == D_now`.
    *   **Remaining Pool (P):** `T - S_prev`. This is the cash currently on hand for the rest of the cycle.

4.  **Derive Daily Limit:**
    *   **New Daily Limit (L):** `P / R`.
        *   This represents how much you can spend *every day* from now until the end of the cycle to land perfectly at 0.
    *   **Available Today (A):** `L - S_today`.
        *   This is the "One Big Number" shown to the user.

### 4.2 Edge Cases
*   **Bankruptcy:** If `P <= 0`, `New Daily Limit` is forced to `0`. The user is in debt to themselves.
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
    *   Custom Cards with soft, diffuse shadows (`spotColor`, `ambientColor`) and subtle inner borders.
    *   No standard Material "Elevations" or drop shadows.

### 5.2 Screen Flow
1.  **Setup Screen:** Initial onboarding to set budget amount and duration.
2.  **Dashboard (Home):**
    *   **Hero:** Large "Available Today" display with status pill (On Track / Over Limit).
    *   **Stats:** Daily Limit and Days Remaining.
    *   **List:** Recent transactions.
    *   **FAB:** Triggers "Add Expense".
3.  **Add Expense:** A modal/bottom sheet for quick entry.

---

## 6. State Management

*   **Source of Truth:** The `ViewModel` holds `MutableStateFlow<BudgetUiState>`.
*   **Observation:** The UI calls `collectAsStateWithLifecycle()` to safely observe state changes.
*   **Events:** User actions (e.g., `saveTransaction`) are methods on the ViewModel that launch coroutines to update the Repository, which in turn emits new data via Flow, triggering the `calculate()` logic and updating the UI State.

---

## 7. Dependencies & Rationale

| Library | Purpose | Rationale |
| :--- | :--- | :--- |
| **Jetpack Compose** | UI Framework | Declarative, modern, and allows for complex animations easily. |
| **Room** | Database | Type-safe SQLite abstraction with built-in Coroutine/Flow support. |
| **DataStore** | Preferences | Modern replacement for SharedPreferences; handles data updates asynchronously. |
| **Hilt** | Dependency Injection | (Planned) Standardizes component instantiation and scoping (Singleton, ViewModelScoped). |
| **java.time** | Date/Time | Robust handling of dates and durations (Desugaring enabled for API < 26). |

---

## 8. Future Roadmap

Technical improvements and features slated for future phases (derived from `TASKS.md`):

1.  **WorkManager Integration:** Needed to reliably recalculate the budget at midnight (00:00) if the app is in the background, ensuring notifications are accurate.
2.  **Multi-Budget Support:** Refactoring the schema to support multiple concurrent or archived budgets (e.g., "Travel" vs "Monthly").
3.  **Currency Support:** Abstracting currency formatting to support non-USD locales.
4.  **Automated Tests:** Comprehensive unit tests for the `calculate()` algorithm to ensure mathematical correctness across date boundaries.
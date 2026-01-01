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

**Entity:** `Transaction`
```kotlin
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,          // The cost of the item
    val note: String,            // User description (optional)
    val date: Long               // Epoch timestamp
)
```

**DAO:** `TransactionDao` provides methods for `insert`, `delete`, and reactive queries (`getAllTransactions()` returning `Flow<List<Transaction>>`).

### 3.2 Budget Settings (DataStore)
Stored using Jetpack DataStore (Preferences) for lightweight persistence.

**Keys:**
*   `TOTAL_BUDGET` (Double): The total amount allocated for the cycle.
*   `CYCLE_START_DATE` (Long): Epoch timestamp of the start date.
*   `CYCLE_DURATION_DAYS` (Int): Total length of the budget cycle.

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

## 7. Future Roadmap

1.  **WorkManager Integration:** Needed to reliably recalculate the budget at midnight (00:00) if the app is in the background.
2.  **Multi-Budget Support:** Refactoring the schema to support multiple concurrent or archived budgets (e.g., "Travel" vs "Monthly").
3.  **Currency Support:** Abstracting currency formatting to support non-USD locales.
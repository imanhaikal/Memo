# REQUIREMENTS.md - Dynamic Expense Tracker (Kotlin + Jetpack Compose)

## 1. Project Overview
**Project Name:** Memo Budget (Working Title)
**Platform:** Android (Native)
**Technology Stack:** Kotlin, Jetpack Compose, Android Architecture Components
**Goal:** To build an Android expense tracking application that treats a budget as a fluid pool of funds. This system dynamically recalculates the "Daily Spending Limit" in real-time, providing immediate feedback.
**Core Philosophy:**
*   **Underspending** today increases the daily limit for all future days.
*   **Overspending** today decreases the daily limit for all future days to ensure the user does not run out of funds before the cycle ends.

---

## 2. User Stories
*   **As a user**, I want to define a specific budget amount for a custom date range (start/end date or total days) so that I can track expenses for a month, a week, or a specific trip.
*   **As a user**, I want to see exactly how much I can spend *today* without breaking my budget for the rest of the period, displayed prominently.
*   **As a user**, I want my future daily allowances to automatically adjust and be reflected in the UI if I spend too much or too little today.
*   **As a user**, I want to quickly add an expense from anywhere in the app.
*   **As a user**, I want to view a history of my expenses.
*   **As a user**, I want a clear visual warning if I'm overspending.

---

## 3. Functional Requirements

### 3.1 Budget Setup & Configuration
The user must be able to initialize and modify a budget cycle with the following parameters:

1.  **Total Budget Amount:** (e.g., $3000). Must accept decimal values.
2.  **Budget Cycle Configuration:** The user must be able to choose between two primary time modes:
    *   **Mode A: Date Range:** User selects a `startDate` and an `endDate` using Android's built-in Date Pickers.
    *   **Mode B: Countdown/Days Remaining:** User inputs a specific `numberOfDays` for the budget to last from the current date.
3.  **Currency Symbol:** (Future) Allow selection of preferred currency (e.g., USD, EUR, GBP). For MVP, default to USD.

### 3.2 The Calculation Engine (Core Logic)
The system must recalculate and update the displayed metrics reactivity every time a relevant change occurs (transaction added/edited/deleted, date changes).

**Variables & Data Flow:**
*   `totalBudget`: Stored in `DataStore`.
*   `cycleStartDate`: Stored in `DataStore`.
*   `cycleEndDate`: Derived from `cycleStartDate` and `numberOfDays`, or explicitly set.
*   `transactions`: Stored in `Room` database.
*   `currentDate`: Derived from system time.

**Algorithm Logic (within ViewModel):**
1.  **Determine Cycle Dates:**
    *   If Mode A (Date Range): Use provided `startDate` and `endDate`.
    *   If Mode B (Countdown): `startDate` is the day budget is set, `endDate` is `startDate + numberOfDays`.
2.  **Calculate Days Remaining:** `D_remaining = total days in cycle - days passed + 1` (including today). Minimum 1 day.
3.  **Calculate Spent:**
    *   `spentBeforeToday`: Sum of all transactions where `transaction.date < currentDate`.
    *   `spentToday`: Sum of all transactions where `transaction.date == currentDate`.
    *   `totalSpent`: Sum of all transactions within the current budget cycle.
4.  **Calculate Remaining Pool (Cash on hand):** `Pool = totalBudget - spentBeforeToday`.
5.  **Calculate New Daily Limit:** `newDailyLimit = Pool / D_remaining`.
6.  **Calculate Available Today:** `availableToday = newDailyLimit - spentToday`.

**Constraint:**
*   If `D_remaining` is 1 (the last day), the `newDailyLimit` equals the entire `Pool`.
*   If `Pool` becomes negative (Total Spent > Total Budget), the `newDailyLimit` should be `0`, and `availableToday` will show a negative value.

### 3.3 Transaction Management
*   **Add Expense:**
    *   Input amount (`Decimal` or `Double`).
    *   Optional `note` (`String`).
    *   Defaults `date` to current device date and time.
    *   A dedicated "Add Expense" Composable (e.g., a bottom sheet or a full-screen dialog).
*   **Edit Expense:**
    *   Allow users to tap on a transaction to edit its amount or note.
    *   Must trigger recalculation upon save.
*   **Delete Expense:**
    *   Implement swipe-to-delete gesture on transaction list items.
    *   Must trigger recalculation upon deletion.
*   **History:** A `LazyColumn` displaying all transactions within the current budget cycle, sorted by most recent first.

### 3.4 Data Persistence
*   **Room Database:**
    *   Store `Transaction` entities (`id`, `amount`, `note`, `date`).
    *   Store `Budget` entities (if supporting multiple budgets) or current budget parameters.
*   **Jetpack DataStore (Preferences):**
    *   Store single key-value preferences like `totalBudget`, `totalDays` (or `cycleStartDate` / `cycleEndDate`). This is suitable for a single active budget.
*   **State Management:**
    *   Utilize `ViewModel`s to hold UI state and expose data as `StateFlow`s or `LiveData`s.
    *   Compose UI Observes these `Flow`s for reactive updates.

---

## 4. UI/UX Requirements (Jetpack Compose Specifics)

### 4.1 Design System Components
*   **Compose `Card`:** Used for main data displays (Hero, Stat Cards).
*   **Compose `Button` / `FloatingActionButton` (FAB):** For actions.
*   **Compose `TextField`:** For all input fields.
*   **Compose `Text`:** For all text elements, leveraging `MaterialTheme.typography` and custom `TextStyle` for the hero number.

### 4.2 Screens (Composables)

#### 4.2.1 Setup/Onboarding Screen
*   **Input Fields:** `TextField` for budget amount and number of days.
*   **Date Pickers:** If "Date Range" mode is selected, use `DatePickerDialog` or `DatePicker` composable.
*   **Action:** Primary button (`Button`) to "Start Tracking".

#### 4.2.2 Dashboard Screen (Primary View)
*   **`Scaffold`:** Main layout structure for app bar, content, and FAB.
*   **Hero Section:**
    *   Large `Text` Composable for "Available to Spend Today".
    *   `Modifier.background()` and `clip(RoundedCornerShape)` for Status Pill.
    *   **Color Coding:** `Color` parameter for the hero amount and `Modifier.background()` for the status pill based on the calculated status (Green for on track, Red for over limit, Yellow/Gray for careful).
*   **Stats Grid:**
    *   Two `Card` Composables in a `Row` or `LazyRow` (or custom layout).
    *   Display "New Daily Limit" and "Days Left".
*   **Transaction History:**
    *   `LazyColumn` to efficiently display `TransactionItem` Composables.
    *   `TransactionItem` should be a `Card` or `Surface` with `Row` for details (Note, Date, Amount).
*   **Floating Action Button (FAB):** For "Add Expense".

#### 4.2.3 Add Expense Dialog/Screen
*   **Layout:** Can be an `AlertDialog`, a `BottomSheetScaffold`, or a dedicated `composable` screen.
*   **Input Fields:** `TextField` for amount (numeric keyboard) and note (text keyboard).
*   **Action:** Primary button to "Save Expense", secondary to "Cancel".

### 4.3 Motion Design (Animations in Compose)
Animations are key for the "Premium Hardware" feel.

*   **Rolling Numbers:**
    *   Use `Animatable` or `animate*AsState` for smooth transitions of numeric values in the Hero and Stats.
    *   `graphicsLayer` with `Modifier.scaleX`, `Modifier.scaleY`, `Modifier.alpha` can be used for custom entrance effects.
*   **Staggered Entrance:**
    *   Apply `AnimatedVisibility` or `Modifier.animateEnterExit` with `EnterTransition.slideInVertically` and `fadeIn` with staggered delays for Dashboard elements (Hero, Stats, List, FAB).
*   **Spring Physics:**
    *   Utilize `spring()` animation specs for interactive elements like `Button` presses or `Modal` entries (e.g., `animateContentSize`, `animateOffsetAsState`, `animateFloatAsState`).
*   **List Entry:**
    *   `LazyColumn` items should use `animateItemPlacement()` (for reordering) or custom `Modifier.graphicsLayer` and `Modifier.animateEnterExit` for individual item entry (e.g., slide-in from bottom, fade-in).
*   **Status Transitions:**
    *   `animateColorAsState` for smooth color changes on the Hero text and Status Pill background/text when the budget status changes.

---

## 5. Technical Logic & Edge Cases (Android Specific)

### 5.1 Date & Time Handling
*   **Library:** Utilize `java.time` (or `kotlinx.datetime`) for all date and time calculations (`LocalDate`, `LocalDateTime`, `Duration`).
*   **Timezone:** All calculations should ideally use UTC internally and convert to the user's local timezone for display. `isToday` logic should be robust.
*   **Budget Cycle Reset:**
    *   When `currentDate` > `cycleEndDate`, the app should detect that the budget cycle is complete.
    *   **Option 1 (MVP):** Prompt the user to archive the current budget and start a new one (clearing the `DataStore` and prompting for setup).
    *   **Option 2 (Future):** Implement `WorkManager` to trigger background notifications or automated budget resets/rollovers.

### 5.2 Negative Values (Budget Exceeded)
*   **UI:** The `Text` composable for `availableToday` should turn `var(--danger-color)` (red). The Status Pill should show "Over Limit".
*   **Functionality:** `newDailyLimit` should be calculated as `0` if `Pool <= 0`.

### 5.3 Zero Days Remaining
*   The `calculate()` function must always ensure `D_remaining` is at least `1` to prevent division by zero, even if it's the last day of the cycle.
*   When a cycle formally ends, the UI should transition to a summary screen or prompt for a new budget.

### 5.4 App Lifecycle & State Restoration
*   `ViewModel`s ensure data survives configuration changes (rotation).
*   `rememberSaveable` for Compose-specific UI state that needs to survive process death (e.g., scroll position, modal visibility).

---

## 6. Development Milestones (Kotlin + Jetpack Compose)

### Phase 1: Project Setup & Core Data
- [ ] **Android Project Setup:** New Android Studio project with Jetpack Compose.
- [ ] **Compose Navigation:** Set up `NavController` and define routes for `SetupScreen`, `DashboardScreen`, `AddTransactionScreen`.
- [ ] **Data Classes:** Define `Transaction` and `Budget` data classes (e.g., `data class Transaction(...)`).
- [ ] **Room Database Integration:**
    - [ ] Set up `Room` entities for `Transaction`.
    - [ ] Create `DAO` for `Transaction` (insert, get all, delete).
    - [ ] Configure `AppDatabase`.
- [ ] **DataStore Integration:**
    - [ ] Set up `DataStore` for preferences (`totalBudget`, `cycleStartDate`, `totalDays`).
    - [ ] Create a repository/manager for `DataStore` interactions.

### Phase 2: Core Logic & Initial UI
- [ ] **`BudgetViewModel`:**
    - [ ] Inject `TransactionRepository` and `DataStoreManager`.
    - [ ] Expose `StateFlow`s for `totalBudget`, `cycleStartDate`, `transactions`.
    - [ ] Implement `calculate()` logic (Budget Pool, Daily Limit, Days Left, etc.).
    - [ ] Expose calculated UI state (`availableToday`, `newDailyLimit`, `daysLeft`, `status`) as `StateFlow`.
- [ ] **Setup Screen:**
    - [ ] Implement basic `TextField`s for budget and days.
    - [ ] Implement `DatePickerDialog` for `startDate` and `endDate` if using date range mode.
    - [ ] Connect to `BudgetViewModel` to save budget configuration.
- [ ] **Dashboard Screen:**
    - [ ] Basic `Scaffold` with `TopAppBar` and `FloatingActionButton`.
    - [ ] Display `availableToday`, `newDailyLimit`, `daysLeft` using `Text` Composables.
    - [ ] Implement `LazyColumn` for `TransactionItem`s (without edit/delete).
    - [ ] Observe `BudgetViewModel`'s `StateFlow`s to reactively update the UI.

### Phase 3: Transaction Management & Polish
- [ ] **Add Transaction UI:**
    - [ ] Implement `AddTransactionScreen` (e.g., `BottomSheetScaffold` or `AlertDialog`).
    - [ ] Connect to `BudgetViewModel` to save new transactions to `Room`.
- [ ] **Edit/Delete Transactions:**
    - [ ] Implement `SwipeToDismiss` for `LazyColumn` items to delete transactions.
    - [ ] Implement click listener on `TransactionItem` to open an edit dialog/screen.
- [ ] **UI Refinements:**
    - [ ] Apply `DESIGN.md` styling (colors, typography, shapes, shadows) to all Composables.
    - [ ] Implement "Empty State" for transaction list.

### Phase 4: Animations & Advanced Polish
- [ ] **Staggered Dashboard Entry:** Implement `AnimatedVisibility` with delays for cards and list.
- [ ] **Rolling Number Animations:** Use `Animatable` or `animate*AsState` for `availableToday` and `newDailyLimit`.
- [ ] **Status Pill Transitions:** Animate `backgroundColor` and `textColor` of the status pill.
- [ ] **FAB Animations:** Add scaling/elevation animations on press/hover.
- [ ] **Modal Animations:** Implement `scale` and `translateY` animations for dialogs/sheets with `spring()` physics.
- [ ] **Reset Functionality:** Implement a clear "Reset App" option.
- [ ] **Input Focus:** Ensure correct focus behavior when modals open.

## 🐛 Known Bugs / Issues (Android Specific)
1.  **Background Processing:** If the user doesn't open the app for days, the daily limit calculation won't update until the app is opened. Consider `WorkManager` for daily checks/notifications.
2.  **Date Picker UX:** Standard Android Date Pickers might not perfectly match the "Clean Tech" aesthetic; custom theming or a custom composable might be needed.
3.  **Keyboard Handling:** Ensuring the keyboard gracefully appears/disappears and doesn't obscure content in input forms (especially with `BottomSheetScaffold`).
4.  **Accessibility:** Ensure all UI elements have proper content descriptions for screen readers.
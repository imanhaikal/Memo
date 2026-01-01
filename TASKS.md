# TASKS.md - Memo Budget (Android)

## 📋 Project Tracker
**Status:** `Phase 6: Release Readiness`
**Stack:** Kotlin, Jetpack Compose, Room, DataStore, MVVM
**Target Aesthetic:** Premium Hardware / "Clean Tech"

---

## Phase 1: Foundation & Scaffold
*Goal: Initialize the project, set up the architecture, and define the design system.*

- [x] **Project Setup**
    - [x] Create new Android Studio Project (Empty Activity - Compose).
    - [x] Configure `build.gradle.kts`:
        - [x] Add `Room` dependencies (KSP, Runtime).
        - [x] Add `DataStore` (Preferences) dependency.
        - [x] Add `Navigation Compose` dependency.
        - [x] Add `Extended Icons` (Material) dependency.
    - [x] Enable Edge-to-Edge support in `MainActivity`.
- [x] **Theming (The Design System)**
    - [x] `Color.kt`: Define `AppColors` palette (Off-whites, Sunday Yellow, Ink Black).
    - [x] `Type.kt`: Configure `Inter` font family.
        - [x] **Crucial:** Enable `FontFeature.TabularNumbers` (`tnum`) for Hero typography.
    - [x] `Theme.kt`: Set up `MaterialTheme` wrapper, forcing Light Mode (or custom Dark Mode handling).
- [x] **Navigation**
    - [x] Create `NavRoutes` sealed class/object (`Setup`, `Dashboard`, `AddExpense`).
    - [x] Set up basic `NavHost` in `MainActivity`.

---

## Phase 2: Data Layer (Room & DataStore)
*Goal: specific persistence for Transactions and Budget Settings.*

- [x] **Budget Settings (DataStore)**
    - [x] Create `BudgetPreferences` repository.
    - [x] Define keys: `TOTAL_BUDGET` (Double), `CYCLE_START_DATE` (Long/String), `CYCLE_DURATION_DAYS` (Int).
    - [x] Implement `read` (Flow) and `write` (suspend) functions.
- [x] **Transactions (Room)**
    - [x] Create `Transaction` Entity (`id`, `amount`, `note`, `timestamp`).
    - [x] Create `TransactionDao`:
        - [x] `insert(transaction)`
        - [x] `delete(transaction)`
        - [x] `getAll()` (Return `Flow<List<Transaction>>`).
        - [x] `getTransactionsForDateRange(start, end)` (Optional optimization).
    - [x] Setup `AppDatabase` and provide instance.
- [x] **Repository Layer**
    - [x] Create `BudgetRepository`: Combine DataStore and DAO flows into a single source of truth for the ViewModel.

---

## Phase 3: Domain Logic (The Brain)
*Goal: Implement the dynamic recalculation engine.*

- [x] **MainViewModel Setup**
    - [x] Create `MainViewModel` (inject Repository).
    - [x] Define `BudgetUiState` data class:
        - [x] `isLoading`: Boolean
        - [x] `needsSetup`: Boolean (if budget not set)
        - [x] `currencySymbol`: String
        - [x] `availableToday`: Double
        - [x] `dailyLimitBaseline`: Double
        - [x] `daysRemaining`: Int
        - [x] `status`: Enum (ON_TRACK, CAREFUL, OVER_LIMIT)
        - [x] `transactions`: List<Transaction>
- [x] **Calculation Logic (Reactive)**
    - [x] Implement `combine` logic on Flow collection:
        - [x] **Input:** `totalBudget`, `startDate`, `duration`, `transactionList`.
        - [x] **Logic:**
            1. Calculate `spentBeforeToday`.
            2. Calculate `remainingPool`.
            3. Calculate `newDailyLimit` (`pool / daysRemaining`).
            4. Calculate `availableToday` (`newDailyLimit - spentToday`).
    - [x] Handle **Edge Cases**:
        - [x] `daysRemaining <= 0` (End of cycle).
        - [x] `remainingPool <= 0` (Bankruptcy).

---

## Phase 4: UI Implementation (Composables)
*Goal: Build the screens according to DESIGN.md.*

- [x] **Onboarding / Setup Screen**
    - [x] Create layout: Title, Amount Input, Days Input, "Start" Button.
    - [x] Validation: Ensure numbers are positive.
    - [x] Action: Save to DataStore -> Navigate to Dashboard.
- [x] **Dashboard Screen (Main)**
    - [x] **Hero Card:**
        - [x] Display "Available Today".
        - [x] Status Pill (Dynamic color).
    - [x] **Stats Grid:**
        - [x] Row with two cards: "New Limit" and "Days Left".
    - [x] **Transaction List:**
        - [x] `LazyColumn` implementation.
        - [x] `TransactionItem` composable (Card style, Row layout).
        - [x] Empty State view (Minimalist icon).
    - [x] **FAB:**
        - [x] "Add" icon.
        - [x] Positioned bottom right (with scaffold padding).
- [x] **Add Expense Interface**
    - [x] Decide: BottomSheet or Full Screen Dialog? (Recommendation: `ModalBottomSheet`).
    - [x] Numeric Keypad input focus (Auto-open keyboard).
    - [x] Save Action: Insert to Room.

---

## Phase 5: Polish & Motion (The "Sunday" Feel)
*Goal: Replace static transitions with physics-based motion.*

- [x] **Rolling Numbers**
    - [x] Create `RollingNumberText` composable using `Animatable` and `SideEffect`.
    - [x] Apply to Hero Amount and Daily Limit text.
- [x] **Entrance Animations**
    - [x] Apply `AnimatedVisibility` (Slide Up + Fade) to Dashboard components.
    - [x] Stagger the delays (Hero -> Stats -> List).
- [x] **Interactive Physics**
    - [x] Add `scale` animation on FAB press (`Modifier.clickable` interaction source).
    - [x] Add `spring` spec to BottomSheet open/close.
- [x] **Haptics**
    - [x] Add `LocalHapticFeedback` triggers on:
        - [x] Button Taps.
        - [x] Rolling number completion (subtle).
- [x] **Refinements**
    - [x] Adjust Shadows: Use `ambientColor` and `spotColor` for soft diffusion.
    - [x] Input Fields: Remove underline, use custom background shapes.

---

## Phase 6: Release Readiness
*Goal: Prepare for deployment.*

- [ ] **App Icon:** Design adaptive icon (Background: Yellow, Foreground: Abstract shape or "M").
- [ ] **Splash Screen:** Implement Android 12+ Splash API.
- [ ] **Testing:**
    - [x] Test Date rollover (Manually change device time).
    - [x] Test "Bankruptcy" state (Spend all money).
    - [x] Test configuration changes (Dark mode toggle, Rotation).
- [ ] **Build:** Generate Signed APK/AAB.

---

## 🐞 Known Android Issues to Watch
1.  **Soft Keyboard handling:** Ensure `WindowInsets.ime` padding is applied so the "Save" button in the Add Expense sheet isn't covered by the keyboard.
2.  **Date Desugaring:** If supporting Android < 8.0 (API 26), enable Java 8 library desugaring in Gradle to use `java.time`.
3.  **System Bar Colors:** Ensure Status Bar icons switch to Dark/Light correctly depending on the background color.
# TASKS.md - Memo Budget (Android)

## 📋 Project Tracker
**Status:** `Phase 1: Initialization`
**Stack:** Kotlin, Jetpack Compose, Room, DataStore, MVVM
**Target Aesthetic:** Premium Hardware / "Clean Tech"

---

## Phase 1: Foundation & Scaffold
*Goal: Initialize the project, set up the architecture, and define the design system.*

- [ ] **Project Setup**
    - [ ] Create new Android Studio Project (Empty Activity - Compose).
    - [ ] Configure `build.gradle.kts`:
        - [ ] Add `Room` dependencies (KSP, Runtime).
        - [ ] Add `DataStore` (Preferences) dependency.
        - [ ] Add `Navigation Compose` dependency.
        - [ ] Add `Extended Icons` (Material) dependency.
    - [ ] Enable Edge-to-Edge support in `MainActivity`.
- [ ] **Theming (The Design System)**
    - [ ] `Color.kt`: Define `AppColors` palette (Off-whites, Sunday Yellow, Ink Black).
    - [ ] `Type.kt`: Configure `Inter` font family.
        - [ ] **Crucial:** Enable `FontFeature.TabularNumbers` (`tnum`) for Hero typography.
    - [ ] `Theme.kt`: Set up `MaterialTheme` wrapper, forcing Light Mode (or custom Dark Mode handling).
- [ ] **Navigation**
    - [ ] Create `NavRoutes` sealed class/object (`Setup`, `Dashboard`, `AddExpense`).
    - [ ] Set up basic `NavHost` in `MainActivity`.

---

## Phase 2: Data Layer (Room & DataStore)
*Goal: specific persistence for Transactions and Budget Settings.*

- [ ] **Budget Settings (DataStore)**
    - [ ] Create `BudgetPreferences` repository.
    - [ ] Define keys: `TOTAL_BUDGET` (Double), `CYCLE_START_DATE` (Long/String), `CYCLE_DURATION_DAYS` (Int).
    - [ ] Implement `read` (Flow) and `write` (suspend) functions.
- [ ] **Transactions (Room)**
    - [ ] Create `Transaction` Entity (`id`, `amount`, `note`, `timestamp`).
    - [ ] Create `TransactionDao`:
        - [ ] `insert(transaction)`
        - [ ] `delete(transaction)`
        - [ ] `getAll()` (Return `Flow<List<Transaction>>`).
        - [ ] `getTransactionsForDateRange(start, end)` (Optional optimization).
    - [ ] Setup `AppDatabase` and provide instance.
- [ ] **Repository Layer**
    - [ ] Create `BudgetRepository`: Combine DataStore and DAO flows into a single source of truth for the ViewModel.

---

## Phase 3: Domain Logic (The Brain)
*Goal: Implement the dynamic recalculation engine.*

- [ ] **MainViewModel Setup**
    - [ ] Create `MainViewModel` (inject Repository).
    - [ ] Define `BudgetUiState` data class:
        - [ ] `isLoading`: Boolean
        - [ ] `needsSetup`: Boolean (if budget not set)
        - [ ] `currencySymbol`: String
        - [ ] `availableToday`: Double
        - [ ] `dailyLimitBaseline`: Double
        - [ ] `daysRemaining`: Int
        - [ ] `status`: Enum (ON_TRACK, CAREFUL, OVER_LIMIT)
        - [ ] `transactions`: List<Transaction>
- [ ] **Calculation Logic (Reactive)**
    - [ ] Implement `combine` logic on Flow collection:
        - [ ] **Input:** `totalBudget`, `startDate`, `duration`, `transactionList`.
        - [ ] **Logic:**
            1. Calculate `spentBeforeToday`.
            2. Calculate `remainingPool`.
            3. Calculate `newDailyLimit` (`pool / daysRemaining`).
            4. Calculate `availableToday` (`newDailyLimit - spentToday`).
    - [ ] Handle **Edge Cases**:
        - [ ] `daysRemaining <= 0` (End of cycle).
        - [ ] `remainingPool <= 0` (Bankruptcy).

---

## Phase 4: UI Implementation (Composables)
*Goal: Build the screens according to DESIGN.md.*

- [ ] **Onboarding / Setup Screen**
    - [ ] Create layout: Title, Amount Input, Days Input, "Start" Button.
    - [ ] Validation: Ensure numbers are positive.
    - [ ] Action: Save to DataStore -> Navigate to Dashboard.
- [ ] **Dashboard Screen (Main)**
    - [ ] **Hero Card:**
        - [ ] Display "Available Today".
        - [ ] Status Pill (Dynamic color).
    - [ ] **Stats Grid:**
        - [ ] Row with two cards: "New Limit" and "Days Left".
    - [ ] **Transaction List:**
        - [ ] `LazyColumn` implementation.
        - [ ] `TransactionItem` composable (Card style, Row layout).
        - [ ] Empty State view (Minimalist icon).
    - [ ] **FAB:**
        - [ ] "Add" icon.
        - [ ] Positioned bottom right (with scaffold padding).
- [ ] **Add Expense Interface**
    - [ ] Decide: BottomSheet or Full Screen Dialog? (Recommendation: `ModalBottomSheet`).
    - [ ] Numeric Keypad input focus (Auto-open keyboard).
    - [ ] Save Action: Insert to Room.

---

## Phase 5: Polish & Motion (The "Sunday" Feel)
*Goal: Replace static transitions with physics-based motion.*

- [ ] **Rolling Numbers**
    - [ ] Create `RollingNumberText` composable using `Animatable` and `SideEffect`.
    - [ ] Apply to Hero Amount and Daily Limit text.
- [ ] **Entrance Animations**
    - [ ] Apply `AnimatedVisibility` (Slide Up + Fade) to Dashboard components.
    - [ ] Stagger the delays (Hero -> Stats -> List).
- [ ] **Interactive Physics**
    - [ ] Add `scale` animation on FAB press (`Modifier.clickable` interaction source).
    - [ ] Add `spring` spec to BottomSheet open/close.
- [ ] **Haptics**
    - [ ] Add `LocalHapticFeedback` triggers on:
        - [ ] Button Taps.
        - [ ] Rolling number completion (subtle).
- [ ] **Refinements**
    - [ ] Adjust Shadows: Use `ambientColor` and `spotColor` for soft diffusion.
    - [ ] Input Fields: Remove underline, use custom background shapes.

---

## Phase 6: Release Readiness
*Goal: Prepare for deployment.*

- [ ] **App Icon:** Design adaptive icon (Background: Yellow, Foreground: Abstract shape or "M").
- [ ] **Splash Screen:** Implement Android 12+ Splash API.
- [ ] **Testing:**
    - [ ] Test Date rollover (Manually change device time).
    - [ ] Test "Bankruptcy" state (Spend all money).
    - [ ] Test configuration changes (Dark mode toggle, Rotation).
- [ ] **Build:** Generate Signed APK/AAB.

---

## 🐞 Known Android Issues to Watch
1.  **Soft Keyboard handling:** Ensure `WindowInsets.ime` padding is applied so the "Save" button in the Add Expense sheet isn't covered by the keyboard.
2.  **Date Desugaring:** If supporting Android < 8.0 (API 26), enable Java 8 library desugaring in Gradle to use `java.time`.
3.  **System Bar Colors:** Ensure Status Bar icons switch to Dark/Light correctly depending on the background color.
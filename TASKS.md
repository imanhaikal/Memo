# Settings Menu Implementation Tasks

## Overview
This document outlines the steps to replace the "Reset" button on the Dashboard with a full "Settings" menu. The Settings page will allow users to modify their budget parameters and reset the application data.

## Phase 1: Logic & ViewModel Updates
- [x] **Extend MainViewModel**
    - [x] Create a function `updateBudgetSettings(newAmount: Double, newDays: Int)` that updates the budget configuration without resetting the cycle start date or clearing transactions.
    - [x] Ensure the existing `resetBudget()` function is available for the "Hard Reset" functionality.

## Phase 2: UI - Settings Screen
- [x] **Create `SettingsScreen.kt`**
    - [x] Design a clean layout consistent with `DashboardScreen`.
    - [x] **Header**: Title "Settings" and a "Back" or "Close" button.
    - [x] **Configuration Section**:
        - [x] Input field for "Total Budget".
        - [x] Input field for "Cycle Length (Days)".
        - [x] "Save Changes" button (active only when changes are made).
    - [x] **Danger Zone**:
        - [x] "Reset All Data" button (distinct style, e.g., red text or warning icon).
        - [x] Confirmation dialog for Reset action to prevent accidental data loss.

## Phase 3: Navigation & Integration
- [x] **Update `MemoApp.kt` Navigation**
    - [x] Add a local state `var showSettings by remember { mutableStateOf(false) }`.
    - [x] Modify the composition logic to show `SettingsScreen` when `showSettings` is true.
    - [x] Pass navigation callbacks:
        - `onOpenSettings`: Sets `showSettings = true`.
        - `onBack`: Sets `showSettings = false`.
- [x] **Update `DashboardScreen.kt`**
    - [x] Replace the "Reset" `TextButton` in the header with a "Settings" button.
    - [x] Connect the button to the `onOpenSettings` callback.

## Phase 4: Verification
- [x] **Test Navigation**: Verify transitioning between Dashboard and Settings.
- [x] **Test Editing**:
    - [x] Change Budget Amount -> Verify updates on Dashboard immediately.
    - [x] Change Cycle Length -> Verify "Days Remaining" and "Daily Limit" update correctly.
- [x] **Test Reset**:
    - [x] Use "Reset" in Settings -> Verify app returns to "Setup" state and data is cleared.
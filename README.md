# Memo Budget

**Memo Budget** is a native Android expense tracking application built with Kotlin and Jetpack Compose. It treats your budget as a fluid pool of funds, dynamically recalculating your **Daily Spending Limit** in real-time. This provides immediate, actionable feedback: spending less today increases your future daily limits, while overspending decreases them to ensure you stay on track.

The app features a "Premium Hardware" / "Clean Tech" design aesthetic, focusing on clarity, tactility, and the "One Big Number."

## 🚀 Features

*   **Dynamic Budget Engine**: Automatically recalculates daily allowances based on current spending and remaining days.
*   **Fluid Logic**: Spending less today automatically increases tomorrow's limit.
*   **Real-time Status**: Visual indicators for "On Track" (Green), "Careful" (Gray), and "Over Limit" (Red) states.
*   **Multiple Budgets**: Run "Travel" alongside "Monthly", each with its own amount, cycle length and currency.
*   **Income & Refunds**: A refund puts money back in the pool, so the daily limit recovers instead of punishing you for it.
*   **Cycle History**: Finished cycles are archived with their totals rather than quietly disappearing.
*   **Category Limits**: Optional per-category caps, shown against spending on the dashboard.
*   **Recurring Expenses**: Rent and subscriptions post themselves on their due date, whether or not the app is opened.
*   **Notifications**: Daily limit, over-limit, cycle summary and recurring-posted — all off by default.
*   **Search**: Text and category filters over your history.
*   **Home-screen Widget**: "Available today" plus one-tap add, without opening the app.
*   **Backup & Restore**: A plain, schema-versioned JSON file you save wherever you like.
*   **Persistent Storage**: **Room Database** (transactions, budgets, cycles, caps, recurring rules) with **DataStore** for device settings.
*   **Premium Motion & Feel**:
    *   Physics-based rolling numbers.
    *   Tactile spring animations (FAB, Dialogs).
    *   Haptic feedback on interactions.
    *   Staggered entrance animations and smooth transitions.
*   **Privacy Focused**: All data is stored locally on the device, and the database is deliberately excluded from Android's cloud backup. Backups are user-driven — nothing leaves the device on its own.

## 🛠 Tech Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material3)
*   **Architecture**: MVVM (Model-View-ViewModel)
*   **Persistence**:
    *   Room Database (Transactions)
    *   Jetpack DataStore (Budget Settings)
*   **Background**: WorkManager (recurring expenses, notifications)
*   **Widget**: Glance
*   **Build System**: Gradle (Kotlin DSL)
*   **Testing**: JUnit, Mockk, Turbine (Unit Tests), Espresso/Compose Test (UI Tests)

## 🏗 Architecture

The application follows the recommended Android Architecture guidelines:

*   **UI Layer**: `ui/screens` and `ui/components` using Jetpack Compose.
*   **ViewModel Layer**: `MainViewModel` manages UI state (`BudgetUiState`) and executes the core budgeting algorithm.
*   **Data Layer**:
    *   `TransactionDao`: Interface for Room Database operations.
    *   `BudgetPreferences`: Wrapper for DataStore operations.

## 💻 Setup & Installation

To build and run this project locally:

1.  **Prerequisites**:
    *   Android Studio Ladybug or newer.
    *   JDK 17 (Required by AGP 8.13+).

2.  **Clone the Repository**:
    ```bash
    git clone https://github.com/yourusername/memo-budget.git
    cd memo-budget
    ```

3.  **Open in Android Studio**:
    *   Open Android Studio and select "Open".
    *   Navigate to the cloned directory and select it.
    *   Ensure Gradle Sync completes successfully.

4.  **Run Tests**:
    *   Run Unit Tests: `gradlew testDebugUnitTest`
    *   Run UI/DB Tests: `gradlew connectedAndroidTest` (Requires connected device/emulator).

5.  **Build and Run**:
    *   Select a connected device or emulator (API Level 26+ recommended).
    *   Click the **Run** button (Green Play Icon).

## 📂 Project Structure

```text
c:/AndroidProjects/Memo
├── app/
│   ├── src/main/java/com/imanhaikal/memo/
│   │   ├── data/           # Room Entity, DAO, Database, Preferences
│   │   ├── ui/             # Composable screens, components, viewmodels
│   │   ├── ui/theme/       # Color, Type, Theme definitions
│   │   └── MemoApplication.kt # Manual DI Container
│   ├── src/test/           # Unit tests (MainViewModelTest)
│   └── src/androidTest/    # Instrumented UI tests (Phase5PolishTest)
├── gradle/                 # Gradle configuration and version catalog
├── REQUIREMENTS.md         # Detailed functional requirements
├── DESIGN.md               # UI/UX design specifications
└── TASKS.md                # Project roadmap and task tracking
```

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1.  Fork the repository.
2.  Create a feature branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to the branch (`git push origin feature/AmazingFeature`).
5.  Open a Pull Request.

## 📄 License

[License Information Here - e.g., MIT License]
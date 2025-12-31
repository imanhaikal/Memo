# Memo Budget

**Memo Budget** is a native Android expense tracking application built with Kotlin and Jetpack Compose. It treats your budget as a fluid pool of funds, dynamically recalculating your **Daily Spending Limit** in real-time. This provides immediate, actionable feedback: spending less today increases your future daily limits, while overspending decreases them to ensure you stay on track.

The app features a "Premium Hardware" / "Clean Tech" design aesthetic, focusing on clarity, tactility, and the "One Big Number."

## 🚀 Features

*   **Dynamic Budget Engine**: Automatically recalculates daily allowances based on current spending and remaining days.
*   **Flexible Budget Cycles**: Support for specific date ranges or countdown modes.
*   **Real-time Status**: Visual indicators for "On Track", "Careful", and "Over Limit" states.
*   **Transaction Management**: Quick and easy expense entry.
*   **Privacy Focused**: All data is stored locally on the device.

## 🛠 Tech Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material3)
*   **Architecture**: MVVM (Model-View-ViewModel)
*   **Persistence**: Room Database (Transactions) & Jetpack DataStore (Budget Settings)
*   **Build System**: Gradle (Kotlin DSL)

## 🏗 Architecture

The application follows the recommended Android Architecture guidelines:

*   **UI Layer**: Jetpack Compose for declarative UI.
*   **ViewModel Layer**: Manages UI state and business logic (Budget Calculation Engine).
*   **Data Layer**: Repositories mediating data between Room/DataStore and the UI.

## 💻 Setup & Installation

To build and run this project locally:

1.  **Prerequisites**:
    *   Android Studio Ladybug or newer.
    *   JDK 11 or newer.

2.  **Clone the Repository**:
    ```bash
    git clone https://github.com/yourusername/memo-budget.git
    cd memo-budget
    ```

3.  **Open in Android Studio**:
    *   Open Android Studio and select "Open".
    *   Navigate to the cloned directory and select it.

4.  **Build and Run**:
    *   Wait for Gradle synchronization to complete.
    *   Select a connected device or emulator (API Level 26+).
    *   Click the **Run** button (Green Play Icon).

## 📂 Project Structure

```text
c:/AndroidProjects/Memo
├── app/
│   ├── src/main/java/com/imanhaikal/memo/
│   │   ├── ui/             # Composable screens and theme
│   │   ├── MainActivity.kt # Entry point
│   │   └── ...
│   └── src/main/res/       # Static resources
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
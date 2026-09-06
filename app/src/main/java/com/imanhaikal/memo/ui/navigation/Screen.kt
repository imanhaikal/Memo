package com.imanhaikal.memo.ui.navigation

/**
 * Every destination in the app.
 *
 * [depth] drives the shared-axis transition direction: pushing to a deeper screen slides
 * forward, popping slides back. Before v5 this was a single `showSettings` boolean, which
 * could express two screens and one direction.
 */
sealed interface Screen {
    val depth: Int

    data object Dashboard : Screen {
        override val depth = 0
    }

    data object Settings : Screen {
        override val depth = 1
    }

    data object Budgets : Screen {
        override val depth = 2
    }

    data object CycleHistory : Screen {
        override val depth = 2
    }

    data object CategoryCaps : Screen {
        override val depth = 2
    }

    data object Recurring : Screen {
        override val depth = 2
    }

    data object Search : Screen {
        override val depth = 2
    }

    /** Serialized token used by the back stack's [androidx.compose.runtime.saveable.Saver]. */
    val token: String
        get() = when (this) {
            Dashboard -> "dashboard"
            Settings -> "settings"
            Budgets -> "budgets"
            CycleHistory -> "cycleHistory"
            CategoryCaps -> "categoryCaps"
            Recurring -> "recurring"
            Search -> "search"
        }

    companion object {
        fun fromToken(token: String): Screen? = when (token) {
            "dashboard" -> Dashboard
            "settings" -> Settings
            "budgets" -> Budgets
            "cycleHistory" -> CycleHistory
            "categoryCaps" -> CategoryCaps
            "recurring" -> Recurring
            "search" -> Search
            else -> null
        }
    }
}

package com.imanhaikal.memo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imanhaikal.memo.data.ThemeMode
import com.imanhaikal.memo.ui.MainViewModel
import com.imanhaikal.memo.ui.theme.MemoTheme
import com.imanhaikal.memo.ui.MemoApp
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    /**
     * Set when the widget or launcher shortcut asked for the add-expense dialog.
     *
     * A flow rather than a one-off read because the activity is `singleTop`: a second tap
     * on an already-open app arrives at [onNewIntent], and without this it would do
     * nothing at all.
     */
    private val quickAddRequests = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the splash until the first real UI state is ready, so the app
        // never flashes a blank frame between launch and the dashboard entrance
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value.isLoading }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleQuickAdd(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            MemoTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            ) {
                MemoApp(
                    viewModel = viewModel,
                    quickAddRequests = quickAddRequests
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickAdd(intent)
    }

    private fun handleQuickAdd(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_QUICK_ADD, false) == true) {
            quickAddRequests.value = true
            // Consumed, so a configuration change doesn't reopen the dialog.
            intent.removeExtra(EXTRA_QUICK_ADD)
        }
    }

    companion object {
        const val EXTRA_QUICK_ADD = "com.imanhaikal.memo.extra.QUICK_ADD"
    }
}

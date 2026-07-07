package com.imanhaikal.memo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.imanhaikal.memo.ui.MainViewModel
import com.imanhaikal.memo.ui.theme.MemoTheme
import com.imanhaikal.memo.ui.MemoApp

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the splash until the first real UI state is ready, so the app
        // never flashes a blank frame between launch and the dashboard entrance
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value.isLoading }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemoTheme {
                MemoApp(viewModel = viewModel)
            }
        }
    }
}
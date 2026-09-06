package com.imanhaikal.memo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * A minimal navigation stack.
 *
 * Deliberately not Navigation Compose: the predictive-back gesture in [MemoNavHost] feeds
 * a per-frame progress fraction into a `graphicsLayer` on the outgoing screen, which a
 * NavHost's own transition handling does not expose. The app also has a single ViewModel,
 * so per-destination scoping — the main thing a NavHost buys — would go unused.
 */
@Stable
class MemoBackStack(initial: List<Screen> = listOf(Screen.Dashboard)) {

    var entries: List<Screen> by mutableStateOf(initial.ifEmpty { listOf(Screen.Dashboard) })
        private set

    val current: Screen get() = entries.last()

    val canGoBack: Boolean get() = entries.size > 1

    /** Re-selecting the screen already on top is a no-op rather than a duplicate entry. */
    fun push(screen: Screen) {
        if (current == screen) return
        entries = entries + screen
    }

    fun pop() {
        if (canGoBack) entries = entries.dropLast(1)
    }

    fun popToRoot() {
        entries = listOf(Screen.Dashboard)
    }

    companion object {
        val Saver: Saver<MemoBackStack, Any> = listSaver(
            save = { stack -> stack.entries.map { it.token } },
            restore = { tokens ->
                MemoBackStack(tokens.mapNotNull { Screen.fromToken(it as String) })
            }
        )
    }
}

@Composable
fun rememberMemoBackStack(): MemoBackStack =
    rememberSaveable(saver = MemoBackStack.Saver) { MemoBackStack() }

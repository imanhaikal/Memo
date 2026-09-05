package com.imanhaikal.memo.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Hosts the screen area: a shared-axis-X transition between back-stack entries, with the
 * system back gesture following the finger so it can be abandoned mid-swipe.
 *
 * Lifted wholesale from MemoApp's Dashboard/Settings switch. The one behavioural change is
 * that slide direction now comes from [Screen.depth] rather than a boolean, so it stays
 * correct with more than two destinations.
 */
@Composable
fun MemoNavHost(
    backStack: MemoBackStack,
    modifier: Modifier = Modifier,
    content: @Composable (screen: Screen, modifier: Modifier) -> Unit
) {
    val scope = rememberCoroutineScope()
    val backProgress = remember { Animatable(0f) }

    PredictiveBackHandler(enabled = backStack.canGoBack) { progress ->
        try {
            progress.collect { backProgress.snapTo(it.progress) }
            backStack.pop()
            // Resolve alongside the outgoing slide rather than snapping
            backProgress.animateTo(0f, tween(SCREEN_NAV_MS))
        } catch (_: CancellationException) {
            // An abandoned gesture cancels this handler's own job, so the settle has to
            // run somewhere that outlives it — but still dies with the screen. Suspending
            // here would throw immediately and strand the screen at its half-swiped
            // scale and offset.
            scope.launch {
                backProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    AnimatedContent(
        targetState = backStack.current,
        modifier = modifier,
        transitionSpec = {
            val duration = SCREEN_NAV_MS
            val distance = 60
            val goingDeeper = targetState.depth > initialState.depth
            if (goingDeeper) {
                (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { distance } +
                    fadeIn(tween(duration))) togetherWith
                    (slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -distance } +
                        fadeOut(tween(duration)))
            } else {
                (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -distance } +
                    fadeIn(tween(duration))) togetherWith
                    (slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { distance } +
                        fadeOut(tween(duration)))
            }
        },
        label = "screenNav"
    ) { screen ->
        val screenModifier = if (screen == Screen.Dashboard) {
            Modifier
        } else {
            // Shrink toward the trailing edge as the gesture progresses, the way the
            // platform moves a screen that is about to be popped.
            Modifier.graphicsLayer {
                val p = backProgress.value
                val s = 1f - BACK_SCALE_TRAVEL * p
                scaleX = s
                scaleY = s
                translationX = size.width * BACK_SLIDE_FRACTION * p
                alpha = 1f - 0.25f * p
            }
        }
        content(screen, screenModifier)
    }
}

internal const val SCREEN_NAV_MS = 300

/** How far the outgoing screen shrinks at full back-gesture progress. */
private const val BACK_SCALE_TRAVEL = 0.12f

/** How far it drifts toward the trailing edge, as a fraction of screen width. */
private const val BACK_SLIDE_FRACTION = 0.08f

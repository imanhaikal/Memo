package com.imanhaikal.memo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.theme.AppColors

/**
 * Placeholder shown while the first budget state loads. Mirrors the real dashboard's
 * silhouettes — hero, stat pair, progress, rows — so the layout settles in place rather
 * than assembling itself once data lands, and so a slow load never shows a bare screen.
 */
@Composable
fun DashboardSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val shimmer = rememberInfiniteTransition(label = "skeleton")
    val alpha by shimmer.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 24.dp)
            // One announcement for the whole placeholder, not eight anonymous boxes
            .semantics { contentDescription = "Loading your budget" },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SkeletonBlock(height = 176.dp, alpha = alpha, cornerRadius = 32.dp)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(Modifier.weight(1f), height = 92.dp, alpha = alpha)
            SkeletonBlock(Modifier.weight(1f), height = 92.dp, alpha = alpha)
        }

        SkeletonBlock(height = 116.dp, alpha = alpha)

        repeat(3) {
            SkeletonBlock(height = 76.dp, alpha = alpha, cornerRadius = 16.dp)
        }
    }
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp,
    alpha: Float,
    cornerRadius: Dp = 24.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(cornerRadius))
            .background(AppColors.Field)
    )
}

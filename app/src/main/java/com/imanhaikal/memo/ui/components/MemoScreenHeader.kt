package com.imanhaikal.memo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imanhaikal.memo.ui.theme.AppColors

/**
 * The back-arrow-and-title row every secondary screen opens with.
 *
 * Five screens had their own byte-identical copy of this, and the bottom padding had
 * already drifted between them — hence [bottomPadding] rather than a single fixed value.
 * [trailing] carries the optional action on the far side (e.g. "New budget").
 */
@Composable
fun MemoScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 12.dp,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 24.dp, top = 16.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Weighted so a long title cannot push [trailing] off the edge at large font
        // scales; fill = false keeps short titles sitting against the back arrow.
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemoIconButton(
                onClick = onBack,
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back"
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke()
    }
}

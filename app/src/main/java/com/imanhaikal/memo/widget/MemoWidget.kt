package com.imanhaikal.memo.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent
import androidx.glance.color.ColorProvider as dayNightColor
import androidx.glance.unit.ColorProvider
import com.imanhaikal.memo.MainActivity
import com.imanhaikal.memo.data.widget.WidgetSnapshot
import com.imanhaikal.memo.data.widget.WidgetSnapshotRepository
import com.imanhaikal.memo.ui.BudgetStatus
import com.imanhaikal.memo.utils.CurrencyUtils

/**
 * "Available today" on the home screen.
 *
 * Reads only the snapshot written by the app — never Room, never a ViewModel. Glance can
 * recompose in a cold process, and a widget is not the place to be opening a database.
 */
class MemoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotRepository(context).read()
        provideContent {
            GlanceTheme {
                WidgetContent(context, snapshot)
            }
        }
    }
}

@Composable
private fun WidgetContent(context: Context, snapshot: WidgetSnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.Surface)
            .cornerRadius(24.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        if (!snapshot.hasBudget) {
            // A widget added before the app has ever run has nothing real to show;
            // saying so beats rendering a convincing but meaningless zero.
            Text(
                text = "Open Memo to set up a budget",
                style = TextStyle(
                    color = WidgetColors.TextSecondary,
                    fontSize = 14.sp
                )
            )
            return@Column
        }

        Text(
            text = "AVAILABLE TODAY",
            style = TextStyle(
                color = WidgetColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = CurrencyUtils.formatCurrency(snapshot.availableTodayCents, snapshot.currencyCode),
            style = TextStyle(
                color = statusColor(snapshot.status),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "${CurrencyUtils.formatCurrency(snapshot.dailyLimitCents, snapshot.currencyCode)} / day",
                style = TextStyle(color = WidgetColors.TextSecondary, fontSize = 12.sp)
            )
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        Text(
            text = "+  Add expense",
            style = TextStyle(
                color = WidgetColors.OnAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier
                .background(WidgetColors.Accent)
                .cornerRadius(50.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                // singleTop + onNewIntent means a second tap on an already-open app
                // still opens the dialog rather than doing nothing.
                .clickable(actionStartActivityIntent(quickAddIntent(context)))
        )
    }
}

private fun quickAddIntent(context: Context) =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_QUICK_ADD, true)
    }

private fun statusColor(status: BudgetStatus): ColorProvider =
    when (status) {
        BudgetStatus.OVER_LIMIT -> WidgetColors.Red
        BudgetStatus.CAREFUL -> WidgetColors.TextPrimary
        BudgetStatus.ON_TRACK -> WidgetColors.TextPrimary
    }

class MemoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MemoWidget()
}

/**
 * The palette, re-declared.
 *
 * `AppColors` is a Compose-runtime object the widget process cannot read, and Glance's
 * ColorProvider is a different type from Compose's Color, so there is no bridge to share.
 * These values must be kept in step with ui/theme/Color.kt by hand.
 */
private object WidgetColors {
    val Surface = dayNightColor(
        day = Color(0xFFFFFFFF),
        night = Color(0xFF1A1A1A)
    )
    val TextPrimary = dayNightColor(
        day = Color(0xFF111111),
        night = Color(0xFFEEEEEE)
    )
    val TextSecondary = dayNightColor(
        day = Color(0xFF666666),
        night = Color(0xFFAAAAAA)
    )
    val Red = dayNightColor(
        day = Color(0xFFC91F1F),
        night = Color(0xFFE86A6A)
    )
    val Accent = dayNightColor(
        day = Color(0xFFF2E057),
        night = Color(0xFFF2E057)
    )
    val OnAccent = dayNightColor(
        day = Color(0xFF111111),
        night = Color(0xFF111111)
    )
}

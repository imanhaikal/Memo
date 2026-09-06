package com.imanhaikal.memo.notifications

import com.imanhaikal.memo.utils.CurrencyUtils

/** Title and body for one notification. */
data class NotificationText(val title: String, val body: String)

/**
 * Notification wording, kept as pure functions so it can be unit tested without a device
 * and without touching NotificationManager.
 */
object NotificationContent {

    fun dailyReminder(availableTodayCents: Long, currencyCode: String): NotificationText {
        val amount = CurrencyUtils.formatCurrency(availableTodayCents, currencyCode)
        return if (availableTodayCents < 0) {
            NotificationText(
                title = "You're over for today",
                body = "$amount left. Spending nothing today puts you back on track."
            )
        } else {
            NotificationText(
                title = "$amount to spend today",
                body = "That's your allowance for the rest of the day."
            )
        }
    }

    fun overLimit(availableTodayCents: Long, currencyCode: String): NotificationText {
        // availableToday is negative here; the message reads better with the magnitude.
        val over = CurrencyUtils.formatCurrency(-availableTodayCents, currencyCode)
        return NotificationText(
            title = "Over today's limit",
            body = "You're $over past today's allowance. Tomorrow's limit adjusts to suit."
        )
    }

    fun cycleEnd(
        spentCents: Long,
        budgetCents: Long,
        currencyCode: String
    ): NotificationText {
        val leftover = budgetCents - spentCents
        val spent = CurrencyUtils.formatCurrency(spentCents, currencyCode)
        return if (leftover >= 0) {
            NotificationText(
                title = "Cycle finished — ${CurrencyUtils.formatCurrency(leftover, currencyCode)} under",
                body = "You spent $spent. A new cycle has started."
            )
        } else {
            NotificationText(
                title = "Cycle finished — ${CurrencyUtils.formatCurrency(-leftover, currencyCode)} over",
                body = "You spent $spent. A new cycle has started."
            )
        }
    }

    fun recurringPosted(count: Int, totalCents: Long, currencyCode: String): NotificationText {
        val amount = CurrencyUtils.formatCurrency(totalCents, currencyCode)
        return if (count == 1) {
            NotificationText(
                title = "Recurring expense added",
                body = "$amount was recorded automatically."
            )
        } else {
            NotificationText(
                title = "$count recurring expenses added",
                body = "$amount was recorded automatically."
            )
        }
    }
}

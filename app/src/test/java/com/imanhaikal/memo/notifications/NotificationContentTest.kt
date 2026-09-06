package com.imanhaikal.memo.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentTest {

    @Test
    fun `the daily reminder leads with what is left to spend`() {
        val text = NotificationContent.dailyReminder(2_500L, "MYR")

        assertTrue(text.title.contains("25"))
        assertFalse(text.title.contains("-"))
    }

    @Test
    fun `a negative balance reads as over rather than a minus amount in the title`() {
        val text = NotificationContent.dailyReminder(-2_500L, "MYR")

        assertTrue(text.title.contains("over", ignoreCase = true))
    }

    @Test
    fun `the over-limit alert states the amount as a positive overspend`() {
        val text = NotificationContent.overLimit(-3_000L, "MYR")

        // The caller passes a negative availableToday; the wording should not show it.
        assertFalse(text.body.contains("-"))
        assertTrue(text.body.contains("30"))
    }

    @Test
    fun `the cycle summary distinguishes coming in under from going over`() {
        val under = NotificationContent.cycleEnd(spentCents = 8_000L, budgetCents = 10_000L, currencyCode = "MYR")
        val over = NotificationContent.cycleEnd(spentCents = 12_000L, budgetCents = 10_000L, currencyCode = "MYR")

        assertTrue(under.title.contains("under", ignoreCase = true))
        assertTrue(over.title.contains("over", ignoreCase = true))
        assertFalse(over.title.contains("-"))
    }

    @Test
    fun `recurring wording is singular for one and plural for several`() {
        val one = NotificationContent.recurringPosted(1, 1_000L, "MYR")
        val many = NotificationContent.recurringPosted(3, 3_000L, "MYR")

        assertTrue(one.title.contains("expense"))
        assertFalse(one.title.contains("expenses"))
        assertTrue(many.title.contains("3 recurring expenses"))
    }
}

package com.feniqo.mobile.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionPaymentReminderStartupInitializerTest {

    private class FakeSubscriptionPaymentReminderWorkScheduler : SubscriptionPaymentReminderWorkScheduler {
        var scheduleCallCount = 0
        var cancelCallCount = 0

        override fun schedulePeriodicReminders() {
            scheduleCallCount++
        }

        override fun cancelPeriodicReminders() {
            cancelCallCount++
        }
    }

    @Test
    fun `onAppCreate invokes schedulePeriodicReminders exactly once`() {
        val fakeScheduler = FakeSubscriptionPaymentReminderWorkScheduler()
        val initializer = SubscriptionPaymentReminderStartupInitializer(fakeScheduler)

        assertEquals(0, fakeScheduler.scheduleCallCount)

        initializer.onAppCreate()

        assertEquals(1, fakeScheduler.scheduleCallCount)
        assertEquals(0, fakeScheduler.cancelCallCount)
    }
}

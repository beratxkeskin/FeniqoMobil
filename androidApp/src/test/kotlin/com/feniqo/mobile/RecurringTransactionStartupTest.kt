package com.feniqo.mobile

import com.feniqo.mobile.sync.RecurringTransactionStartupInitializer
import com.feniqo.mobile.sync.RecurringTransactionWorkScheduler
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurringTransactionStartupTest {

    private class FakeRecurringWorkScheduler : RecurringTransactionWorkScheduler {
        var scheduleCallCount = 0
        var cancelCallCount = 0

        override fun scheduleRecurringCheck() {
            scheduleCallCount++
        }

        override fun cancelRecurringWork() {
            cancelCallCount++
        }
    }

    @Test
    fun `onAppCreate triggers scheduleRecurringCheck exactly once`() {
        val fakeScheduler = FakeRecurringWorkScheduler()
        val initializer = RecurringTransactionStartupInitializer(fakeScheduler)

        initializer.onAppCreate()

        assertEquals(1, fakeScheduler.scheduleCallCount)
        assertEquals(0, fakeScheduler.cancelCallCount)
    }
}

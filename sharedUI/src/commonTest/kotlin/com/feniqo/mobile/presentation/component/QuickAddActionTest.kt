package com.feniqo.mobile.presentation.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickAddActionTest {
    @Test
    fun onlyTransfer_remainsUnavailableUntilItsDomainFlowExists() {
        val unavailable = QuickAddAction.entries.filterNot { it.available }
        assertEquals(listOf(QuickAddAction.TRANSFER), unavailable)
        assertTrue(QuickAddAction.EXPENSE.available)
        assertTrue(QuickAddAction.INCOME.available)
        assertTrue(QuickAddAction.DEBT_RECEIVABLE.available)
        assertTrue(QuickAddAction.RECURRING_TRANSACTION.available)
        assertFalse(QuickAddAction.TRANSFER.available)
    }
}

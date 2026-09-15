package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.presentation.screen.transactionStatusText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TransactionSyncStatusTextTest {
    @Test
    fun onlyAcknowledgedRecordsAreShownAsSynced() {
        assertEquals("Senkronize edildi", SyncStatus.SYNCED.transactionStatusText())
        SyncStatus.entries.filter { it != SyncStatus.SYNCED }.forEach {
            assertNotEquals("Senkronize edildi", it.transactionStatusText())
        }
        assertNotEquals("Senkronize edildi", null.transactionStatusText())
        assertEquals("Senkronizasyon bekleniyor", SyncStatus.PENDING_CREATE.transactionStatusText())
    }
}

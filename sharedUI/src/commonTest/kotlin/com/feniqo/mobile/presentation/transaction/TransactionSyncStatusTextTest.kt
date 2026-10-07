@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.SyncStatus
import feniqomobil.sharedui.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TransactionSyncStatusTextTest {
    @Test
    fun onlyAcknowledgedRecordsAreShownAsSynced() {
        assertEquals(Res.string.transaction_detail_sync_synced, SyncStatus.SYNCED.toTransactionStatusStringResource())
        SyncStatus.entries.filter { it != SyncStatus.SYNCED }.forEach {
            assertNotEquals(Res.string.transaction_detail_sync_synced, it.toTransactionStatusStringResource())
        }
        assertNotEquals(Res.string.transaction_detail_sync_synced, null.toTransactionStatusStringResource())
        assertEquals(
            Res.string.transaction_detail_sync_pending,
            SyncStatus.PENDING_CREATE.toTransactionStatusStringResource(),
        )
    }
}

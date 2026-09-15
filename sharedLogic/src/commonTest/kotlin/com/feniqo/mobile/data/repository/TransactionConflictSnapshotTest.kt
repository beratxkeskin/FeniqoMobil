package com.feniqo.mobile.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransactionConflictSnapshotTest {
    @Test
    fun storedSnapshotIsMappedWithoutLeakingPayloadToUi() {
        val payload = """{"id":"transaction","user_id":"user","amount_minor":12345,"currency":"TRY","type":"expense","category_id":"category","payment_method":"cash","transaction_date":"2026-09-14","created_at":"2026-09-14T09:00:00Z","description":"Market","note":"Not"}"""
        val snapshot = decodeTransactionConflictSnapshot("TRANSACTION", payload)
        assertEquals(12345L, snapshot?.amount?.amountMinor)
        assertEquals("Not", snapshot?.note)
        assertNull(decodeTransactionConflictSnapshot("CATEGORY", payload))
        assertNull(decodeTransactionConflictSnapshot("TRANSACTION", "{}"))
        assertNull(decodeTransactionConflictSnapshot("TRANSACTION", payload.replace("12345", "-1")))
    }
}

package com.feniqo.mobile.data.backup

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BackupImportPlannerTest {
    @Test
    fun plan_remapsIdsAndNeverRestoresOwnerReceiptOrWorkspaceData() {
        var sequence = 0
        val planner = BackupImportPlanner(EntityIdGenerator { EntityId("new-${++sequence}") })
        val backup = FeniqoBackupV1(
            createdAt = "2026-09-10T12:00:00Z",
            categories = listOf(BackupCategoryV1("old-cat", "Market", "EXPENSE", "#112233", "cart")),
            transactions = listOf(
                BackupTransactionV1(
                    "old-tx", 100, "TRY", "EXPENSE", "old-cat", null, "CASH",
                    "2026-09-10", "2026-09-10T10:00:00Z", BackupInstallmentV1("old-group", 1, 2),
                ),
                BackupTransactionV1(
                    "old-tx-2", 200, "TRY", "EXPENSE", "old-cat", null, "CASH",
                    "2026-09-10", "2026-09-10T10:00:00Z", BackupInstallmentV1("old-group", 2, 2),
                ),
            ),
        )

        val plan = planner.plan(backup, EntityId("current-owner"))

        assertNotEquals("old-cat", plan.categories.single().id.value)
        assertEquals(plan.categories.single().id, plan.transactions.first().categoryId)
        assertEquals(plan.transactions[0].installment?.groupId, plan.transactions[1].installment?.groupId)
        assertEquals(setOf("current-owner"), plan.transactions.map { it.ownerId.value }.toSet())
        assertNull(plan.transactions.first().workspaceId)
        assertNull(plan.transactions.first().receiptPath)
    }
}

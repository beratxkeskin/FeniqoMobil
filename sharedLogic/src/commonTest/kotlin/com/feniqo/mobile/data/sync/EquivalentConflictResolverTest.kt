package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EquivalentConflictResolverTest {

    private val json = Json { encodeDefaults = true; explicitNulls = true }

    @Test
    fun transaction_equivalent_when_all_business_fields_match_ignoring_version_and_metadata() {
        val localDto = TransactionDto(
            id = "tx-1",
            userId = "usr-1",
            workspaceId = null,
            amountMinor = 33333,
            currency = "TRY",
            type = "expense",
            categoryId = "cat-1",
            description = "Market Alışverişi ",
            paymentMethod = "credit_card",
            transactionDate = "2026-08-25",
            receiptPath = null,
            installmentNumber = 1,
            totalInstallments = 3,
            installmentGroupId = "grp-1",
            createdAt = "2026-08-25T17:00:00Z",
            version = null,
        )

        val remoteDto = TransactionDto(
            id = "tx-1",
            userId = "usr-1",
            workspaceId = null,
            amountMinor = 33333,
            currency = "try", // lowercase casing should match
            type = "EXPENSE", // uppercase casing should match
            categoryId = "cat-1",
            description = "Market Alışverişi", // trimmed
            paymentMethod = "CREDIT_CARD",
            transactionDate = "2026-08-25",
            receiptPath = null,
            installmentNumber = 1,
            totalInstallments = 3,
            installmentGroupId = "grp-1",
            createdAt = "2026-08-25T17:00:01Z", // server timestamp different
            updatedAt = "2026-08-25T17:00:01Z",
            version = 1L, // server version different
        )

        val conflict = conflictEntity("TRANSACTION", "tx-1", "op-1", localDto, remoteDto)
        val op = operationEntity("TRANSACTION", "tx-1", "op-1", "CREATE")

        assertTrue(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun transaction_not_equivalent_when_amount_differs() {
        val localDto = sampleTx(amountMinor = 10000)
        val remoteDto = sampleTx(amountMinor = 20000)

        val conflict = conflictEntity("TRANSACTION", "tx-1", "op-1", localDto, remoteDto)
        val op = operationEntity("TRANSACTION", "tx-1", "op-1", "CREATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun transaction_not_equivalent_when_receipt_path_differs() {
        val localDto = sampleTx(receiptPath = "receipts/user/receipt1.jpg")
        val remoteDto = sampleTx(receiptPath = "receipts/user/receipt2.jpg")

        val conflict = conflictEntity("TRANSACTION", "tx-1", "op-1", localDto, remoteDto)
        val op = operationEntity("TRANSACTION", "tx-1", "op-1", "CREATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun category_equivalent_positive_and_negative() {
        val localDto = CategoryDto(
            id = "cat-1",
            userId = "usr-1",
            name = "Market ",
            slug = "market",
            type = "expense",
            color = "#ef4444",
            icon = "cart",
            isDefault = false,
            createdAt = "2026-08-25T17:00:00Z",
        )
        val remoteDto = CategoryDto(
            id = "cat-1",
            userId = "usr-1",
            name = "market",
            slug = "market",
            type = "EXPENSE",
            color = "#EF4444",
            icon = "cart",
            isDefault = false,
            createdAt = "2026-08-25T17:00:05Z",
            version = 1L,
        )

        val conflict = conflictEntity("CATEGORY", "cat-1", "op-1", localDto, remoteDto)
        val op = operationEntity("CATEGORY", "cat-1", "op-1", "CREATE")
        assertTrue(EquivalentConflictResolver.isEquivalent(conflict, op))

        // Different name -> not equivalent
        val diffNameConflict = conflictEntity("CATEGORY", "cat-1", "op-1", localDto, remoteDto.copy(name = "Farklı"))
        assertFalse(EquivalentConflictResolver.isEquivalent(diffNameConflict, op))
    }

    @Test
    fun profile_equivalent_positive_and_negative() {
        val localDto = ProfileDto(
            id = "usr-1",
            email = "test@example.com ",
            fullName = "Test User ",
            currency = "try",
            theme = "dark",
            lang = "tr",
            activeWorkspaceId = null,
            createdAt = "2026-08-25T17:00:00Z",
        )
        val remoteDto = ProfileDto(
            id = "usr-1",
            email = "TEST@example.com",
            fullName = "Test User",
            currency = "TRY",
            theme = "DARK",
            lang = "TR",
            activeWorkspaceId = null,
            createdAt = "2026-08-25T17:00:05Z",
            version = 1L,
        )

        val conflict = conflictEntity("PROFILE", "usr-1", "op-1", localDto, remoteDto)
        val op = operationEntity("PROFILE", "usr-1", "op-1", "CREATE")
        assertTrue(EquivalentConflictResolver.isEquivalent(conflict, op))

        // Different email -> not equivalent
        val diffEmailConflict = conflictEntity("PROFILE", "usr-1", "op-1", localDto, remoteDto.copy(email = "other@example.com"))
        assertFalse(EquivalentConflictResolver.isEquivalent(diffEmailConflict, op))
    }

    @Test
    fun non_create_operation_is_never_equivalent() {
        val localDto = sampleTx()
        val remoteDto = sampleTx(version = 2L)

        val conflict = conflictEntity("TRANSACTION", "tx-1", "op-1", localDto, remoteDto)
        val updateOp = operationEntity("TRANSACTION", "tx-1", "op-1", "UPDATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, updateOp))
    }

    @Test
    fun mismatched_operation_id_is_never_equivalent() {
        val localDto = sampleTx()
        val remoteDto = sampleTx(version = 1L)

        val conflict = conflictEntity("TRANSACTION", "tx-1", "op-1", localDto, remoteDto)
        val opWithOtherId = operationEntity("TRANSACTION", "tx-1", "op-2", "CREATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, opWithOtherId))
    }

    private fun sampleTx(
        amountMinor: Long = 10000,
        receiptPath: String? = null,
        version: Long? = null,
    ) = TransactionDto(
        id = "tx-1",
        userId = "usr-1",
        amountMinor = amountMinor,
        currency = "TRY",
        type = "expense",
        categoryId = "cat-1",
        paymentMethod = "credit_card",
        transactionDate = "2026-08-25",
        receiptPath = receiptPath,
        createdAt = "2026-08-25T17:00:00Z",
        version = version,
    )

    private inline fun <reified T : Any> conflictEntity(
        entityType: String,
        entityId: String,
        opId: String,
        local: T,
        remote: T,
    ) = SyncConflictEntity(
        entityTypeCode = entityType,
        entityId = entityId,
        operationId = opId,
        localVersion = 0L,
        remoteVersion = 1L,
        localPayloadJson = json.encodeToString(local),
        remotePayloadJson = json.encodeToString(remote),
        detectedAtEpochMillis = 1000L,
    )

    private fun operationEntity(
        entityType: String,
        entityId: String,
        opId: String,
        opType: String,
    ) = SyncOperationEntity(
        operationId = opId,
        entityTypeCode = entityType,
        entityId = entityId,
        operationTypeCode = opType,
        baseVersion = if (opType == "CREATE") null else 1L,
        statusCode = "IN_FLIGHT",
        attemptCount = 1,
        lastError = null,
        nextAttemptAtEpochMillis = 1000L,
        createdAtEpochMillis = 1000L,
        updatedAtEpochMillis = 1000L,
    )
}

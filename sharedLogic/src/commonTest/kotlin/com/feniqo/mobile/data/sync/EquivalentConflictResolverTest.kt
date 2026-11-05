package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.UserMerchantVerificationDto
import com.feniqo.mobile.data.util.UuidHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EquivalentConflictResolverTest {
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = true
        }

    @Test
    fun transaction_equivalent_when_all_business_fields_match_ignoring_version_and_metadata() {
        val localDto =
            TransactionDto(
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

        val remoteDto =
            TransactionDto(
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
    fun transaction_equivalent_when_split_fields_match_or_default_to_user_id() {
        val localDto =
            sampleTx().copy(
                paidByUserId = "usr-1",
                participantUserIds = listOf("usr-1", "usr-2"),
            )
        val remoteDto =
            sampleTx(version = 1L).copy(
                paidByUserId = "usr-1",
                participantUserIds = listOf("usr-1", "usr-2"),
            )

        val conflict = conflictEntity("TRANSACTION", "tx-1", "op-1", localDto, remoteDto)
        val op = operationEntity("TRANSACTION", "tx-1", "op-1", "CREATE")

        assertTrue(EquivalentConflictResolver.isEquivalent(conflict, op))

        // When empty, defaults to userId/listOf(userId)
        val localDefault = sampleTx().copy(paidByUserId = null, participantUserIds = emptyList())
        val remoteExplicit = sampleTx(version = 1L).copy(paidByUserId = "usr-1", participantUserIds = listOf("usr-1"))
        val defaultConflict = conflictEntity("TRANSACTION", "tx-1", "op-1", localDefault, remoteExplicit)
        assertTrue(EquivalentConflictResolver.isEquivalent(defaultConflict, op))
    }

    @Test
    fun transaction_not_equivalent_when_paid_by_user_id_differs() {
        val localDto = sampleTx().copy(paidByUserId = "usr-1", participantUserIds = listOf("usr-1", "usr-2"))
        val remoteDto = sampleTx().copy(paidByUserId = "usr-2", participantUserIds = listOf("usr-1", "usr-2"))

        val conflict = conflictEntity("TRANSACTION", "tx-1", "op-1", localDto, remoteDto)
        val op = operationEntity("TRANSACTION", "tx-1", "op-1", "CREATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun transaction_not_equivalent_when_participant_user_ids_differ() {
        val localDto = sampleTx().copy(paidByUserId = "usr-1", participantUserIds = listOf("usr-1", "usr-2"))
        val remoteDto = sampleTx().copy(paidByUserId = "usr-1", participantUserIds = listOf("usr-1", "usr-3"))

        val conflict = conflictEntity("TRANSACTION", "tx-1", "op-1", localDto, remoteDto)
        val op = operationEntity("TRANSACTION", "tx-1", "op-1", "CREATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun category_equivalent_positive_and_negative() {
        val localDto =
            CategoryDto(
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
        val remoteDto =
            CategoryDto(
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
        val localDto =
            ProfileDto(
                id = "usr-1",
                email = "test@example.com ",
                fullName = "Test User ",
                currency = "try",
                theme = "dark",
                lang = "tr",
                activeWorkspaceId = null,
                createdAt = "2026-08-25T17:00:00Z",
            )
        val remoteDto =
            ProfileDto(
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

    @Test
    fun userMerchantVerification_assign_equivalent_when_business_fields_match_ignoring_version_and_metadata() {
        val userId = "11111111-1111-4111-8111-111111111111"
        val alias = "NETFLIX"
        val id = UuidHelper.deterministicMerchantVerificationUuid(userId, alias)

        val localDto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 0L,
            )

        val remoteDto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-08-25T17:00:01Z",
                updatedAt = "2026-08-25T17:00:01Z",
                version = 1L,
            )

        val conflict = conflictEntity("USER_MERCHANT_VERIFICATION", id, "op-1", localDto, remoteDto, "USER:$userId")
        val op = operationEntity("USER_MERCHANT_VERIFICATION", id, "op-1", "CREATE")

        assertTrue(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun userMerchantVerification_block_equivalent_when_business_fields_match() {
        val userId = "11111111-1111-4111-8111-111111111111"
        val alias = "KAHVEDUNYASI"
        val id = UuidHelper.deterministicMerchantVerificationUuid(userId, alias)

        val localDto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "BLOCK",
                merchantId = null,
                scopeCode = "PERSONAL",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 0L,
            )

        val remoteDto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                workspaceId = null,
                normalizedAlias = alias,
                decisionCode = "BLOCK",
                merchantId = null,
                scopeCode = "PERSONAL",
                createdAt = "2026-08-25T17:00:01Z",
                updatedAt = "2026-08-25T17:00:01Z",
                version = 2L,
            )

        val conflict = conflictEntity("USER_MERCHANT_VERIFICATION", id, "op-1", localDto, remoteDto, "USER:$userId")
        val op = operationEntity("USER_MERCHANT_VERIFICATION", id, "op-1", "CREATE")

        assertTrue(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun userMerchantVerification_different_alias_returns_false() {
        val userId = "11111111-1111-4111-8111-111111111111"
        val alias1 = "NETFLIX"
        val alias2 = "SPOTIFY"
        val id1 = UuidHelper.deterministicMerchantVerificationUuid(userId, alias1)
        val id2 = UuidHelper.deterministicMerchantVerificationUuid(userId, alias2)

        val localDto =
            UserMerchantVerificationDto(
                id = id1,
                userId = userId,
                normalizedAlias = alias1,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 0L,
            )
        val remoteDto =
            UserMerchantVerificationDto(
                id = id2,
                userId = userId,
                normalizedAlias = alias2,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 1L,
            )

        val conflict = conflictEntity("USER_MERCHANT_VERIFICATION", id1, "op-1", localDto, remoteDto, "USER:$userId")
        val op = operationEntity("USER_MERCHANT_VERIFICATION", id1, "op-1", "CREATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun userMerchantVerification_different_decision_returns_false() {
        val userId = "11111111-1111-4111-8111-111111111111"
        val alias = "NETFLIX"
        val id = UuidHelper.deterministicMerchantVerificationUuid(userId, alias)

        val localDto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 0L,
            )
        val remoteDto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                normalizedAlias = alias,
                decisionCode = "BLOCK",
                merchantId = null,
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 1L,
            )

        val conflict = conflictEntity("USER_MERCHANT_VERIFICATION", id, "op-1", localDto, remoteDto, "USER:$userId")
        val op = operationEntity("USER_MERCHANT_VERIFICATION", id, "op-1", "CREATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun userMerchantVerification_different_merchant_returns_false() {
        val userId = "11111111-1111-4111-8111-111111111111"
        val alias = "NETFLIX"
        val id = UuidHelper.deterministicMerchantVerificationUuid(userId, alias)

        val localDto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix_tr",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 0L,
            )
        val remoteDto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix_us",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 1L,
            )

        val conflict = conflictEntity("USER_MERCHANT_VERIFICATION", id, "op-1", localDto, remoteDto, "USER:$userId")
        val op = operationEntity("USER_MERCHANT_VERIFICATION", id, "op-1", "CREATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun userMerchantVerification_malformed_json_returns_false() {
        val userId = "11111111-1111-4111-8111-111111111111"
        val alias = "NETFLIX"
        val id = UuidHelper.deterministicMerchantVerificationUuid(userId, alias)

        val validDto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 0L,
            )

        val malformedConflict =
            SyncConflictEntity(
                syncScopeKey = "USER:$userId",
                entityTypeCode = "USER_MERCHANT_VERIFICATION",
                entityId = id,
                operationId = "op-1",
                localVersion = 0L,
                remoteVersion = 1L,
                localPayloadJson = "{malformed json}",
                remotePayloadJson = json.encodeToString(validDto),
                detectedAtEpochMillis = 1000L,
            )
        val op = operationEntity("USER_MERCHANT_VERIFICATION", id, "op-1", "CREATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(malformedConflict, op))
    }

    @Test
    fun userMerchantVerification_cross_user_returns_false() {
        val user1 = "11111111-1111-4111-8111-111111111111"
        val user2 = "22222222-2222-4222-8222-222222222222"
        val alias = "NETFLIX"
        val id1 = UuidHelper.deterministicMerchantVerificationUuid(user1, alias)
        val id2 = UuidHelper.deterministicMerchantVerificationUuid(user2, alias)

        val localDto =
            UserMerchantVerificationDto(
                id = id1,
                userId = user1,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 0L,
            )
        val remoteDto =
            UserMerchantVerificationDto(
                id = id2,
                userId = user2,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 1L,
            )

        val conflict = conflictEntity("USER_MERCHANT_VERIFICATION", id1, "op-1", localDto, remoteDto, "USER:$user1")
        val op = operationEntity("USER_MERCHANT_VERIFICATION", id1, "op-1", "CREATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun userMerchantVerification_tombstone_returns_false() {
        val userId = "11111111-1111-4111-8111-111111111111"
        val alias = "NETFLIX"
        val id = UuidHelper.deterministicMerchantVerificationUuid(userId, alias)

        val localDto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 0L,
            )
        val remoteTombstone =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                deletedAt = "2026-08-25T17:00:01Z",
                version = 1L,
            )

        val conflict = conflictEntity("USER_MERCHANT_VERIFICATION", id, "op-1", localDto, remoteTombstone, "USER:$userId")
        val op = operationEntity("USER_MERCHANT_VERIFICATION", id, "op-1", "CREATE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, op))
    }

    @Test
    fun userMerchantVerification_update_and_delete_operations_return_false() {
        val userId = "11111111-1111-4111-8111-111111111111"
        val alias = "NETFLIX"
        val id = UuidHelper.deterministicMerchantVerificationUuid(userId, alias)

        val dto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 1L,
            )

        val conflict = conflictEntity("USER_MERCHANT_VERIFICATION", id, "op-1", dto, dto, "USER:$userId")
        val opUpdate = operationEntity("USER_MERCHANT_VERIFICATION", id, "op-1", "UPDATE")
        val opDelete = operationEntity("USER_MERCHANT_VERIFICATION", id, "op-1", "DELETE")

        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, opUpdate))
        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, opDelete))
    }

    @Test
    fun userMerchantVerification_conflict_and_operation_identity_mismatch_returns_false() {
        val userId = "11111111-1111-4111-8111-111111111111"
        val alias = "NETFLIX"
        val id = UuidHelper.deterministicMerchantVerificationUuid(userId, alias)

        val dto =
            UserMerchantVerificationDto(
                id = id,
                userId = userId,
                normalizedAlias = alias,
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                createdAt = "2026-08-25T17:00:00Z",
                updatedAt = "2026-08-25T17:00:00Z",
                version = 1L,
            )

        val conflict = conflictEntity("USER_MERCHANT_VERIFICATION", id, "op-1", dto, dto, "USER:$userId")

        // OperationId mismatch
        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, operationEntity("USER_MERCHANT_VERIFICATION", id, "op-2", "CREATE")))
        // EntityId mismatch
        assertFalse(
            EquivalentConflictResolver.isEquivalent(conflict, operationEntity("USER_MERCHANT_VERIFICATION", "other-id", "op-1", "CREATE")),
        )
        // EntityType mismatch
        assertFalse(EquivalentConflictResolver.isEquivalent(conflict, operationEntity("CATEGORY", id, "op-1", "CREATE")))
    }

    private inline fun <reified T : Any> conflictEntity(
        entityType: String,
        entityId: String,
        opId: String,
        local: T,
        remote: T,
        syncScopeKey: String = "USER:test-user",
    ) = SyncConflictEntity(
        syncScopeKey = syncScopeKey,
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
        syncScopeKey = "USER:test-user",
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

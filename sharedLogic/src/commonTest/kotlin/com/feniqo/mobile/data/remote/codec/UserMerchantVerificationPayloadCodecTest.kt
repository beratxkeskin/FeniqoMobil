package com.feniqo.mobile.data.remote.codec

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UserMerchantVerificationPayloadCodecTest {
    private val userId = "67a3f892-12c4-4b9a-b8e7-9d8e7a6b5c4d"
    private val deterministicId = UuidHelper.deterministicMerchantVerificationUuid(userId, "NETFLIX")

    private fun createEntity(
        id: String = deterministicId,
        ownerId: String = userId,
        alias: String = "NETFLIX",
        decision: String = "ASSIGN",
        merchantId: String? = "netflix",
    ) = UserMerchantVerificationEntity(
        id = id,
        ownerId = ownerId,
        workspaceId = null,
        scopeKey = "user:$ownerId",
        normalizedAlias = alias,
        decisionCode = decision,
        merchantId = merchantId,
        scopeCode = "PERSONAL",
        createdAtEpochMillis = 1700000000000L,
        sync =
            SyncMetadata(
                syncStatus = SyncStatus.SYNCED.name,
                updatedAtEpochMillis = 1700000000000L,
                localUpdatedAtEpochMillis = 1700000000000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            ),
    )

    @Test
    fun encodesAndValidatesCreateSuccessfully() {
        val entity = createEntity()
        val json = UserMerchantVerificationPayloadCodec.encodeCreate(entity)

        val validated =
            UserMerchantVerificationPayloadCodec.parseAndValidate(
                jsonString = json,
                operation = "CREATE",
                baseVersion = null,
            )

        assertEquals(deterministicId, validated.id)
        assertEquals(userId, validated.userId)
        assertEquals("NETFLIX", validated.normalizedAlias)
        assertEquals("ASSIGN", validated.decisionCode)
        assertEquals("netflix", validated.merchantId)
        assertNull(validated.workspaceId)
        assertEquals("PERSONAL", validated.scopeCode)
    }

    @Test
    fun encodesAndValidatesBlockUpdateSuccessfully() {
        val blockId = UuidHelper.deterministicMerchantVerificationUuid(userId, "SPOTIFY")
        val entity = createEntity(id = blockId, alias = "SPOTIFY", decision = "BLOCK", merchantId = null)
        val json = UserMerchantVerificationPayloadCodec.encodeUpdate(entity)

        val validated =
            UserMerchantVerificationPayloadCodec.parseAndValidate(
                jsonString = json,
                operation = "UPDATE",
                baseVersion = 1L,
            )

        assertEquals("BLOCK", validated.decisionCode)
        assertNull(validated.merchantId)
    }

    @Test
    fun rejectsCreateWithBaseVersion() {
        val entity = createEntity()
        val json = UserMerchantVerificationPayloadCodec.encodeCreate(entity)

        assertFailsWith<IllegalArgumentException> {
            UserMerchantVerificationPayloadCodec.parseAndValidate(json, "CREATE", baseVersion = 1L)
        }
    }

    @Test
    fun rejectsUpdateWithoutBaseVersion() {
        val entity = createEntity()
        val json = UserMerchantVerificationPayloadCodec.encodeUpdate(entity)

        assertFailsWith<IllegalArgumentException> {
            UserMerchantVerificationPayloadCodec.parseAndValidate(json, "UPDATE", baseVersion = null)
        }
    }

    @Test
    fun rejectsDeleteWithInvalidBaseVersion() {
        val entity = createEntity()
        val json = UserMerchantVerificationPayloadCodec.encodeDelete(entity)

        assertFailsWith<IllegalArgumentException> {
            UserMerchantVerificationPayloadCodec.parseAndValidate(json, "DELETE", baseVersion = 0L)
        }
    }

    @Test
    fun rejectsMismatchedDeterministicIdInPayload() {
        // ID manuel değiştirilmiş sahte JSON
        val invalidJson =
            """
            {
                "id": "11111111-2222-3333-4444-555555555555",
                "user_id": "$userId",
                "workspace_id": null,
                "normalized_alias": "NETFLIX",
                "decision_code": "ASSIGN",
                "merchant_id": "netflix",
                "scope_code": "PERSONAL"
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            UserMerchantVerificationPayloadCodec.parseAndValidate(invalidJson, "CREATE", null)
        }
    }

    @Test
    fun rejectsUnknownPropertiesInPayload() {
        val unknownFieldJson =
            """
            {
                "id": "$deterministicId",
                "user_id": "$userId",
                "workspace_id": null,
                "normalized_alias": "NETFLIX",
                "decision_code": "ASSIGN",
                "merchant_id": "netflix",
                "scope_code": "PERSONAL",
                "hacker_field": "injected"
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            UserMerchantVerificationPayloadCodec.parseAndValidate(unknownFieldJson, "CREATE", null)
        }
    }

    @Test
    fun rejectsMissingWorkspaceIdInPayload() {
        val json =
            """
            {
                "id": "$deterministicId",
                "user_id": "$userId",
                "normalized_alias": "NETFLIX",
                "decision_code": "ASSIGN",
                "merchant_id": "netflix",
                "scope_code": "PERSONAL"
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            UserMerchantVerificationPayloadCodec.parseAndValidate(json, "CREATE", null)
        }
    }

    @Test
    fun rejectsStringNullWorkspaceIdInPayload() {
        val json =
            """
            {
                "id": "$deterministicId",
                "user_id": "$userId",
                "workspace_id": "null",
                "normalized_alias": "NETFLIX",
                "decision_code": "ASSIGN",
                "merchant_id": "netflix",
                "scope_code": "PERSONAL"
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            UserMerchantVerificationPayloadCodec.parseAndValidate(json, "CREATE", null)
        }
    }

    @Test
    fun rejectsMissingScopeCodeInPayload() {
        val json =
            """
            {
                "id": "$deterministicId",
                "user_id": "$userId",
                "workspace_id": null,
                "normalized_alias": "NETFLIX",
                "decision_code": "ASSIGN",
                "merchant_id": "netflix"
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            UserMerchantVerificationPayloadCodec.parseAndValidate(json, "CREATE", null)
        }
    }

    @Test
    fun rejectsMissingMerchantIdKeyInCreatePayload() {
        val json =
            """
            {
                "id": "$deterministicId",
                "user_id": "$userId",
                "workspace_id": null,
                "normalized_alias": "NETFLIX",
                "decision_code": "ASSIGN",
                "scope_code": "PERSONAL"
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            UserMerchantVerificationPayloadCodec.parseAndValidate(json, "CREATE", null)
        }
    }

    @Test
    fun rejectsMissingMerchantIdKeyInUpdatePayload() {
        val json =
            """
            {
                "id": "$deterministicId",
                "user_id": "$userId",
                "workspace_id": null,
                "normalized_alias": "NETFLIX",
                "decision_code": "ASSIGN",
                "scope_code": "PERSONAL"
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            UserMerchantVerificationPayloadCodec.parseAndValidate(json, "UPDATE", baseVersion = 1L)
        }
    }
}

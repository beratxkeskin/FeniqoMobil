package com.feniqo.mobile.data.remote.codec

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.remote.mapper.RemoteMappingException
import com.feniqo.mobile.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WorkspacePayloadCodecTest {

    private val json = Json { ignoreUnknownKeys = false }

    private fun createValidWorkspaceEntity(
        id: String = "ws-100",
        name: String = "Şirket Ana Hesabı 🚀",
        normalizedName: String = "şirket ana hesabı 🚀",
        ownerId: String = "user-1",
        typeCode: String = "shared",
        currencyCode: String = "TRY",
        description: String? = "Şirket içi bütçe ve harcama takibi",
        createdAtEpochMillis: Long = 1757160000000L, // 2025-09-06T12:00:00Z
        syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
        updatedAtEpochMillis: Long = 1757160000000L,
        localUpdatedAtEpochMillis: Long = 1757160000000L,
        deletedAtEpochMillis: Long? = null,
        version: Long = 0L,
        baseVersion: Long? = null,
    ): WorkspaceEntity = WorkspaceEntity(
        id = id,
        name = name,
        normalizedName = normalizedName,
        ownerId = ownerId,
        typeCode = typeCode,
        currencyCode = currencyCode,
        description = description,
        createdAtEpochMillis = createdAtEpochMillis,
        sync = SyncMetadata(
            syncStatus = syncStatus.name,
            updatedAtEpochMillis = updatedAtEpochMillis,
            localUpdatedAtEpochMillis = localUpdatedAtEpochMillis,
            deletedAtEpochMillis = deletedAtEpochMillis,
            version = version,
            baseVersion = baseVersion,
            lastSyncError = null,
        ),
    )

    @Test
    fun encodes_create_payload_with_full_key_set_and_unicode_support() {
        val entity = createValidWorkspaceEntity()

        val payloadString = WorkspacePayloadCodec.encode(
            entity = entity,
            operationType = OutboxOperationType.CREATE,
            includeCreatedAtInCreate = true,
        )

        val jsonElement = json.parseToJsonElement(payloadString).jsonObject
        assertEquals(6, jsonElement.keys.size)
        assertEquals("ws-100", jsonElement["id"]?.jsonPrimitive?.content)
        assertEquals("Şirket Ana Hesabı 🚀", jsonElement["name"]?.jsonPrimitive?.content)
        assertEquals("shared", jsonElement["type_code"]?.jsonPrimitive?.content)
        assertEquals("TRY", jsonElement["currency_code"]?.jsonPrimitive?.content)
        assertEquals("Şirket içi bütçe ve harcama takibi", jsonElement["description"]?.jsonPrimitive?.content)
        assertEquals("2025-09-06T12:00:00Z", jsonElement["created_at"]?.jsonPrimitive?.content)

        // Strict forbidden keys check
        val forbiddenKeys = listOf(
            "owner_id", "normalized_name", "version", "base_version", "updated_at",
            "deleted_at", "role", "role_code", "token", "code", "hash", "member", "members",
            "amount", "limit", "balance",
        )
        for (key in forbiddenKeys) {
            assertFalse(jsonElement.containsKey(key), "Payload yasak alan içermemeli: $key")
        }
    }

    @Test
    fun encodes_create_payload_with_null_description_and_without_optional_created_at() {
        val entity = createValidWorkspaceEntity(
            description = null,
        )

        val payloadString = WorkspacePayloadCodec.encode(
            entity = entity,
            operationType = OutboxOperationType.CREATE,
            includeCreatedAtInCreate = false,
        )

        val jsonElement = json.parseToJsonElement(payloadString).jsonObject
        assertEquals(5, jsonElement.keys.size)
        assertEquals("ws-100", jsonElement["id"]?.jsonPrimitive?.content)
        assertEquals("Şirket Ana Hesabı 🚀", jsonElement["name"]?.jsonPrimitive?.content)
        assertEquals("shared", jsonElement["type_code"]?.jsonPrimitive?.content)
        assertEquals("TRY", jsonElement["currency_code"]?.jsonPrimitive?.content)
        assertTrue(jsonElement.containsKey("description"))
        assertEquals(kotlinx.serialization.json.JsonNull, jsonElement["description"])
        assertFalse(jsonElement.containsKey("created_at"))
    }

    @Test
    fun encodes_update_payload_with_exact_allowlist_keys() {
        val entity = createValidWorkspaceEntity(
            syncStatus = SyncStatus.PENDING_UPDATE,
            version = 1L,
            baseVersion = 1L,
            description = "Güncellenmiş açıklama 🇹🇷",
        )

        val payloadString = WorkspacePayloadCodec.encode(
            entity = entity,
            operationType = OutboxOperationType.UPDATE,
        )

        val jsonElement = json.parseToJsonElement(payloadString).jsonObject
        assertEquals(5, jsonElement.keys.size)
        assertEquals(setOf("id", "name", "type_code", "currency_code", "description"), jsonElement.keys)
        assertEquals("ws-100", jsonElement["id"]?.jsonPrimitive?.content)
        assertEquals("Şirket Ana Hesabı 🚀", jsonElement["name"]?.jsonPrimitive?.content)
        assertEquals("shared", jsonElement["type_code"]?.jsonPrimitive?.content)
        assertEquals("TRY", jsonElement["currency_code"]?.jsonPrimitive?.content)
        assertEquals("Güncellenmiş açıklama 🇹🇷", jsonElement["description"]?.jsonPrimitive?.content)

        // Strict forbidden keys check
        assertFalse(jsonElement.containsKey("created_at"))
        assertFalse(jsonElement.containsKey("owner_id"))
        assertFalse(jsonElement.containsKey("normalized_name"))
        assertFalse(jsonElement.containsKey("version"))
        assertFalse(jsonElement.containsKey("base_version"))
    }

    @Test
    fun encodes_delete_payload_with_only_id() {
        val entity = createValidWorkspaceEntity(
            syncStatus = SyncStatus.PENDING_DELETE,
            version = 2L,
            baseVersion = 2L,
            deletedAtEpochMillis = 1757165000000L,
        )

        val payloadString = WorkspacePayloadCodec.encode(
            entity = entity,
            operationType = OutboxOperationType.DELETE,
        )

        val jsonElement = json.parseToJsonElement(payloadString).jsonObject
        assertEquals(1, jsonElement.keys.size)
        assertEquals(setOf("id"), jsonElement.keys)
        assertEquals("ws-100", jsonElement["id"]?.jsonPrimitive?.content)

        assertFalse(jsonElement.containsKey("name"))
        assertFalse(jsonElement.containsKey("type_code"))
        assertFalse(jsonElement.containsKey("currency_code"))
        assertFalse(jsonElement.containsKey("description"))
        assertFalse(jsonElement.containsKey("deleted_at"))
        assertFalse(jsonElement.containsKey("version"))
    }

    @Test
    fun rejects_operation_and_sync_status_mismatches_fail_closed() {
        // CREATE with PENDING_UPDATE or PENDING_DELETE
        val createWithPendingUpdate = createValidWorkspaceEntity(syncStatus = SyncStatus.PENDING_UPDATE)
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(createWithPendingUpdate, OutboxOperationType.CREATE)
        }

        val createWithBaseVersion = createValidWorkspaceEntity(baseVersion = 1L)
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(createWithBaseVersion, OutboxOperationType.CREATE)
        }

        val createWithDeletedTimestamp = createValidWorkspaceEntity(deletedAtEpochMillis = 123456L)
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(createWithDeletedTimestamp, OutboxOperationType.CREATE)
        }

        // UPDATE with PENDING_CREATE or deletedAt
        val updateWithPendingCreate = createValidWorkspaceEntity(syncStatus = SyncStatus.PENDING_CREATE)
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(updateWithPendingCreate, OutboxOperationType.UPDATE)
        }

        val updateWithDeletedTimestamp = createValidWorkspaceEntity(
            syncStatus = SyncStatus.PENDING_UPDATE,
            deletedAtEpochMillis = 123456L,
        )
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(updateWithDeletedTimestamp, OutboxOperationType.UPDATE)
        }

        // DELETE with PENDING_UPDATE or missing deletedAt
        val deleteWithPendingUpdate = createValidWorkspaceEntity(
            syncStatus = SyncStatus.PENDING_UPDATE,
            baseVersion = 1L,
            deletedAtEpochMillis = 123456L,
        )
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(deleteWithPendingUpdate, OutboxOperationType.DELETE)
        }

        val deleteWithoutDeletedAt = createValidWorkspaceEntity(
            syncStatus = SyncStatus.PENDING_DELETE,
            baseVersion = 1L,
            deletedAtEpochMillis = null,
        )
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(deleteWithoutDeletedAt, OutboxOperationType.DELETE)
        }
    }

    @Test
    fun rejects_invalid_or_missing_base_version_for_update_and_delete() {
        // UPDATE: baseVersion null
        val updateNullBaseVersion = createValidWorkspaceEntity(
            syncStatus = SyncStatus.PENDING_UPDATE,
            baseVersion = null,
        )
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(updateNullBaseVersion, OutboxOperationType.UPDATE)
        }

        // UPDATE: baseVersion 0
        val updateZeroBaseVersion = createValidWorkspaceEntity(
            syncStatus = SyncStatus.PENDING_UPDATE,
            baseVersion = 0L,
        )
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(updateZeroBaseVersion, OutboxOperationType.UPDATE)
        }

        // UPDATE: baseVersion negative
        val updateNegativeBaseVersion = createValidWorkspaceEntity(
            syncStatus = SyncStatus.PENDING_UPDATE,
            baseVersion = -1L,
        )
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(updateNegativeBaseVersion, OutboxOperationType.UPDATE)
        }

        // DELETE: baseVersion null
        val deleteNullBaseVersion = createValidWorkspaceEntity(
            syncStatus = SyncStatus.PENDING_DELETE,
            baseVersion = null,
            deletedAtEpochMillis = 123456L,
        )
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(deleteNullBaseVersion, OutboxOperationType.DELETE)
        }

        // DELETE: baseVersion 0
        val deleteZeroBaseVersion = createValidWorkspaceEntity(
            syncStatus = SyncStatus.PENDING_DELETE,
            baseVersion = 0L,
            deletedAtEpochMillis = 123456L,
        )
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(deleteZeroBaseVersion, OutboxOperationType.DELETE)
        }

        // DELETE: baseVersion negative
        val deleteNegativeBaseVersion = createValidWorkspaceEntity(
            syncStatus = SyncStatus.PENDING_DELETE,
            baseVersion = -5L,
            deletedAtEpochMillis = 123456L,
        )
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(deleteNegativeBaseVersion, OutboxOperationType.DELETE)
        }
    }

    @Test
    fun rejects_invalid_or_blank_required_fields_in_entity() {
        // Blank id
        val blankId = createValidWorkspaceEntity(id = "   ")
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(blankId, OutboxOperationType.CREATE)
        }

        // Blank name
        val blankName = createValidWorkspaceEntity(name = "   ")
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(blankName, OutboxOperationType.CREATE)
        }
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(blankName.copy(sync = blankName.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name)), OutboxOperationType.UPDATE)
        }

        // Invalid type_code
        val invalidType = createValidWorkspaceEntity(typeCode = "enterprise")
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(invalidType, OutboxOperationType.CREATE)
        }
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(invalidType.copy(sync = invalidType.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name)), OutboxOperationType.UPDATE)
        }

        // Invalid currency_code
        val invalidCurrency = createValidWorkspaceEntity(currencyCode = "GBP")
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(invalidCurrency, OutboxOperationType.CREATE)
        }
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(invalidCurrency.copy(sync = invalidCurrency.sync.copy(syncStatus = SyncStatus.PENDING_UPDATE.name)), OutboxOperationType.UPDATE)
        }

        // Invalid createdAtEpochMillis for CREATE with includeCreatedAt
        val invalidCreatedAt = createValidWorkspaceEntity(createdAtEpochMillis = 0L)
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encode(invalidCreatedAt, OutboxOperationType.CREATE, includeCreatedAtInCreate = true)
        }
    }

    @Test
    fun encodePendingCreateHardDeletePayload_encodes_canonical_id_only_and_escapes_special_characters() {
        val testId = "ws-100\"foo\\bar"
        val payloadWithSpecialChars = WorkspacePayloadCodec.encodePendingCreateHardDeletePayload(testId)
        val jsonElement = json.parseToJsonElement(payloadWithSpecialChars).jsonObject
        assertEquals(1, jsonElement.keys.size)
        assertEquals(setOf("id"), jsonElement.keys)
        assertEquals(testId, jsonElement["id"]?.jsonPrimitive?.content)

        val cleanUuidPayload = WorkspacePayloadCodec.encodePendingCreateHardDeletePayload("550e8400-e29b-41d4-a716-446655440000")
        val cleanJson = json.parseToJsonElement(cleanUuidPayload).jsonObject
        assertEquals("550e8400-e29b-41d4-a716-446655440000", cleanJson["id"]?.jsonPrimitive?.content)

        // Blank id fail-closed
        assertFailsWith<RemoteMappingException> {
            WorkspacePayloadCodec.encodePendingCreateHardDeletePayload("   ")
        }
    }

    @Test
    fun parseAndValidate_valid_create_update_delete_payloads() {
        val testUuid = "550e8400-e29b-41d4-a716-446655440000"
        // Valid CREATE with created_at
        val createPayload = """
            {
                "id": "$testUuid",
                "name": "Yeni Calisma Alani",
                "type_code": "shared",
                "currency_code": "TRY",
                "description": "Aciklama",
                "created_at": "2026-03-01T10:00:00Z"
            }
        """.trimIndent()
        val createObj = WorkspacePayloadCodec.parseAndValidate(
            operationId = "op-1",
            entityId = testUuid,
            operation = com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE,
            baseVersion = null,
            payloadJson = createPayload,
        )
        assertEquals(testUuid, createObj["id"]?.jsonPrimitive?.content)
        assertEquals("Yeni Calisma Alani", createObj["name"]?.jsonPrimitive?.content)

        // Valid UPDATE
        val updatePayload = """
            {
                "id": "$testUuid",
                "name": "Yeni Calisma Alani",
                "type_code": "personal",
                "currency_code": "USD",
                "description": null
            }
        """.trimIndent()
        val updateObj = WorkspacePayloadCodec.parseAndValidate(
            operationId = "op-2",
            entityId = testUuid,
            operation = com.feniqo.mobile.data.remote.core.RemoteWriteOperation.UPDATE,
            baseVersion = 1L,
            payloadJson = updatePayload,
        )
        assertEquals(testUuid, updateObj["id"]?.jsonPrimitive?.content)
        assertEquals("personal", updateObj["type_code"]?.jsonPrimitive?.content)

        // Valid DELETE
        val deletePayload = """{"id": "$testUuid"}"""
        val deleteObj = WorkspacePayloadCodec.parseAndValidate(
            operationId = "op-3",
            entityId = testUuid,
            operation = com.feniqo.mobile.data.remote.core.RemoteWriteOperation.DELETE,
            baseVersion = 2L,
            payloadJson = deletePayload,
        )
        assertEquals(testUuid, deleteObj["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun parseAndValidate_rejects_invalid_inputs_fail_closed() {
        val testUuid = "550e8400-e29b-41d4-a716-446655440000"
        val otherUuid = "550e8400-e29b-41d4-a716-446655440001"

        // Blank or non-json
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, "")
        }
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, "{not-json}")
        }

        // Non-UUID entityId
        val validCreateWithValidId = """{"id": "$testUuid", "name": "A", "type_code": "shared", "currency_code": "TRY"}"""
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", "not-a-uuid", com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, validCreateWithValidId)
        }

        // Non-UUID payload id
        val invalidPayloadId = """{"id": "ws-not-uuid", "name": "A", "type_code": "shared", "currency_code": "TRY"}"""
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, invalidPayloadId)
        }

        // ID mismatch
        val mismatchPayload = """{"id": "$otherUuid", "name": "A", "type_code": "shared", "currency_code": "TRY"}"""
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, mismatchPayload)
        }

        // CREATE baseVersion != null
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, 1L, validCreateWithValidId)
        }

        // UPDATE baseVersion == null
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.UPDATE, null, validCreateWithValidId)
        }

        // Blank description in CREATE
        val blankDescCreate = """{"id": "$testUuid", "name": "A", "type_code": "shared", "currency_code": "TRY", "description": "   "}"""
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, blankDescCreate)
        }

        // Blank description in UPDATE
        val blankDescUpdate = """{"id": "$testUuid", "name": "A", "type_code": "shared", "currency_code": "TRY", "description": "  "}"""
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.UPDATE, 1L, blankDescUpdate)
        }

        // Non-Z timezone offset timestamp in CREATE
        val nonZDateCreate = """{"id": "$testUuid", "name": "A", "type_code": "shared", "currency_code": "TRY", "created_at": "2026-03-01T10:00:00+03:00"}"""
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, nonZDateCreate)
        }

        // Forbidden keys in CREATE / UPDATE / DELETE
        val forbiddenInCreate = """{"id": "$testUuid", "name": "A", "type_code": "shared", "currency_code": "TRY", "owner_id": "u1"}"""
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, forbiddenInCreate)
        }
        val forbiddenInDelete = """{"id": "$testUuid", "name": "A"}"""
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.DELETE, 1L, forbiddenInDelete)
        }

        // Invalid created_at ISO format
        val invalidDateCreate = """{"id": "$testUuid", "name": "A", "type_code": "shared", "currency_code": "TRY", "created_at": "invalid-date"}"""
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, invalidDateCreate)
        }

        // Leading/trailing whitespace in entityId
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", " $testUuid", com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, validCreateWithValidId)
        }
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", "$testUuid ", com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, validCreateWithValidId)
        }

        // Leading/trailing whitespace in payload id
        val leadingWsPayloadId = """{"id": " $testUuid", "name": "A", "type_code": "shared", "currency_code": "TRY"}"""
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, leadingWsPayloadId)
        }
        val trailingWsPayloadId = """{"id": "$testUuid ", "name": "A", "type_code": "shared", "currency_code": "TRY"}"""
        assertFailsWith<IllegalArgumentException> {
            WorkspacePayloadCodec.parseAndValidate("op-1", testUuid, com.feniqo.mobile.data.remote.core.RemoteWriteOperation.CREATE, null, trailingWsPayloadId)
        }
    }
}


package com.feniqo.mobile.data.remote.codec

import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.mapper.RemoteMappingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WorkspaceMembershipPayloadCodecTest {

    private val validWorkspaceId = "11111111-1111-1111-1111-111111111111"
    private val validUserId = "22222222-2222-2222-2222-222222222222"
    private val validInvitationId = "33333333-3333-3333-3333-333333333333"
    private val validTokenHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private val validExpiresAt = "2026-10-01T12:00:00Z"

    @Test
    fun encodeInvitationCreate_encodes_allowlist_fields_only() {
        val jsonString = WorkspaceMembershipPayloadCodec.encodeInvitationCreate(
            id = validInvitationId,
            workspaceId = validWorkspaceId,
            tokenHash = validTokenHash,
            roleCode = "EDITOR",
            expiresAtIso = validExpiresAt,
            maxUses = 5,
        )

        val json = Json.parseToJsonElement(jsonString).jsonObject
        assertEquals(validInvitationId, json["id"]?.jsonPrimitive?.content)
        assertEquals(validWorkspaceId, json["workspace_id"]?.jsonPrimitive?.content)
        assertEquals(validTokenHash, json["token_hash"]?.jsonPrimitive?.content)
        assertEquals("EDITOR", json["role_code"]?.jsonPrimitive?.content)
        assertEquals(validExpiresAt, json["expires_at"]?.jsonPrimitive?.content)
        assertEquals("5", json["max_uses"]?.jsonPrimitive?.content)
        assertEquals(6, json.size) // Strict allowlist check: exactly 6 fields

        // Must validate cleanly
        val validated = WorkspaceMembershipPayloadCodec.parseAndValidateInvitation(
            operationId = "00000000000000000000000000000001",
            entityId = validInvitationId,
            operation = RemoteWriteOperation.CREATE,
            baseVersion = null,
            payloadJson = jsonString,
        )
        assertEquals(validInvitationId, validated["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun encodeInvitationCreate_rejects_owner_role() {
        assertFailsWith<RemoteMappingException> {
            WorkspaceMembershipPayloadCodec.encodeInvitationCreate(
                id = validInvitationId,
                workspaceId = validWorkspaceId,
                tokenHash = validTokenHash,
                roleCode = "OWNER", // Forbidden
                expiresAtIso = validExpiresAt,
                maxUses = 1,
            )
        }
    }

    @Test
    fun validateInvitationPayload_rejects_forbidden_keys() {
        val payloadWithRawToken = """
            {
                "id": "$validInvitationId",
                "workspace_id": "$validWorkspaceId",
                "token_hash": "$validTokenHash",
                "raw_token": "secret-token-that-should-never-be-here",
                "role_code": "VIEWER",
                "expires_at": "$validExpiresAt",
                "max_uses": 1
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            WorkspaceMembershipPayloadCodec.parseAndValidateInvitation(
                operationId = "00000000000000000000000000000001",
                entityId = validInvitationId,
                operation = RemoteWriteOperation.CREATE,
                baseVersion = null,
                payloadJson = payloadWithRawToken,
            )
        }
    }

    @Test
    fun parseAndValidateMember_rejects_generic_create_or_join_fail_closed() {
        val joinPayload = """
            {
                "workspace_id": "$validWorkspaceId",
                "user_id": "$validUserId",
                "role_code": "VIEWER"
            }
        """.trimIndent()

        val ex = assertFailsWith<IllegalArgumentException> {
            WorkspaceMembershipPayloadCodec.parseAndValidateMember(
                operationId = "00000000000000000000000000000002",
                entityId = "$validWorkspaceId:$validUserId",
                operation = RemoteWriteOperation.CREATE,
                baseVersion = null,
                payloadJson = joinPayload,
            )
        }
        assertTrue(ex.message?.contains("generic outbox CREATE/JOIN işlemi desteklenmez") == true)
    }

    @Test
    fun parseAndValidateMember_rejects_token_hash_in_update_or_delete() {
        val taintedUpdatePayload = """
            {
                "workspace_id": "$validWorkspaceId",
                "user_id": "$validUserId",
                "role_code": "VIEWER",
                "token_hash": "$validTokenHash"
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            WorkspaceMembershipPayloadCodec.parseAndValidateMember(
                operationId = "00000000000000000000000000000002",
                entityId = "$validWorkspaceId:$validUserId",
                operation = RemoteWriteOperation.UPDATE,
                baseVersion = 1L,
                payloadJson = taintedUpdatePayload,
            )
        }

        val taintedDeletePayload = """
            {
                "workspace_id": "$validWorkspaceId",
                "user_id": "$validUserId",
                "token": "raw_secret_token"
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            WorkspaceMembershipPayloadCodec.parseAndValidateMember(
                operationId = "00000000000000000000000000000002",
                entityId = "$validWorkspaceId:$validUserId",
                operation = RemoteWriteOperation.DELETE,
                baseVersion = 1L,
                payloadJson = taintedDeletePayload,
            )
        }
    }


    @Test
    fun encodeMemberRoleChange_rejects_owner() {
        assertFailsWith<RemoteMappingException> {
            WorkspaceMembershipPayloadCodec.encodeMemberRoleChange(
                workspaceId = validWorkspaceId,
                userId = validUserId,
                roleCode = "OWNER",
            )
        }
    }

    @Test
    fun validateMemberPayload_for_role_change_and_leave() {
        val canonicalEntityId = "$validWorkspaceId:$validUserId"

        // UPDATE (role change)
        val updatePayload = """
            {
                "workspace_id": "$validWorkspaceId",
                "user_id": "$validUserId",
                "role_code": "EDITOR"
            }
        """.trimIndent()
        val validatedUpdate = WorkspaceMembershipPayloadCodec.parseAndValidateMember(
            operationId = "00000000000000000000000000000003",
            entityId = canonicalEntityId,
            operation = RemoteWriteOperation.UPDATE,
            baseVersion = 1L,
            payloadJson = updatePayload,
        )
        assertEquals("EDITOR", validatedUpdate["role_code"]?.jsonPrimitive?.content)

        // DELETE (leave)
        val deletePayload = """
            {
                "workspace_id": "$validWorkspaceId",
                "user_id": "$validUserId"
            }
        """.trimIndent()
        val validatedDelete = WorkspaceMembershipPayloadCodec.parseAndValidateMember(
            operationId = "00000000000000000000000000000004",
            entityId = canonicalEntityId,
            operation = RemoteWriteOperation.DELETE,
            baseVersion = 1L,
            payloadJson = deletePayload,
        )
        assertEquals(validWorkspaceId, validatedDelete["workspace_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun validateMemberPayload_rejects_extra_unexpected_keys() {
        val taintedPayload = """
            {
                "workspace_id": "$validWorkspaceId",
                "user_id": "$validUserId",
                "unknown_injection": "attack"
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            WorkspaceMembershipPayloadCodec.parseAndValidateMember(
                operationId = "00000000000000000000000000000005",
                entityId = "$validWorkspaceId:$validUserId",
                operation = RemoteWriteOperation.DELETE,
                baseVersion = 1L,
                payloadJson = taintedPayload,
            )
        }
    }
}

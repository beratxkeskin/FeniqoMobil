package com.feniqo.mobile.data.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceMemberEntityIdTest {

    private val validWorkspaceId = "11111111-1111-1111-1111-111111111111"
    private val validUserId = "22222222-2222-2222-2222-222222222222"

    @Test
    fun encode_produces_canonical_entity_id() {
        val encoded = WorkspaceMemberEntityId.encode(validWorkspaceId, validUserId)
        assertEquals("$validWorkspaceId:$validUserId", encoded)
    }

    @Test
    fun decode_recovers_original_identifiers() {
        val canonical = "$validWorkspaceId:$validUserId"
        val (wsId, userId) = WorkspaceMemberEntityId.decode(canonical)
        assertEquals(validWorkspaceId, wsId)
        assertEquals(validUserId, userId)
    }

    @Test
    fun roundtrip_encode_decode_preserves_values() {
        val encoded = WorkspaceMemberEntityId.encode(validWorkspaceId, validUserId)
        val decoded = WorkspaceMemberEntityId.decode(encoded)
        assertEquals(validWorkspaceId to validUserId, decoded)
    }

    @Test
    fun isValid_validates_canonical_format() {
        assertTrue(WorkspaceMemberEntityId.isValid("$validWorkspaceId:$validUserId"))
        assertFalse(WorkspaceMemberEntityId.isValid("invalid-entity-id"))
        assertFalse(WorkspaceMemberEntityId.isValid("$validWorkspaceId:"))
        assertFalse(WorkspaceMemberEntityId.isValid(":$validUserId"))
        assertFalse(WorkspaceMemberEntityId.isValid("$validWorkspaceId:$validUserId:extra"))
        assertFalse(WorkspaceMemberEntityId.isValid(""))
    }

    @Test
    fun encode_rejects_non_uuid_inputs() {
        assertFailsWith<IllegalArgumentException> {
            WorkspaceMemberEntityId.encode("not-a-uuid", validUserId)
        }
        assertFailsWith<IllegalArgumentException> {
            WorkspaceMemberEntityId.encode(validWorkspaceId, "not-a-uuid")
        }
    }

    @Test
    fun decode_rejects_malformed_string() {
        assertFailsWith<IllegalArgumentException> {
            WorkspaceMemberEntityId.decode("just-a-plain-string")
        }
        assertFailsWith<IllegalArgumentException> {
            WorkspaceMemberEntityId.decode("11111111-1111-1111-1111-111111111111:not-a-uuid")
        }
    }
}

package com.feniqo.mobile.data.remote.codec

import com.feniqo.mobile.data.remote.core.RemoteWriteOperation
import com.feniqo.mobile.data.remote.mapper.RemoteMappingException
import com.feniqo.mobile.data.util.WorkspaceInvitationCrypto
import com.feniqo.mobile.data.util.WorkspaceMemberEntityId
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
private val membershipPayloadJson = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = false
}

@Serializable
private data class WorkspaceInvitationCreateWithCreatedAtPayload(
    val id: String,
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("token_hash")
    val tokenHash: String,
    @SerialName("role_code")
    val roleCode: String,
    @SerialName("expires_at")
    val expiresAt: String,
    @SerialName("max_uses")
    val maxUses: Int,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
private data class WorkspaceInvitationCreatePayload(
    val id: String,
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("token_hash")
    val tokenHash: String,
    @SerialName("role_code")
    val roleCode: String,
    @SerialName("expires_at")
    val expiresAt: String,
    @SerialName("max_uses")
    val maxUses: Int,
)

@Serializable
private data class WorkspaceMemberRoleChangePayload(
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("role_code")
    val roleCode: String,
)

@Serializable
private data class WorkspaceMemberLeavePayload(
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("user_id")
    val userId: String,
)

/**
 * WORKSPACE_INVITATION ve WORKSPACE_MEMBER için outbox JSON payload codec ve allowlist denetçisi.
 *
 * Güvenlik invariantları:
 * 1. Payload allowlist dışındaki hiçbir anahtar serialize edilmez ve kabul edilmez.
 * 2. Raw token (ham davet tokenı) ASLA serialize edilmez; yalnızca SHA-256 token_hash taşınır.
 * 3. Davet ve üyelik rollerinde OWNER kabul edilmez (yalnız EDITOR veya VIEWER).
 * 4. WORKSPACE_MEMBER entity_id canonical `workspaceId:userId` formatında olmalıdır.
 */
object WorkspaceMembershipPayloadCodec {

    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val VALID_ROLES = setOf("EDITOR", "VIEWER")

    // =========================================================================
    // 1. WORKSPACE_INVITATION ENCODE & VALIDATE
    // =========================================================================

    /**
     * WORKSPACE_INVITATION CREATE için canonical JSON payload üretir.
     */
    fun encodeInvitationCreate(
        id: String,
        workspaceId: String,
        tokenHash: String,
        roleCode: String,
        expiresAtIso: String,
        maxUses: Int,
        createdAtIso: String? = null,
    ): String {
        val cleanId = id.trim().requireUuid("id")
        val cleanWorkspaceId = workspaceId.trim().requireUuid("workspaceId")
        val cleanRoleCode = roleCode.trim().uppercase()
        if (cleanRoleCode !in VALID_ROLES) {
            throw RemoteMappingException("Davet için geçersiz rol: $roleCode. Beklenen: EDITOR veya VIEWER.")
        }
        if (!WorkspaceInvitationCrypto.isValidTokenHash(tokenHash)) {
            throw RemoteMappingException("Geçersiz token_hash formatı: $tokenHash")
        }
        if (maxUses <= 0) {
            throw RemoteMappingException("max_uses pozitif olmalıdır: $maxUses")
        }
        if (expiresAtIso.isBlank()) {
            throw RemoteMappingException("expires_at boş olamaz.")
        }

        return if (createdAtIso != null && createdAtIso.isNotBlank()) {
            membershipPayloadJson.encodeToString(
                WorkspaceInvitationCreateWithCreatedAtPayload(
                    id = cleanId,
                    workspaceId = cleanWorkspaceId,
                    tokenHash = tokenHash.trim(),
                    roleCode = cleanRoleCode,
                    expiresAt = expiresAtIso.trim(),
                    maxUses = maxUses,
                    createdAt = createdAtIso.trim(),
                ),
            )
        } else {
            membershipPayloadJson.encodeToString(
                WorkspaceInvitationCreatePayload(
                    id = cleanId,
                    workspaceId = cleanWorkspaceId,
                    tokenHash = tokenHash.trim(),
                    roleCode = cleanRoleCode,
                    expiresAt = expiresAtIso.trim(),
                    maxUses = maxUses,
                ),
            )
        }
    }

    /**
     * WORKSPACE_INVITATION outbox payloadını doğrular ve canonical JsonObject döner.
     */
    fun parseAndValidateInvitation(
        operationId: String,
        entityId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        payloadJson: String,
    ): JsonObject {
        require(operation == RemoteWriteOperation.CREATE) {
            "WORKSPACE_INVITATION için yalnız CREATE işlemi desteklenir. Alınan: $operation ($operationId)"
        }
        require(baseVersion == null) {
            "WORKSPACE_INVITATION CREATE işleminde baseVersion null olmalıdır: $baseVersion ($operationId)"
        }
        require(entityId.trim().matches(UUID_REGEX)) {
            "WORKSPACE_INVITATION entityId UUID formatında olmalıdır: $entityId ($operationId)"
        }

        val jsonObject = parsePayloadJson(operationId, payloadJson)
        val keys = jsonObject.keys
        val allowedKeys = setOf("id", "workspace_id", "token_hash", "role_code", "expires_at", "max_uses", "created_at")
        val forbidden = keys - allowedKeys
        if (forbidden.isNotEmpty()) {
            throw IllegalArgumentException("WORKSPACE_INVITATION payloadında izin verilmeyen alanlar var: $forbidden ($operationId)")
        }

        val payloadId = jsonObject["id"]?.jsonPrimitive?.content
        require(payloadId == entityId) {
            "Payload id ($payloadId) ile outbox entityId ($entityId) uyuşmuyor: $operationId"
        }

        val wsId = jsonObject["workspace_id"]?.jsonPrimitive?.content
        require(!wsId.isNullOrBlank() && UUID_REGEX.matches(wsId)) {
            "Geçersiz veya eksik workspace_id: $wsId ($operationId)"
        }

        val tokenHash = jsonObject["token_hash"]?.jsonPrimitive?.content
        require(!tokenHash.isNullOrBlank() && WorkspaceInvitationCrypto.isValidTokenHash(tokenHash)) {
            "Geçersiz veya eksik token_hash: $tokenHash ($operationId)"
        }

        val roleCode = jsonObject["role_code"]?.jsonPrimitive?.content?.uppercase()
        require(roleCode != null && roleCode in VALID_ROLES) {
            "Geçersiz veya eksik role_code: $roleCode ($operationId)"
        }

        val maxUses = jsonObject["max_uses"]?.jsonPrimitive?.content?.toIntOrNull()
        require(maxUses != null && maxUses > 0) {
            "Geçersiz veya pozitif olmayan max_uses: $maxUses ($operationId)"
        }

        val expiresAt = jsonObject["expires_at"]?.jsonPrimitive?.content
        require(!expiresAt.isNullOrBlank()) {
            "expires_at boş olamaz: $operationId"
        }

        return jsonObject
    }

    // =========================================================================
    // 2. WORKSPACE_MEMBER ENCODE & VALIDATE
    // =========================================================================

    /**
     * WORKSPACE_MEMBER ROLE CHANGE (UPDATE) için canonical JSON payload üretir.
     */
    fun encodeMemberRoleChange(
        workspaceId: String,
        userId: String,
        roleCode: String,
    ): String {
        val cleanWorkspaceId = workspaceId.trim().requireUuid("workspaceId")
        val cleanUserId = userId.trim().requireUuid("userId")
        val cleanRoleCode = roleCode.trim().uppercase()
        if (cleanRoleCode !in VALID_ROLES) {
            throw RemoteMappingException("Üye rol değişimi için geçersiz rol: $roleCode. Beklenen: EDITOR veya VIEWER.")
        }

        return membershipPayloadJson.encodeToString(
            WorkspaceMemberRoleChangePayload(
                workspaceId = cleanWorkspaceId,
                userId = cleanUserId,
                roleCode = cleanRoleCode,
            ),
        )
    }

    /**
     * WORKSPACE_MEMBER LEAVE (DELETE) için canonical JSON payload üretir.
     */
    fun encodeMemberLeave(
        workspaceId: String,
        userId: String,
    ): String {
        val cleanWorkspaceId = workspaceId.trim().requireUuid("workspaceId")
        val cleanUserId = userId.trim().requireUuid("userId")

        return membershipPayloadJson.encodeToString(
            WorkspaceMemberLeavePayload(
                workspaceId = cleanWorkspaceId,
                userId = cleanUserId,
            ),
        )
    }

    /**
     * WORKSPACE_MEMBER outbox payloadını doğrular ve canonical JsonObject döner.
     *
     * Güvenlik Invariantı:
     * Generic outbox üzerinden CREATE / JOIN işlemi desteklenmez (fail-closed).
     * Davetle katılım yalnızca `redeem_workspace_invitation_v1` RPC'si üzerinden yapılır.
     */
    fun parseAndValidateMember(
        operationId: String,
        entityId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        payloadJson: String,
    ): JsonObject {
        val (expectedWsId, expectedUserId) = WorkspaceMemberEntityId.decode(entityId)

        val jsonObject = parsePayloadJson(operationId, payloadJson)
        val keys = jsonObject.keys

        when (operation) {
            RemoteWriteOperation.CREATE -> {
                throw IllegalArgumentException(
                    "WORKSPACE_MEMBER için generic outbox CREATE/JOIN işlemi desteklenmez. " +
                        "Davetle katılım için redeem_workspace_invitation_v1 RPC kullanılmalıdır: $operationId"
                )
            }
            RemoteWriteOperation.UPDATE -> {
                require(baseVersion != null && baseVersion >= 1L) {
                    "WORKSPACE_MEMBER UPDATE işleminde baseVersion pozitif bir tamsayı olmalıdır: $baseVersion ($operationId)"
                }
                val allowedKeys = setOf("workspace_id", "user_id", "role_code")
                val forbidden = keys - allowedKeys
                if (forbidden.isNotEmpty()) {
                    throw IllegalArgumentException("WORKSPACE_MEMBER UPDATE payloadında izin verilmeyen alanlar var: $forbidden ($operationId)")
                }

                val wsId = jsonObject["workspace_id"]?.jsonPrimitive?.content
                val userId = jsonObject["user_id"]?.jsonPrimitive?.content
                require(wsId == expectedWsId && userId == expectedUserId) {
                    "Payload ($wsId, $userId) ile entityId ($entityId) uyuşmuyor: $operationId"
                }

                val roleCode = jsonObject["role_code"]?.jsonPrimitive?.content?.uppercase()
                require(roleCode != null && roleCode in VALID_ROLES) {
                    "Geçersiz veya eksik role_code: $roleCode ($operationId)"
                }
            }
            RemoteWriteOperation.DELETE -> {
                require(baseVersion != null && baseVersion >= 1L) {
                    "WORKSPACE_MEMBER DELETE işleminde baseVersion pozitif bir tamsayı olmalıdır: $baseVersion ($operationId)"
                }
                val allowedKeys = setOf("workspace_id", "user_id")
                val forbidden = keys - allowedKeys
                if (forbidden.isNotEmpty()) {
                    throw IllegalArgumentException("WORKSPACE_MEMBER DELETE payloadında izin verilmeyen alanlar var: $forbidden ($operationId)")
                }

                val wsId = jsonObject["workspace_id"]?.jsonPrimitive?.content
                val userId = jsonObject["user_id"]?.jsonPrimitive?.content
                require(wsId == expectedWsId && userId == expectedUserId) {
                    "Payload ($wsId, $userId) ile entityId ($entityId) uyuşmuyor: $operationId"
                }
            }
        }

        return jsonObject
    }

    private fun parsePayloadJson(operationId: String, payloadJson: String): JsonObject {
        require(payloadJson.isNotBlank()) {
            "Outbox payloadJson boş olamaz: $operationId"
        }
        val element = try {
            membershipPayloadJson.parseToJsonElement(payloadJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Payload geçerli bir JSON değil: $operationId", e)
        }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Payload kök elemanı JSON nesnesi olmalıdır: $operationId")
    }

    private fun String.requireUuid(field: String): String {
        if (!matches(UUID_REGEX)) {
            throw RemoteMappingException("Geçersiz UUID formatı ($field): '$this'")
        }
        return this
    }
}

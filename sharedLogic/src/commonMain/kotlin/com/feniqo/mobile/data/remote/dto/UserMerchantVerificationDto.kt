package com.feniqo.mobile.data.remote.dto

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.MerchantVerificationScope
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import com.feniqo.mobile.domain.model.UserMerchantDecision
import com.feniqo.mobile.domain.model.UserMerchantVerification
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserMerchantVerificationDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("workspace_id")
    val workspaceId: String? = null,
    @SerialName("normalized_alias")
    val normalizedAlias: String,
    @SerialName("decision_code")
    val decisionCode: String,
    @SerialName("merchant_id")
    val merchantId: String? = null,
    @SerialName("scope_code")
    val scopeCode: String = "PERSONAL",
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val version: Long? = null,
) {
    private data class ValidatedRemoteVerification(
        val id: String,
        val userId: String,
        val normalizedAlias: String,
        val decision: UserMerchantDecision,
        val createdInstant: Instant,
        val updatedInstant: Instant,
        val deletedInstant: Instant?,
        val version: Long,
    )

    private fun validateRemoteOrNull(): ValidatedRemoteVerification? {
        if (!UuidHelper.isCanonicalUuid(id)) return null
        if (!UuidHelper.isCanonicalUuid(userId)) return null
        if (workspaceId != null) return null
        if (scopeCode != "PERSONAL") return null

        if (normalizedAlias.isBlank() || normalizedAlias.length > Transaction.MAX_DESCRIPTION_LENGTH) return null
        val canonicalAlias = TurkishDescriptionNormalizer.normalize(normalizedAlias)
        if (normalizedAlias != canonicalAlias) return null
        if (normalizedAlias in DISALLOWED_STANDALONE_GENERIC_TOKENS) return null

        val expectedId =
            runCatching {
                UuidHelper.deterministicMerchantVerificationUuid(userId, normalizedAlias)
            }.getOrNull() ?: return null

        if (id != expectedId) return null

        val decision: UserMerchantDecision =
            when (decisionCode) {
                "ASSIGN" -> {
                    if (merchantId.isNullOrBlank()) return null
                    UserMerchantDecision.Assign(merchantId)
                }
                "BLOCK" -> {
                    if (merchantId != null) return null
                    UserMerchantDecision.Block
                }
                else -> return null
            }

        val createdInstant = runCatching { Instant.parse(createdAt) }.getOrNull() ?: return null
        val updatedInstant = updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null

        val deletedInstant =
            deletedAt?.let {
                runCatching { Instant.parse(it) }.getOrNull() ?: return null
            }

        if (version == null || version < 1L) return null

        return ValidatedRemoteVerification(
            id = id,
            userId = userId,
            normalizedAlias = normalizedAlias,
            decision = decision,
            createdInstant = createdInstant,
            updatedInstant = updatedInstant,
            deletedInstant = deletedInstant,
            version = version,
        )
    }

    /**
     * DTO'yu [UserMerchantVerification] domain modeline dönüştürür.
     * Herhangi bir kural veya deterministik ID ihlalinde fail-closed olarak null döner.
     */
    fun toDomainOrNull(): UserMerchantVerification? {
        val validated = validateRemoteOrNull() ?: return null
        return try {
            UserMerchantVerification(
                id = EntityId(validated.id),
                ownerId = EntityId(validated.userId),
                workspaceId = null,
                normalizedAlias = validated.normalizedAlias,
                decision = validated.decision,
                scope = MerchantVerificationScope.PERSONAL,
                createdAt = validated.createdInstant,
                updatedAt = validated.updatedInstant,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun toDomain(): UserMerchantVerification =
        toDomainOrNull() ?: throw IllegalArgumentException("Geçersiz UserMerchantVerificationDto kaydı: id=$id")

    fun toEntity(sync: SyncMetadata): UserMerchantVerificationEntity {
        val domain = toDomain()
        val ownerEntityId = domain.ownerId
        val scopeKey = "user:${ownerEntityId.value}"
        return UserMerchantVerificationEntity(
            id = domain.id.value,
            ownerId = ownerEntityId.value,
            workspaceId = null,
            scopeKey = scopeKey,
            normalizedAlias = domain.normalizedAlias,
            decisionCode =
                when (domain.decision) {
                    is UserMerchantDecision.Assign -> "ASSIGN"
                    is UserMerchantDecision.Block -> "BLOCK"
                },
            merchantId = (domain.decision as? UserMerchantDecision.Assign)?.merchantId,
            scopeCode = "PERSONAL",
            createdAtEpochMillis = domain.createdAt.toEpochMilliseconds(),
            sync = sync,
        )
    }

    fun toRemoteSyncMetadataOrNull(receivedAtEpochMillis: Long): SyncMetadata? {
        val validated = validateRemoteOrNull() ?: return null
        return SyncMetadata(
            syncStatus = SyncStatus.SYNCED.name,
            updatedAtEpochMillis = validated.updatedInstant.toEpochMilliseconds(),
            localUpdatedAtEpochMillis = receivedAtEpochMillis,
            deletedAtEpochMillis = validated.deletedInstant?.toEpochMilliseconds(),
            version = validated.version,
            baseVersion = validated.version,
            lastSyncError = null,
        )
    }

    fun toRemoteSyncMetadata(receivedAtEpochMillis: Long): SyncMetadata =
        toRemoteSyncMetadataOrNull(receivedAtEpochMillis)
            ?: throw IllegalArgumentException("Geçersiz UserMerchantVerificationDto kaydı: id=$id")
}

fun UserMerchantVerification.toDto(
    version: Long? = null,
    deletedAt: Instant? = null,
): UserMerchantVerificationDto =
    UserMerchantVerificationDto(
        id = id.value,
        userId = ownerId.value,
        workspaceId = null,
        normalizedAlias = normalizedAlias,
        decisionCode =
            when (decision) {
                is UserMerchantDecision.Assign -> "ASSIGN"
                is UserMerchantDecision.Block -> "BLOCK"
            },
        merchantId = (decision as? UserMerchantDecision.Assign)?.merchantId,
        scopeCode = "PERSONAL",
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
        deletedAt = deletedAt?.toString(),
        version = version,
    )

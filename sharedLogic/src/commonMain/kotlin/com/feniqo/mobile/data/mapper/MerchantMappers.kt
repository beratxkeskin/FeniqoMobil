package com.feniqo.mobile.data.mapper

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.MerchantVerificationScope
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import com.feniqo.mobile.domain.model.UserMerchantDecision
import com.feniqo.mobile.domain.model.UserMerchantVerification
import kotlinx.datetime.Instant

/**
 * [UserMerchantVerificationEntity] kaydını güvenli biçimde [UserMerchantVerification] domain modeline dönüştürür.
 * Herhangi bir şema veya iş kuralı ihlalinde fail-closed olarak null döner.
 */
fun UserMerchantVerificationEntity.toDomainOrNull(): UserMerchantVerification? {
    if (sync.version < 0) return null
    if (scopeCode != "PERSONAL") return null
    if (workspaceId != null) return null

    // Canonical lowercase UUID zorunluluğu: scopeKey hesaplanmadan önce doğrulanır.
    if (!UuidHelper.isCanonicalUuid(id)) return null
    if (!UuidHelper.isCanonicalUuid(ownerId)) return null

    val ownerEntityId = EntityId(ownerId)
    val expectedScopeKey = scopeKey(ownerEntityId, null)
    if (scopeKey != expectedScopeKey) return null

    if (normalizedAlias.isBlank() || normalizedAlias.length > Transaction.MAX_DESCRIPTION_LENGTH) return null
    val canonicalAlias = TurkishDescriptionNormalizer.normalize(normalizedAlias)
    if (normalizedAlias != canonicalAlias) return null
    if (normalizedAlias in DISALLOWED_STANDALONE_GENERIC_TOKENS) return null

    val expectedDeterministicId =
        runCatching {
            UuidHelper.deterministicMerchantVerificationUuid(ownerId, normalizedAlias)
        }.getOrNull() ?: return null
    if (id != expectedDeterministicId) return null

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

    return try {
        UserMerchantVerification(
            id = EntityId(id),
            ownerId = ownerEntityId,
            workspaceId = null,
            normalizedAlias = normalizedAlias,
            decision = decision,
            scope = MerchantVerificationScope.PERSONAL,
            createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
            updatedAt = Instant.fromEpochMilliseconds(sync.updatedAtEpochMillis),
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun UserMerchantVerificationEntity.toDomain(): UserMerchantVerification =
    toDomainOrNull() ?: throw IllegalArgumentException("Geçersiz UserMerchantVerificationEntity kaydı: id=$id")

fun UserMerchantVerification.toEntity(metadata: SyncMetadata): UserMerchantVerificationEntity =
    UserMerchantVerificationEntity(
        id = id.value,
        ownerId = ownerId.value,
        workspaceId = workspaceId?.value,
        scopeKey = scopeKey(ownerId, workspaceId),
        normalizedAlias = normalizedAlias,
        decisionCode =
            when (decision) {
                is UserMerchantDecision.Assign -> "ASSIGN"
                is UserMerchantDecision.Block -> "BLOCK"
            },
        merchantId =
            when (decision) {
                is UserMerchantDecision.Assign -> decision.merchantId
                is UserMerchantDecision.Block -> null
            },
        scopeCode =
            when (scope) {
                MerchantVerificationScope.PERSONAL -> "PERSONAL"
            },
        createdAtEpochMillis = createdAt.toEpochMilliseconds(),
        sync = metadata,
    )

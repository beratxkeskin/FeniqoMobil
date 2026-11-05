package com.feniqo.mobile.data.remote.dto

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.MerchantVerificationScope
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.UserMerchantDecision
import com.feniqo.mobile.domain.model.UserMerchantVerification
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserMerchantVerificationDtoMapperTest {
    private val userId = "67a3f892-12c4-4b9a-b8e7-9d8e7a6b5c4d"
    private val deterministicId = UuidHelper.deterministicMerchantVerificationUuid(userId, "NETFLIX")

    @Test
    fun mapsValidAssignDtoToDomainSuccessfully() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                deletedAt = null,
                version = 1L,
            )

        val domain = dto.toDomainOrNull()
        assertNotNull(domain)
        assertEquals(deterministicId, domain.id.value)
        assertEquals(userId, domain.ownerId.value)
        assertEquals("NETFLIX", domain.normalizedAlias)
        assertEquals(UserMerchantDecision.Assign("netflix"), domain.decision)
        assertEquals(MerchantVerificationScope.PERSONAL, domain.scope)
        assertEquals(Instant.parse("2026-10-15T10:00:00Z"), domain.createdAt)
        assertEquals(Instant.parse("2026-10-15T11:00:00Z"), domain.updatedAt)
    }

    @Test
    fun mapsValidBlockDtoToDomainSuccessfully() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "BLOCK",
                merchantId = null,
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                deletedAt = null,
                version = 2L,
            )

        val domain = dto.toDomainOrNull()
        assertNotNull(domain)
        assertEquals(UserMerchantDecision.Block, domain.decision)
    }

    @Test
    fun rejectsMismatchedDeterministicId() {
        val wrongId = "11111111-2222-3333-4444-555555555555"
        val dto =
            UserMerchantVerificationDto(
                id = wrongId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                version = 1L,
            )
        assertNull(dto.toDomainOrNull(), "Deterministik ID eşleşmeyen DTO fail-closed null dönmelidir.")
    }

    @Test
    fun rejectsWorkspaceIdNotNull() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = "22222222-3333-4444-5555-666666666666",
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                version = 1L,
            )
        assertNull(dto.toDomainOrNull())
    }

    @Test
    fun rejectsNonPersonalScope() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "WORKSPACE",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                version = 1L,
            )
        assertNull(dto.toDomainOrNull())
    }

    @Test
    fun rejectsInvalidAssignWithoutMerchantId() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = null,
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                version = 1L,
            )
        assertNull(dto.toDomainOrNull())
    }

    @Test
    fun rejectsInvalidBlockWithMerchantId() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "BLOCK",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                version = 1L,
            )
        assertNull(dto.toDomainOrNull())
    }

    @Test
    fun mapsDomainToDtoCorrectly() {
        val domain =
            UserMerchantVerification(
                id = EntityId(deterministicId),
                ownerId = EntityId(userId),
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decision = UserMerchantDecision.Assign("netflix"),
                scope = MerchantVerificationScope.PERSONAL,
                createdAt = Instant.parse("2026-10-15T10:00:00Z"),
                updatedAt = Instant.parse("2026-10-15T11:00:00Z"),
            )

        val dto = domain.toDto(version = 3L)
        assertEquals(deterministicId, dto.id)
        assertEquals(userId, dto.userId)
        assertEquals("NETFLIX", dto.normalizedAlias)
        assertEquals("ASSIGN", dto.decisionCode)
        assertEquals("netflix", dto.merchantId)
        assertEquals(3L, dto.version)
    }

    @Test
    fun mapsDtoToEntityCorrectly() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                version = 1L,
            )

        val sync =
            SyncMetadata(
                syncStatus = SyncStatus.SYNCED.name,
                updatedAtEpochMillis = 1000L,
                localUpdatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
                version = 1L,
                baseVersion = 1L,
                lastSyncError = null,
            )

        val entity = dto.toEntity(sync)
        assertEquals(deterministicId, entity.id)
        assertEquals(userId, entity.ownerId)
        assertEquals("user:$userId", entity.scopeKey)
        assertEquals("NETFLIX", entity.normalizedAlias)
        assertEquals("ASSIGN", entity.decisionCode)
        assertEquals("netflix", entity.merchantId)
    }

    @Test
    fun rejectsMissingUpdatedAt() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = null,
                version = 1L,
            )
        assertNull(dto.toDomainOrNull(), "Missing updatedAt fail-closed olarak null dönmelidir.")
        assertNull(dto.toRemoteSyncMetadataOrNull(1000L), "Missing updatedAt sync metadata için null dönmelidir.")
        val exception =
            assertFailsWith<IllegalArgumentException> {
                dto.toRemoteSyncMetadata(1000L)
            }
        assertNotNull(exception.message)
    }

    @Test
    fun rejectsMalformedUpdatedAt() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "not-a-valid-timestamp",
                version = 1L,
            )
        assertNull(dto.toDomainOrNull(), "Bozuk updatedAt null dönmelidir.")
        assertNull(dto.toRemoteSyncMetadataOrNull(1000L))
        assertFailsWith<IllegalArgumentException> {
            dto.toRemoteSyncMetadata(1000L)
        }
    }

    @Test
    fun rejectsMalformedDeletedAt() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                deletedAt = "invalid-deleted-at",
                version = 1L,
            )
        assertNull(dto.toDomainOrNull(), "Bozuk deletedAt sessizce yok sayılmamalı, null dönmelidir.")
        assertNull(dto.toRemoteSyncMetadataOrNull(1000L))
        assertFailsWith<IllegalArgumentException> {
            dto.toRemoteSyncMetadata(1000L)
        }
    }

    @Test
    fun rejectsMissingVersion() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                version = null,
            )
        assertNull(dto.toDomainOrNull(), "Eksik version null dönmelidir.")
        assertNull(dto.toRemoteSyncMetadataOrNull(1000L))
        assertFailsWith<IllegalArgumentException> {
            dto.toRemoteSyncMetadata(1000L)
        }
    }

    @Test
    fun rejectsVersionZero() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                version = 0L,
            )
        assertNull(dto.toDomainOrNull(), "Version 0 null dönmelidir.")
        assertNull(dto.toRemoteSyncMetadataOrNull(1000L))
        assertFailsWith<IllegalArgumentException> {
            dto.toRemoteSyncMetadata(1000L)
        }
    }

    @Test
    fun rejectsNegativeVersion() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                version = -5L,
            )
        assertNull(dto.toDomainOrNull(), "Negatif version null dönmelidir.")
        assertNull(dto.toRemoteSyncMetadataOrNull(1000L))
        assertFailsWith<IllegalArgumentException> {
            dto.toRemoteSyncMetadata(1000L)
        }
    }

    @Test
    fun mapsValidTombstoneDtoToSyncMetadataCorrectly() {
        val deletedTimestamp = "2026-10-15T12:00:00Z"
        val updatedTimestamp = "2026-10-15T12:00:00Z"
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "BLOCK",
                merchantId = null,
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = updatedTimestamp,
                deletedAt = deletedTimestamp,
                version = 4L,
            )

        val metadata = dto.toRemoteSyncMetadataOrNull(1700000000000L)
        assertNotNull(metadata)
        assertEquals(SyncStatus.SYNCED.name, metadata.syncStatus)
        assertEquals(Instant.parse(deletedTimestamp).toEpochMilliseconds(), metadata.deletedAtEpochMillis)
        assertEquals(Instant.parse(updatedTimestamp).toEpochMilliseconds(), metadata.updatedAtEpochMillis)
        assertEquals(4L, metadata.version)
        assertEquals(4L, metadata.baseVersion)
        assertEquals(1700000000000L, metadata.localUpdatedAtEpochMillis)

        assertNotNull(dto.toDomainOrNull())
    }

    @Test
    fun preservesValidActiveRecordBehavior() {
        val dto =
            UserMerchantVerificationDto(
                id = deterministicId,
                userId = userId,
                workspaceId = null,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
                scopeCode = "PERSONAL",
                createdAt = "2026-10-15T10:00:00Z",
                updatedAt = "2026-10-15T11:00:00Z",
                deletedAt = null,
                version = 1L,
            )

        val metadata = dto.toRemoteSyncMetadata(1700000000000L)
        assertNull(metadata.deletedAtEpochMillis)
        assertEquals(1L, metadata.version)
        assertEquals(Instant.parse("2026-10-15T11:00:00Z").toEpochMilliseconds(), metadata.updatedAtEpochMillis)
        assertNotNull(dto.toDomainOrNull())
    }
}

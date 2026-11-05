package com.feniqo.mobile.data.mapper

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.UserMerchantVerificationEntity
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.MerchantVerificationScope
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.UserMerchantDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserMerchantVerificationMapperTest {
    private val validOwnerId = "11111111-1111-4111-8111-111111111111"
    private val validScopeKey = "user:$validOwnerId"
    private val validVerificationId = UuidHelper.deterministicMerchantVerificationUuid(validOwnerId, "NETFLIX")

    private fun sampleEntity(
        id: String? = null,
        ownerId: String = this.validOwnerId,
        workspaceId: String? = null,
        scopeKey: String = this.validScopeKey,
        normalizedAlias: String = "NETFLIX",
        decisionCode: String = "ASSIGN",
        merchantId: String? = "netflix",
        scopeCode: String = "PERSONAL",
        createdAtEpochMs: Long = 1000L,
        version: Long = 1L,
        updatedAtEpochMs: Long = 2000L,
    ): UserMerchantVerificationEntity {
        val resolvedId =
            id ?: runCatching {
                UuidHelper.deterministicMerchantVerificationUuid(ownerId, normalizedAlias)
            }.getOrDefault(this.validVerificationId)

        return UserMerchantVerificationEntity(
            id = resolvedId,
            ownerId = ownerId,
            workspaceId = workspaceId,
            scopeKey = scopeKey,
            normalizedAlias = normalizedAlias,
            decisionCode = decisionCode,
            merchantId = merchantId,
            scopeCode = scopeCode,
            createdAtEpochMillis = createdAtEpochMs,
            sync =
                SyncMetadata(
                    syncStatus = "SYNCED",
                    updatedAtEpochMillis = updatedAtEpochMs,
                    localUpdatedAtEpochMillis = updatedAtEpochMs,
                    deletedAtEpochMillis = null,
                    version = version,
                    baseVersion = version - 1,
                    lastSyncError = null,
                ),
        )
    }

    @Test
    fun canonicalIdAndCanonicalOwner_mapsSuccessfully() {
        val entity = sampleEntity()
        val domain = entity.toDomainOrNull()

        assertNotNull(domain)
        assertEquals(EntityId(validVerificationId), domain.id)
        assertEquals(EntityId(validOwnerId), domain.ownerId)
        assertNull(domain.workspaceId)
        assertEquals("NETFLIX", domain.normalizedAlias)
        assertEquals(UserMerchantDecision.Assign("netflix"), domain.decision)
        assertEquals(MerchantVerificationScope.PERSONAL, domain.scope)
        assertEquals(1000L, domain.createdAt.toEpochMilliseconds())
        assertEquals(2000L, domain.updatedAt.toEpochMilliseconds())
    }

    @Test
    fun nonDeterministicVerificationId_isRejected() {
        val arbitraryUuid = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        assertNull(sampleEntity(id = arbitraryUuid).toDomainOrNull())
    }

    @Test
    fun malformedVerificationId_isRejected() {
        assertNull(sampleEntity(id = "verif-1").toDomainOrNull())
        assertNull(sampleEntity(id = "not-a-uuid").toDomainOrNull())
        assertNull(sampleEntity(id = "   ").toDomainOrNull())
        assertNull(sampleEntity(id = "").toDomainOrNull())
    }

    @Test
    fun nonCanonicalOrUppercaseVerificationId_isRejected() {
        val uppercaseUuid = "CCCCCCCC-CCCC-4CCC-8CCC-CCCCCCCCCCCC"
        assertNull(sampleEntity(id = uppercaseUuid).toDomainOrNull())

        val mixedCaseUuid = "cccccccc-cccc-4ccc-8ccc-CCCCCCCCCCCC"
        assertNull(sampleEntity(id = mixedCaseUuid).toDomainOrNull())
    }

    @Test
    fun compact32HexVerificationId_isRejected() {
        val compactHex = "cccccccccccc4ccc8ccccccccccccccc"
        assertNull(sampleEntity(id = compactHex).toDomainOrNull())
    }

    @Test
    fun malformedOwnerId_isRejected() {
        assertNull(sampleEntity(ownerId = "owner-1").toDomainOrNull())
        assertNull(sampleEntity(ownerId = "not-a-uuid").toDomainOrNull())
        assertNull(sampleEntity(ownerId = "   ").toDomainOrNull())
        assertNull(sampleEntity(ownerId = "").toDomainOrNull())
    }

    @Test
    fun nonCanonicalOrUppercaseOwnerId_isRejected() {
        val uppercaseOwner = "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA"
        assertNull(sampleEntity(ownerId = uppercaseOwner).toDomainOrNull())

        val mixedCaseOwner = "Aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        assertNull(sampleEntity(ownerId = mixedCaseOwner).toDomainOrNull())
    }

    @Test
    fun compact32HexOwnerId_isRejected() {
        val compactHexOwner = "11111111111141118111111111111111"
        assertNull(sampleEntity(ownerId = compactHexOwner).toDomainOrNull())
    }

    @Test
    fun validBlock_mapsToDomainModelSuccessfully() {
        val entity =
            sampleEntity(
                decisionCode = "BLOCK",
                merchantId = null,
                normalizedAlias = "BILINMEYEN POS",
            )
        val domain = entity.toDomainOrNull()

        assertNotNull(domain)
        assertEquals("BILINMEYEN POS", domain.normalizedAlias)
        assertEquals(UserMerchantDecision.Block, domain.decision)
        assertEquals(MerchantVerificationScope.PERSONAL, domain.scope)
    }

    @Test
    fun assignWithNullOrBlankMerchant_isRejected() {
        val nullMerchant = sampleEntity(decisionCode = "ASSIGN", merchantId = null)
        assertNull(nullMerchant.toDomainOrNull())

        val blankMerchant = sampleEntity(decisionCode = "ASSIGN", merchantId = "   ")
        assertNull(blankMerchant.toDomainOrNull())
    }

    @Test
    fun blockWithNonNullMerchant_isRejected() {
        val entity = sampleEntity(decisionCode = "BLOCK", merchantId = "netflix")
        assertNull(entity.toDomainOrNull())
    }

    @Test
    fun workspaceScopeOrNonNullableWorkspaceId_isRejectedInC2() {
        val nonNullWorkspace = sampleEntity(workspaceId = "ws-1")
        assertNull(nonNullWorkspace.toDomainOrNull())

        val workspaceScopeCode = sampleEntity(scopeCode = "WORKSPACE")
        assertNull(workspaceScopeCode.toDomainOrNull())
    }

    @Test
    fun malformedScopeKey_isRejected() {
        val mismatchedScopeKey = sampleEntity(scopeKey = "user:different-user")
        assertNull(mismatchedScopeKey.toDomainOrNull())

        val workspaceKey = sampleEntity(scopeKey = "workspace:ws-1")
        assertNull(workspaceKey.toDomainOrNull())
    }

    @Test
    fun unknownDecisionOrScopeCode_isRejected() {
        val unknownDecision = sampleEntity(decisionCode = "UNKNOWN")
        assertNull(unknownDecision.toDomainOrNull())

        val unknownScope = sampleEntity(scopeCode = "ORGANIZATION")
        assertNull(unknownScope.toDomainOrNull())
    }

    @Test
    fun standaloneGenericTokenAlias_isRejected() {
        for (token in listOf("MARKET", "PREMIUM", "APPLE", "SHOP", "STORE", "YEMEK")) {
            val entity = sampleEntity(normalizedAlias = token)
            assertNull(entity.toDomainOrNull(), "Token $token tek başına reddedilmeliydi")
        }
    }

    @Test
    fun nonCanonicalOrBlankAlias_isRejected() {
        val lowerCase = sampleEntity(normalizedAlias = "netflix")
        assertNull(lowerCase.toDomainOrNull())

        val unnormalizedSpaces = sampleEntity(normalizedAlias = "NETFLIX   UYELIK")
        assertNull(unnormalizedSpaces.toDomainOrNull())

        val blank = sampleEntity(normalizedAlias = "   ")
        assertNull(blank.toDomainOrNull())
    }

    @Test
    fun aliasExceedingMaxDescriptionLength_isRejected() {
        val tooLong = sampleEntity(normalizedAlias = "A".repeat(Transaction.MAX_DESCRIPTION_LENGTH + 1))
        assertNull(tooLong.toDomainOrNull())

        val validLength = sampleEntity(normalizedAlias = "A".repeat(Transaction.MAX_DESCRIPTION_LENGTH))
        assertNotNull(validLength.toDomainOrNull())
    }

    @Test
    fun negativeVersion_isRejected() {
        val entity = sampleEntity(version = -1L)
        assertNull(entity.toDomainOrNull())
    }

    @Test
    fun toDomain_throwsOnInvalidRecord() {
        val invalid = sampleEntity(decisionCode = "INVALID")
        val error =
            assertFailsWith<IllegalArgumentException> {
                invalid.toDomain()
            }
        assertTrue(error.message?.contains("Geçersiz UserMerchantVerificationEntity") == true)
    }
}

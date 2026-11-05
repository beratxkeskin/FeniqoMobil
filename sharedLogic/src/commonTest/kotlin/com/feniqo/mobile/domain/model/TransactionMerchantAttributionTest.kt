package com.feniqo.mobile.domain.model

import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.mapper.mergePreservingLocalMerchant
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationRepository
import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog
import com.feniqo.mobile.domain.usecase.TransactionMerchantAttributionResolver
import com.feniqo.mobile.domain.usecase.TransactionMerchantResolutionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionMerchantAttributionTest {
    private val now = Instant.fromEpochMilliseconds(1700000000000)

    private fun sampleTransaction(
        merchantInfo: TransactionMerchantInfo? = null,
        description: String? = "Test Transaction",
    ) = Transaction(
        id = EntityId("tx-001"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        amount = Money(10000, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = EntityId("cat-1"),
        description = description,
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate.parse("2026-10-14"),
        receiptPath = null,
        installment = null,
        createdAt = now,
        merchantInfo = merchantInfo,
    )

    @Test
    fun transactionMerchantInfo_validParameters_constructedSuccessfully() {
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("netflix"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 1,
            )
        assertEquals("netflix", info.merchantId.value)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, info.matchSource)
        assertEquals(95, info.confidence)
        assertEquals(1, info.catalogVersion)
    }

    @Test
    fun transactionMerchantInfo_invalidParameters_throwsIllegalArgumentException() {
        // Boş merchantId
        assertFailsWith<IllegalArgumentException> {
            TransactionMerchantInfo(
                merchantId = EntityId(""),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 90,
                catalogVersion = 1,
            )
        }

        // Negatif confidence
        assertFailsWith<IllegalArgumentException> {
            TransactionMerchantInfo(
                merchantId = EntityId("netflix"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = -1,
                catalogVersion = 1,
            )
        }

        // 100'den büyük confidence
        assertFailsWith<IllegalArgumentException> {
            TransactionMerchantInfo(
                merchantId = EntityId("netflix"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 101,
                catalogVersion = 1,
            )
        }

        // 0 veya negatif catalogVersion
        assertFailsWith<IllegalArgumentException> {
            TransactionMerchantInfo(
                merchantId = EntityId("netflix"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 90,
                catalogVersion = 0,
            )
        }
    }

    @Test
    fun transaction_nullMerchantInfo_exposesNullPropertyGetters() {
        val tx = sampleTransaction(merchantInfo = null)
        assertNull(tx.merchantInfo)
        assertNull(tx.merchantId)
        assertNull(tx.merchantMatchSource)
        assertNull(tx.merchantConfidence)
        assertNull(tx.merchantCatalogVersion)
    }

    @Test
    fun transaction_presentMerchantInfo_exposesMatchingPropertyGetters() {
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("spotify"),
                matchSource = MerchantMatchSource.STRONG_BRAND,
                confidence = 85,
                catalogVersion = 1,
            )
        val tx = sampleTransaction(merchantInfo = info)
        assertEquals(EntityId("spotify"), tx.merchantId)
        assertEquals(MerchantMatchSource.STRONG_BRAND, tx.merchantMatchSource)
        assertEquals(85, tx.merchantConfidence)
        assertEquals(1, tx.merchantCatalogVersion)
    }

    @Test
    fun transaction_secondaryConstructor_validatesCoherence() {
        // Tümü null -> başarılı
        val txNull =
            Transaction(
                id = EntityId("tx-002"),
                ownerId = EntityId("user-1"),
                workspaceId = null,
                amount = Money(5000, Currency.TRY),
                type = TransactionType.EXPENSE,
                categoryId = EntityId("cat-1"),
                description = "Kahve",
                paymentMethod = PaymentMethod.CASH,
                transactionDate = LocalDate.parse("2026-10-14"),
                receiptPath = null,
                installment = null,
                createdAt = now,
                merchantId = null,
                merchantMatchSource = null,
                merchantConfidence = null,
                merchantCatalogVersion = null,
            )
        assertNull(txNull.merchantInfo)

        // Tümü dolu -> başarılı
        val txFull =
            Transaction(
                id = EntityId("tx-003"),
                ownerId = EntityId("user-1"),
                workspaceId = null,
                amount = Money(5000, Currency.TRY),
                type = TransactionType.EXPENSE,
                categoryId = EntityId("cat-1"),
                description = "Kahve",
                paymentMethod = PaymentMethod.CASH,
                transactionDate = LocalDate.parse("2026-10-14"),
                receiptPath = null,
                installment = null,
                createdAt = now,
                merchantId = EntityId("starbucks"),
                merchantMatchSource = MerchantMatchSource.STRONG_BRAND,
                merchantConfidence = 90,
                merchantCatalogVersion = 1,
            )
        assertNotNull(txFull.merchantInfo)
        assertEquals(EntityId("starbucks"), txFull.merchantId)

        // Kısmi (örn: merchantId var ama confidence null) -> başarısız
        assertFailsWith<IllegalArgumentException> {
            Transaction(
                id = EntityId("tx-004"),
                ownerId = EntityId("user-1"),
                workspaceId = null,
                amount = Money(5000, Currency.TRY),
                type = TransactionType.EXPENSE,
                categoryId = EntityId("cat-1"),
                description = "Kahve",
                paymentMethod = PaymentMethod.CASH,
                transactionDate = LocalDate.parse("2026-10-14"),
                receiptPath = null,
                installment = null,
                createdAt = now,
                merchantId = EntityId("starbucks"),
                merchantMatchSource = MerchantMatchSource.STRONG_BRAND,
                merchantConfidence = null,
                merchantCatalogVersion = 1,
            )
        }
    }

    @Test
    fun starterMerchantCatalog_allMerchantsHavePositiveCatalogVersion() {
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(151, merchants.size, "Katalog tam olarak 151 merchant içermelidir")
        merchants.forEach { m ->
            assertTrue(m.catalogVersion > 0, "Merchant ${m.id} katalog sürümü pozitif olmalıdır: ${m.catalogVersion}")
            assertEquals(StarterMerchantCatalog.CATALOG_VERSION, m.catalogVersion)
        }
    }

    @Test
    fun financeMappers_roundTrip_preservesMerchantAttribution() {
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("netflix"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 1,
            )
        val domain = sampleTransaction(merchantInfo = info)
        val sync = SyncMetadata("SYNCED", 1000, 1000, null, 1, null, null)

        val entity = domain.toEntity(sync)
        assertEquals("netflix", entity.merchantId)
        assertEquals("EXACT_ALIAS", entity.merchantMatchSourceCode)
        assertEquals(95, entity.merchantConfidence)
        assertEquals(1, entity.merchantCatalogVersion)

        val decoded = entity.toDomain()
        assertNotNull(decoded.merchantInfo)
        assertEquals(EntityId("netflix"), decoded.merchantId)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, decoded.merchantMatchSource)
        assertEquals(95, decoded.merchantConfidence)
        assertEquals(1, decoded.merchantCatalogVersion)
    }

    @Test
    fun financeMappers_roundTrip_nullMerchant_remainsNull() {
        val domain = sampleTransaction(merchantInfo = null)
        val sync = SyncMetadata("SYNCED", 1000, 1000, null, 1, null, null)

        val entity = domain.toEntity(sync)
        assertNull(entity.merchantId)
        assertNull(entity.merchantMatchSourceCode)
        assertNull(entity.merchantConfidence)
        assertNull(entity.merchantCatalogVersion)

        val decoded = entity.toDomain()
        assertNull(decoded.merchantInfo)
    }

    @Test
    fun financeMappers_failClosed_onCorruptedOrPartialLocalMerchantColumns() {
        val baseEntity = sampleTransaction().toEntity(SyncMetadata("SYNCED", 1000, 1000, null, 1, null, null))

        // 1. merchant_id var ama confidence null
        val corrupted1 =
            baseEntity.copy(
                merchantId = "netflix",
                merchantMatchSourceCode = "EXACT_ALIAS",
                merchantConfidence = null,
                merchantCatalogVersion = 1,
            )
        assertNull(corrupted1.toDomain().merchantInfo)

        // 2. merchant_id var ama matchSourceCode geçersiz
        val corrupted2 =
            baseEntity.copy(
                merchantId = "netflix",
                merchantMatchSourceCode = "UNKNOWN_CODE",
                merchantConfidence = 90,
                merchantCatalogVersion = 1,
            )
        assertNull(corrupted2.toDomain().merchantInfo)

        // 3. confidence 100'den büyük
        val corrupted3 =
            baseEntity.copy(
                merchantId = "netflix",
                merchantMatchSourceCode = "EXACT_ALIAS",
                merchantConfidence = 150,
                merchantCatalogVersion = 1,
            )
        assertNull(corrupted3.toDomain().merchantInfo)

        // 4. catalog_version <= 0
        val corrupted4 =
            baseEntity.copy(
                merchantId = "netflix",
                merchantMatchSourceCode = "EXACT_ALIAS",
                merchantConfidence = 90,
                merchantCatalogVersion = 0,
            )
        assertNull(corrupted4.toDomain().merchantInfo)
    }

    @Test
    fun mergePreservingLocalMerchant_preservesAttributionWhenRemoteDescriptionUnchanged() {
        val local =
            sampleTransaction(
                description = "Netflix",
                merchantInfo =
                    TransactionMerchantInfo(
                        merchantId = EntityId("netflix"),
                        matchSource = MerchantMatchSource.EXACT_ALIAS,
                        confidence = 95,
                        catalogVersion = 1,
                    ),
            ).toEntity(SyncMetadata("SYNCED", 1000, 1000, null, 1, null, null))

        val remoteSameDescription =
            sampleTransaction(description = "Netflix", merchantInfo = null)
                .toEntity(SyncMetadata("SYNCED", 2000, 2000, null, 2, 1, null))

        val merged = remoteSameDescription.mergePreservingLocalMerchant(local)
        assertEquals("netflix", merged.merchantId)
        assertEquals("EXACT_ALIAS", merged.merchantMatchSourceCode)
        assertEquals(95, merged.merchantConfidence)
        assertEquals(1, merged.merchantCatalogVersion)
        assertEquals(2L, merged.sync.version)
    }

    @Test
    fun mergePreservingLocalMerchant_reResolvesWhenRemoteDescriptionChangesToKnownMerchant() {
        val local =
            sampleTransaction(
                description = "Netflix",
                merchantInfo =
                    TransactionMerchantInfo(
                        merchantId = EntityId("netflix"),
                        matchSource = MerchantMatchSource.EXACT_ALIAS,
                        confidence = 95,
                        catalogVersion = 1,
                    ),
            ).toEntity(SyncMetadata("SYNCED", 1000, 1000, null, 1, null, null))

        val candidateSpotify = TransactionMerchantAttributionResolver.resolve("Spotify")
        val remoteChangedToSpotify =
            sampleTransaction(description = "Spotify", merchantInfo = candidateSpotify)
                .toEntity(SyncMetadata("SYNCED", 2000, 2000, null, 2, 1, null))

        val merged = remoteChangedToSpotify.mergePreservingLocalMerchant(local)
        assertEquals("spotify", merged.merchantId)
        assertEquals(95, merged.merchantConfidence)
        assertEquals(10, merged.merchantCatalogVersion)
        assertEquals("Spotify", merged.description)
        assertEquals(2L, merged.sync.version)
    }

    @Test
    fun mergePreservingLocalMerchant_clearsAttributionWhenRemoteDescriptionChangesToUnknown() {
        val local =
            sampleTransaction(
                description = "Netflix",
                merchantInfo =
                    TransactionMerchantInfo(
                        merchantId = EntityId("netflix"),
                        matchSource = MerchantMatchSource.EXACT_ALIAS,
                        confidence = 95,
                        catalogVersion = 1,
                    ),
            ).toEntity(SyncMetadata("SYNCED", 1000, 1000, null, 1, null, null))

        val remoteChangedToUnknown =
            sampleTransaction(description = "Bilinmeyen Bir Harcama", merchantInfo = null)
                .toEntity(SyncMetadata("SYNCED", 2000, 2000, null, 2, 1, null))

        val merged = remoteChangedToUnknown.mergePreservingLocalMerchant(local)
        assertNull(merged.merchantId)
        assertNull(merged.merchantMatchSourceCode)
        assertNull(merged.merchantConfidence)
        assertNull(merged.merchantCatalogVersion)
        assertEquals("Bilinmeyen Bir Harcama", merged.description)
        assertEquals(2L, merged.sync.version)
    }

    @Test
    fun mergePreservingLocalMerchant_resolvesFromRemoteDescriptionWhenLocalIsNull() {
        val remoteSpotify =
            sampleTransaction(
                description = "Spotify",
                merchantInfo = TransactionMerchantAttributionResolver.resolve("Spotify"),
            ).toEntity(SyncMetadata("SYNCED", 2000, 2000, null, 1, null, null))
        val mergedSpotify = remoteSpotify.mergePreservingLocalMerchant(null)
        assertEquals("spotify", mergedSpotify.merchantId)

        val remoteUnknown =
            sampleTransaction(
                description = "Bilinmeyen",
                merchantInfo = TransactionMerchantAttributionResolver.resolve("Bilinmeyen"),
            ).toEntity(SyncMetadata("SYNCED", 2000, 2000, null, 1, null, null))
        val mergedUnknown = remoteUnknown.mergePreservingLocalMerchant(null)
        assertNull(mergedUnknown.merchantId)
    }

    @Test
    fun attributionResolver_resolvesKnownMerchantsAndFailsClosedOnAmbiguousOrExcluded() {
        // Netflix exact match
        val netflix = TransactionMerchantAttributionResolver.resolve("Netflix")
        assertNotNull(netflix)
        assertEquals("netflix", netflix.merchantId.value)
        assertEquals(95, netflix.confidence)

        // BİM tam kullanıcı girdisi
        val bim = TransactionMerchantAttributionResolver.resolve("BİM")
        assertNotNull(bim)
        assertEquals("bim", bim.merchantId.value)
        assertEquals(95, bim.confidence)

        // Bilinmeyen açıklama
        assertNull(TransactionMerchantAttributionResolver.resolve("Rastgele Bir Odeme"))

        // Excluded işlem (Maaş)
        assertNull(TransactionMerchantAttributionResolver.resolve("Maaş Ödemesi"))

        // Boş / null
        assertNull(TransactionMerchantAttributionResolver.resolve(null))
        assertNull(TransactionMerchantAttributionResolver.resolve("   "))
    }

    @Test
    fun attributionResolver_withPersonalAssign_produces100UserVerifiedInfo() {
        val info =
            TransactionMerchantAttributionResolver.resolve(
                description = "POS MIGROS",
                decision = UserMerchantDecision.Assign("shell"),
            )
        assertNotNull(info)
        assertEquals("shell", info.merchantId.value)
        assertEquals(MerchantMatchSource.USER_VERIFIED, info.matchSource)
        assertEquals(100, info.confidence)
        assertEquals(StarterMerchantCatalog.CATALOG_VERSION, info.catalogVersion)
    }

    @Test
    fun attributionResolver_withPersonalBlock_producesNull() {
        val info =
            TransactionMerchantAttributionResolver.resolve(
                description = "Netflix",
                decision = UserMerchantDecision.Block,
            )
        assertNull(info)
    }

    @Test
    fun attributionResolver_withInvalidAssign_producesNull() {
        val info =
            TransactionMerchantAttributionResolver.resolve(
                description = "Netflix",
                decision = UserMerchantDecision.Assign("non_existent_merchant"),
            )
        assertNull(info)
    }

    @Test
    fun attributionResolver_withoutDecision_preservesSystemAttribution() {
        val netflix = TransactionMerchantAttributionResolver.resolve("Netflix")
        assertNotNull(netflix)
        assertEquals("netflix", netflix.merchantId.value)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, netflix.matchSource)
        assertEquals(95, netflix.confidence)
    }

    private class TestVerificationRepository(
        private val onLookup: (String) -> UserMerchantVerificationLookupResult,
    ) : UserMerchantVerificationRepository {
        override fun observeActiveVerifications(): Flow<List<com.feniqo.mobile.domain.model.UserMerchantVerification>> =
            flowOf(emptyList<com.feniqo.mobile.domain.model.UserMerchantVerification>())

        override suspend fun getDecisionForAlias(rawOrNormalizedAlias: String): UserMerchantVerificationLookupResult =
            onLookup(rawOrNormalizedAlias)

        override suspend fun assignMerchant(
            alias: String,
            merchantId: String,
        ): RepositoryResult<com.feniqo.mobile.domain.model.UserMerchantVerification> = throw UnsupportedOperationException()

        override suspend fun blockMerchant(alias: String): RepositoryResult<com.feniqo.mobile.domain.model.UserMerchantVerification> =
            throw UnsupportedOperationException()

        override suspend fun deleteVerification(alias: String): RepositoryResult<Unit> = throw UnsupportedOperationException()
    }

    @Test
    fun resolveWithDecision_whenExcluded_neverCallsRepositoryAndReturnsSuccessNull() =
        runTest {
            var lookupCount = 0
            val repo =
                TestVerificationRepository {
                    lookupCount++
                    throw IllegalStateException("Should not be called for excluded transactions")
                }

            val result =
                TransactionMerchantAttributionResolver.resolveWithDecision(
                    description = "Maaş Ödemesi",
                    verificationRepository = repo,
                )

            assertIs<TransactionMerchantResolutionResult.Success>(result)
            assertNull(result.merchantInfo)
            assertEquals(0, lookupCount, "Excluded açıklamalar için repository getDecisionForAlias kesinlikle çağrılmamalıdır")
        }

    @Test
    fun resolveWithDecision_whenAssignWithUnknownMerchantId_returnsInvalidVerification() =
        runTest {
            val repo =
                TestVerificationRepository {
                    UserMerchantVerificationLookupResult.Decision(UserMerchantDecision.Assign("non_existent_merchant_id_999"))
                }

            val result =
                TransactionMerchantAttributionResolver.resolveWithDecision(
                    description = "Özel Market",
                    verificationRepository = repo,
                )

            assertIs<TransactionMerchantResolutionResult.InvalidVerification>(result)
            assertEquals("non_existent_merchant_id_999", result.id)
        }

    @Test
    fun resolveWithDecision_whenAssignWithKnownMerchantId_returnsSuccessWithUserVerified() =
        runTest {
            val repo =
                TestVerificationRepository {
                    UserMerchantVerificationLookupResult.Decision(UserMerchantDecision.Assign("shell"))
                }

            val result =
                TransactionMerchantAttributionResolver.resolveWithDecision(
                    description = "POS MIGROS",
                    verificationRepository = repo,
                )

            assertIs<TransactionMerchantResolutionResult.Success>(result)
            assertNotNull(result.merchantInfo)
            assertEquals("shell", result.merchantInfo?.merchantId?.value)
            assertEquals(MerchantMatchSource.USER_VERIFIED, result.merchantInfo?.matchSource)
            assertEquals(100, result.merchantInfo?.confidence)
        }

    @Test
    fun resolveWithDecision_whenBlock_returnsSuccessNullWithoutCatalogFallback() =
        runTest {
            val repo =
                TestVerificationRepository {
                    UserMerchantVerificationLookupResult.Decision(UserMerchantDecision.Block)
                }

            val result =
                TransactionMerchantAttributionResolver.resolveWithDecision(
                    description = "Netflix",
                    verificationRepository = repo,
                )

            assertIs<TransactionMerchantResolutionResult.Success>(result)
            assertNull(result.merchantInfo, "BLOCK kararı bilinen katalog tahminini bastırıp null üretmelidir")
        }

    @Test
    fun resolveWithDecision_whenNotFound_usesCatalogMatch() =
        runTest {
            val repo =
                TestVerificationRepository {
                    UserMerchantVerificationLookupResult.NotFound
                }

            val result =
                TransactionMerchantAttributionResolver.resolveWithDecision(
                    description = "Netflix",
                    verificationRepository = repo,
                )

            assertIs<TransactionMerchantResolutionResult.Success>(result)
            assertNotNull(result.merchantInfo)
            assertEquals("netflix", result.merchantInfo?.merchantId?.value)
            assertEquals(MerchantMatchSource.EXACT_ALIAS, result.merchantInfo?.matchSource)
        }

    @Test
    fun catalogV10Snapshot_andAttributionVersionRegressions() {
        // 1. StarterMerchantCatalog.CATALOG_VERSION == 10
        assertEquals(10, StarterMerchantCatalog.CATALOG_VERSION)

        // 2. Katalogdaki 151 merchant'ın tamamı catalogVersion == 10
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(151, merchants.size)
        assertTrue(merchants.all { it.catalogVersion == 10 }, "Tüm merchant'lar v10 olmalıdır")

        // 3. Yeni merchant otomatik tanıma sonucu oluşturulan TransactionMerchantInfo.catalogVersion == 10
        val newMerchantInfo = TransactionMerchantAttributionResolver.resolve("İstanbulkart")
        assertNotNull(newMerchantInfo)
        assertEquals(EntityId("istanbulkart"), newMerchantInfo.merchantId)
        assertEquals(10, newMerchantInfo.catalogVersion)

        // 4. v1..v9 tarihsel sürüm kabulü: mapper round-trip ve tarihsel catalogVersion korunması
        val historicalVersions =
            listOf(
                1 to "migros",
                2 to "migros",
                3 to "kofteci_yusuf",
                4 to "gratis",
                5 to "watsons",
                6 to "turknet",
                7 to "istanbulkart",
                8 to "ajet",
                9 to "tarim_kredi",
            )
        historicalVersions.forEach { (version, merchantId) ->
            val historicalInfo =
                TransactionMerchantInfo(
                    merchantId = EntityId(merchantId),
                    matchSource = MerchantMatchSource.EXACT_ALIAS,
                    confidence = 95,
                    catalogVersion = version,
                )
            val historicalTx = sampleTransaction(merchantInfo = historicalInfo)
            assertEquals(version, historicalTx.merchantCatalogVersion)
            val entity = historicalTx.toEntity(SyncMetadata("SYNCED", 1000, 1000, null, 1, null, null))
            assertEquals(version, entity.merchantCatalogVersion)
            val decoded = entity.toDomain()
            assertNotNull(decoded.merchantInfo)
            assertEquals(version, decoded.merchantCatalogVersion)
        }

        // 5. Gelecek v11 sürümü: persistence/mapper round-trip ve sürümün korunması
        // Not: Bu test sunum katmanı fail-closed davranışını değil, persistence/mapper round-trip davranışını kanıtlar.
        val futureInfo =
            TransactionMerchantInfo(
                merchantId = EntityId("migros"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 11,
            )
        val futureTx = sampleTransaction(merchantInfo = futureInfo)
        assertEquals(11, futureTx.merchantCatalogVersion)
        val entityFuture = futureTx.toEntity(SyncMetadata("SYNCED", 1000, 1000, null, 1, null, null))
        assertEquals(11, entityFuture.merchantCatalogVersion)
        val decodedFuture = entityFuture.toDomain()
        assertNotNull(decodedFuture.merchantInfo)
        assertEquals(11, decodedFuture.merchantCatalogVersion)
    }
}

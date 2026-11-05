package com.feniqo.mobile.data.backup

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.MerchantMatchSource
import com.feniqo.mobile.domain.model.UserMerchantDecision
import com.feniqo.mobile.domain.model.UserMerchantVerification
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BackupImportPlannerTest {
    private class FakeUserMerchantVerificationRepository(
        private val decisions: Map<String, UserMerchantVerificationLookupResult> = emptyMap(),
    ) : UserMerchantVerificationRepository {
        override fun observeActiveVerifications(): Flow<List<UserMerchantVerification>> = flowOf(emptyList())

        override suspend fun getDecisionForAlias(rawOrNormalizedAlias: String): UserMerchantVerificationLookupResult =
            decisions[rawOrNormalizedAlias] ?: UserMerchantVerificationLookupResult.NotFound

        override suspend fun assignMerchant(
            alias: String,
            merchantId: String,
        ): RepositoryResult<UserMerchantVerification> = throw UnsupportedOperationException()

        override suspend fun blockMerchant(alias: String): RepositoryResult<UserMerchantVerification> =
            throw UnsupportedOperationException()

        override suspend fun deleteVerification(alias: String): RepositoryResult<Unit> = throw UnsupportedOperationException()
    }

    @Test
    fun plan_remapsIdsAndNeverRestoresOwnerReceiptOrWorkspaceData() =
        runTest {
            var sequence = 0
            val planner =
                BackupImportPlanner(
                    idGenerator = EntityIdGenerator { EntityId("new-${++sequence}") },
                    userMerchantVerificationRepository = FakeUserMerchantVerificationRepository(),
                )
            val backup =
                FeniqoBackupV1(
                    createdAt = "2026-09-10T12:00:00Z",
                    categories = listOf(BackupCategoryV1("old-cat", "Market", "EXPENSE", "#112233", "cart")),
                    transactions =
                        listOf(
                            BackupTransactionV1(
                                "old-tx",
                                100,
                                "TRY",
                                "EXPENSE",
                                "old-cat",
                                null,
                                "CASH",
                                "2026-09-10",
                                "2026-09-10T10:00:00Z",
                                BackupInstallmentV1("old-group", 1, 2),
                            ),
                            BackupTransactionV1(
                                "old-tx-2",
                                200,
                                "TRY",
                                "EXPENSE",
                                "old-cat",
                                null,
                                "CASH",
                                "2026-09-10",
                                "2026-09-10T10:00:00Z",
                                BackupInstallmentV1("old-group", 2, 2),
                            ),
                        ),
                )

            val plan = planner.plan(backup, EntityId("current-owner"))

            assertNotEquals(
                "old-cat",
                plan.categories
                    .single()
                    .id.value,
            )
            assertEquals(plan.categories.single().id, plan.transactions.first().categoryId)
            assertEquals(plan.transactions[0].installment?.groupId, plan.transactions[1].installment?.groupId)
            assertEquals(setOf("current-owner"), plan.transactions.map { it.ownerId.value }.toSet())
            assertNull(plan.transactions.first().workspaceId)
            assertNull(plan.transactions.first().receiptPath)
        }

    @Test
    fun plan_recognizesKnownMerchantsAndLeavesOthersNull_withoutModifyingV1Format() =
        runTest {
            var sequence = 0
            val planner =
                BackupImportPlanner(
                    idGenerator = EntityIdGenerator { EntityId("new-${++sequence}") },
                    userMerchantVerificationRepository = FakeUserMerchantVerificationRepository(),
                )
            val backup =
                FeniqoBackupV1(
                    createdAt = "2026-09-10T12:00:00Z",
                    categories = listOf(BackupCategoryV1("old-cat", "Market", "EXPENSE", "#112233", "cart")),
                    transactions =
                        listOf(
                            BackupTransactionV1(
                                id = "tx-bim",
                                amountMinor = 5000L,
                                currency = "TRY",
                                type = "EXPENSE",
                                categoryId = "old-cat",
                                description = "BİM",
                                paymentMethod = "CASH",
                                transactionDate = "2026-09-10",
                                createdAt = "2026-09-10T10:00:00Z",
                            ),
                            BackupTransactionV1(
                                id = "tx-unknown",
                                amountMinor = 7000L,
                                currency = "TRY",
                                type = "EXPENSE",
                                categoryId = "old-cat",
                                description = "Bilinmeyen Bakkal",
                                paymentMethod = "CASH",
                                transactionDate = "2026-09-10",
                                createdAt = "2026-09-10T10:00:00Z",
                            ),
                        ),
                )

            val plan = planner.plan(backup, EntityId("current-owner"))
            assertEquals(2, plan.transactions.size)

            val bimTx = plan.transactions.first { it.description == "BİM" }
            assertNotNull(bimTx.merchantInfo)
            assertEquals("bim", bimTx.merchantInfo?.merchantId?.value)
            assertEquals(95, bimTx.merchantInfo?.confidence)
            assertEquals(10, bimTx.merchantInfo?.catalogVersion)

            val unknownTx = plan.transactions.first { it.description == "Bilinmeyen Bakkal" }
            assertNull(unknownTx.merchantInfo)
        }

    @Test
    fun plan_appliesPersonalDecisionsAndSuppressesKatalogOnBlockOrInvalid() =
        runTest {
            var sequence = 0
            val fakeRepo =
                FakeUserMerchantVerificationRepository(
                    decisions =
                        mapOf(
                            "Netflix Üyeliği" to UserMerchantVerificationLookupResult.Decision(UserMerchantDecision.Assign("netflix")),
                            "BİM" to UserMerchantVerificationLookupResult.Decision(UserMerchantDecision.Block),
                            "Migros" to UserMerchantVerificationLookupResult.InvalidStoredVerification("corrupt-id"),
                        ),
                )
            val planner =
                BackupImportPlanner(
                    idGenerator = EntityIdGenerator { EntityId("new-${++sequence}") },
                    userMerchantVerificationRepository = fakeRepo,
                )
            val backup =
                FeniqoBackupV1(
                    createdAt = "2026-09-10T12:00:00Z",
                    categories = listOf(BackupCategoryV1("cat-1", "Abonelik", "EXPENSE", "#112233", "sub")),
                    transactions =
                        listOf(
                            BackupTransactionV1(
                                id = "tx-1",
                                amountMinor = 100,
                                currency = "TRY",
                                type = "EXPENSE",
                                categoryId = "cat-1",
                                description = "Netflix Üyeliği",
                                paymentMethod = "CASH",
                                transactionDate = "2026-09-10",
                                createdAt = "2026-09-10T10:00:00Z",
                            ),
                            BackupTransactionV1(
                                id = "tx-2",
                                amountMinor = 200,
                                currency = "TRY",
                                type = "EXPENSE",
                                categoryId = "cat-1",
                                description = "BİM",
                                paymentMethod = "CASH",
                                transactionDate = "2026-09-10",
                                createdAt = "2026-09-10T10:00:00Z",
                            ),
                            BackupTransactionV1(
                                id = "tx-3",
                                amountMinor = 300,
                                currency = "TRY",
                                type = "EXPENSE",
                                categoryId = "cat-1",
                                description = "Migros",
                                paymentMethod = "CASH",
                                transactionDate = "2026-09-10",
                                createdAt = "2026-09-10T10:00:00Z",
                            ),
                        ),
                )

            val plan = planner.plan(backup, EntityId("user-1"))

            val tx1 = plan.transactions.first { it.description == "Netflix Üyeliği" }
            assertNotNull(tx1.merchantInfo)
            assertEquals("netflix", tx1.merchantInfo?.merchantId?.value)
            assertEquals(MerchantMatchSource.USER_VERIFIED, tx1.merchantInfo?.matchSource)
            assertEquals(100, tx1.merchantInfo?.confidence)

            val tx2 = plan.transactions.first { it.description == "BİM" }
            assertNull(tx2.merchantInfo, "BLOCK kararı bilinen katalog alias'ında merchantInfo üretmemeli")

            val tx3 = plan.transactions.first { it.description == "Migros" }
            assertNull(tx3.merchantInfo, "InvalidStoredVerification katalog fallback'ini engellemeli")
        }

    @Test
    fun plan_whenPersonalAssignHasUnknownMerchantId_suppressesCatalogFallbackAndSetsMerchantInfoNull() =
        runTest {
            var sequence = 0
            val fakeRepo =
                FakeUserMerchantVerificationRepository(
                    decisions =
                        mapOf(
                            "BİM" to
                                UserMerchantVerificationLookupResult.Decision(
                                    UserMerchantDecision.Assign("unknown_merchant_id_999"),
                                ),
                        ),
                )
            val planner =
                BackupImportPlanner(
                    idGenerator = EntityIdGenerator { EntityId("new-${++sequence}") },
                    userMerchantVerificationRepository = fakeRepo,
                )
            val backup =
                FeniqoBackupV1(
                    createdAt = "2026-09-10T12:00:00Z",
                    categories = listOf(BackupCategoryV1("cat-1", "Market", "EXPENSE", "#112233", "cart")),
                    transactions =
                        listOf(
                            BackupTransactionV1(
                                id = "tx-bim-unknown-assign",
                                amountMinor = 5000L,
                                currency = "TRY",
                                type = "EXPENSE",
                                categoryId = "cat-1",
                                description = "BİM",
                                paymentMethod = "CASH",
                                transactionDate = "2026-09-10",
                                createdAt = "2026-09-10T10:00:00Z",
                            ),
                        ),
                )

            val plan = planner.plan(backup, EntityId("user-1"))
            val bimTx = plan.transactions.single()
            assertNull(
                bimTx.merchantInfo,
                "Unknown ASSIGN merchant ID katalog fallback'i yapmamalı ve merchantInfo=null olmalıdır",
            )
        }
}

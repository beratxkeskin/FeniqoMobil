package com.feniqo.mobile.data.backup

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PersonalBackupExporterTest {

    @Test
    fun calculateScope_returnsCorrectCounts() = runTest {
        val catRepo = FakeCategoryRepo(listOf(testCategory("c1"), testCategory("c2")))
        val trxRepo = FakeTransactionRepo(listOf(testTransaction("t1", "c1"), testTransaction("t2", "c2")))

        val exporter = PersonalBackupExporter(catRepo, trxRepo)
        val scope = exporter.calculateScope()

        assertEquals(2, scope.categoryCount)
        assertEquals(2, scope.transactionCount)
    }

    @Test
    fun export_producesValidAndDecodableBackup() = runTest {
        val catRepo = FakeCategoryRepo(listOf(testCategory("c1", "Market")))
        val trxRepo = FakeTransactionRepo(listOf(testTransaction("t1", "c1", 125050L)))

        val exporter = PersonalBackupExporter(catRepo, trxRepo) { Instant.parse("2026-09-16T12:00:00Z").toEpochMilliseconds() }
        val rawJson = exporter.export()

        val decoded = assertIs<BackupDecodeResult.Valid>(FeniqoBackupCodec.decode(rawJson)).backup
        assertEquals(1, decoded.categories.size)
        assertEquals("Market", decoded.categories[0].name)
        assertEquals(1, decoded.transactions.size)
        assertEquals(125050L, decoded.transactions[0].amountMinor)
        assertEquals("TRY", decoded.transactions[0].currency)
        assertTrue(rawJson.contains("\"format_version\":1"))
    }

    private fun testCategory(id: String, name: String = "Test") = Category(
        id = EntityId(id),
        ownerId = EntityId("u1"),
        workspaceId = null,
        name = name,
        type = TransactionType.EXPENSE,
        color = CategoryColor("#2D5A43"),
        icon = CategoryIcon("cart"),
        isDefault = false,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private fun testTransaction(id: String, catId: String, amountMinor: Long = 1000L) = Transaction(
        id = EntityId(id),
        ownerId = EntityId("u1"),
        workspaceId = null,
        amount = Money(amountMinor, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = EntityId(catId),
        description = "Test transaction",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = LocalDate.parse("2026-09-16"),
        receiptPath = null,
        installment = null,
        createdAt = Instant.parse("2026-09-16T10:00:00Z"),
        paidByUserId = EntityId("u1"),
        participantUserIds = listOf(EntityId("u1")),
        note = null,
    )

    private class FakeCategoryRepo(private val categories: List<Category>) : CategoryRepository {
        override fun observeCategories(type: TransactionType?, workspaceId: EntityId?): Flow<List<Category>> =
            flowOf(categories)
        override fun observeCategory(id: EntityId): Flow<Category?> = flowOf(categories.find { it.id == id })
        override fun observeCategoriesForHistoryLookup(workspaceId: EntityId?): Flow<List<Category>> =
            flowOf(categories)
        override suspend fun create(category: Category): RepositoryResult<EntityId> =
            RepositoryResult.Success(category.id)
        override suspend fun update(category: Category): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private class FakeTransactionRepo(private val transactions: List<Transaction>) : TransactionRepository {
        override fun observeTransactions(filter: com.feniqo.mobile.domain.repository.TransactionFilter): Flow<List<Transaction>> =
            flowOf(transactions)
        override fun observeTransaction(id: EntityId): Flow<Transaction?> =
            flowOf(transactions.find { it.id == id })
        override fun observeInstallmentGroup(groupId: EntityId): Flow<List<Transaction>> =
            flowOf(emptyList())
        override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> =
            RepositoryResult.Success(transaction.id)
        override suspend fun createInstallmentGroup(transactions: List<Transaction>): RepositoryResult<EntityId> =
            RepositoryResult.Success(EntityId("grp-1"))
        override suspend fun update(transaction: Transaction): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun softDeleteInstallments(ids: Set<EntityId>): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }
}

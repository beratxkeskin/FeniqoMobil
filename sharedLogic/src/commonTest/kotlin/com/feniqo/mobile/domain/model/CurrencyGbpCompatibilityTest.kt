package com.feniqo.mobile.domain.model

import com.feniqo.mobile.data.backup.DefaultPersonalBackupExporter
import com.feniqo.mobile.data.backup.PersonalBackupImporter
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.usecase.TransactionCsvExporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurrencyGbpCompatibilityTest {

    @Test
    fun currencyEnum_gbpIsAppendedAtTheEnd() {
        val entries = Currency.entries
        assertEquals(Currency.TRY, entries[0])
        assertEquals(Currency.USD, entries[1])
        assertEquals(Currency.EUR, entries[2])
        assertEquals(Currency.GBP, entries[3])
        assertEquals(4, entries.size)
    }

    @Test
    fun money_supportsGbpCalculations() {
        val m1 = Money(1500L, Currency.GBP)
        val m2 = Money(3500L, Currency.GBP)
        val sum = m1 + m2
        assertEquals(5000L, sum.amountMinor)
        assertEquals(Currency.GBP, sum.currency)

        val delta = MoneyDelta.between(sum, m1)
        assertEquals(3500L, delta.amountMinor)
        assertEquals(Currency.GBP, delta.currency)
    }

    @Test
    fun transactionEntity_roundTripWithGbp() {
        val tx = Transaction(
            id = EntityId("tx-gbp-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(25000L, Currency.GBP),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = "London Tube",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionDate = LocalDate(2026, 9, 15),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        val entity = tx.toEntity(com.feniqo.mobile.data.mapper.newSyncMetadata(1))
        assertEquals("GBP", entity.currencyCode)

        val restored = entity.toDomain()
        assertEquals(Currency.GBP, restored.amount.currency)
        assertEquals(25000L, restored.amount.amountMinor)
        assertEquals("London Tube", restored.description)
    }

    @Test
    fun csvExport_supportsGbpTransactions() {
        val tx = Transaction(
            id = EntityId("tx-gbp-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            amount = Money(4500L, Currency.GBP),
            type = TransactionType.EXPENSE,
            categoryId = EntityId("cat-1"),
            description = "Cafe",
            paymentMethod = PaymentMethod.CASH,
            transactionDate = LocalDate(2026, 9, 16),
            receiptPath = null,
            installment = null,
            createdAt = Instant.fromEpochMilliseconds(2000L),
        )

        val exporter = TransactionCsvExporter()
        val csv = exporter.export(listOf(tx))
        assertTrue(csv.contains("\"GBP\""))
        assertTrue(csv.contains("\"4500\""))
    }
}

package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RepositoryContractTest {

    @Test
    fun successful_write_is_observed_from_the_repository_flow() = runTest {
        val repository: TransactionRepository = FakeTransactionRepository()
        val transaction = sampleTransaction()

        val result = repository.create(transaction)

        assertIs<RepositoryResult.Success<EntityId>>(result)
        assertEquals(listOf(transaction), repository.observeTransactions().first())
    }

    @Test
    fun rejected_write_returns_a_defined_application_error() = runTest {
        val repository: TransactionRepository = FakeTransactionRepository()
        val transaction = sampleTransaction()
        repository.create(transaction)

        val result = repository.create(transaction)

        val failure = assertIs<RepositoryResult.Failure>(result)
        assertEquals("transaction_already_exists", failure.error.code)
    }

    private fun sampleTransaction() = Transaction(
        id = EntityId("transaction-1"),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        amount = Money(12_550, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = EntityId("category-1"),
        description = "Market",
        paymentMethod = PaymentMethod.DEBIT_CARD,
        transactionDate = LocalDate(2026, 8, 5),
        receiptPath = null,
        installment = null,
        createdAt = Instant.parse("2026-08-05T00:00:00Z"),
    )
}

private class FakeTransactionRepository : TransactionRepository {
    private val transactions = MutableStateFlow<List<Transaction>>(emptyList())

    override fun observeTransactions(filter: TransactionFilter): Flow<List<Transaction>> = transactions

    override fun observeTransaction(id: EntityId): Flow<Transaction?> =
        transactions.map { items -> items.firstOrNull { it.id == id } }

    override suspend fun create(transaction: Transaction): RepositoryResult<EntityId> {
        if (transactions.value.any { it.id == transaction.id }) {
            return RepositoryResult.Failure(AppError.Conflict("transaction_already_exists"))
        }
        transactions.value = transactions.value + transaction
        return RepositoryResult.Success(transaction.id)
    }

    override suspend fun update(transaction: Transaction): RepositoryResult<Unit> {
        transactions.value = transactions.value.map { current ->
            if (current.id == transaction.id) transaction else current
        }
        return RepositoryResult.Success(Unit)
    }

    override suspend fun softDelete(id: EntityId): RepositoryResult<Unit> {
        transactions.value = transactions.value.filterNot { it.id == id }
        return RepositoryResult.Success(Unit)
    }
}

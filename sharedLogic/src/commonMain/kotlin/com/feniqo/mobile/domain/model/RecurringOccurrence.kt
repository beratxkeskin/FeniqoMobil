package com.feniqo.mobile.domain.model

/**
 * Bir tekrarlayan işlemin belirli bir vadedeki tekil, kalıcı idempotency kimliği.
 * İleride Room ve remote katmanları bu iki alanı birlikte benzersiz kabul eder.
 */
data class RecurringOccurrenceKey(
    val recurringTransactionId: EntityId,
    val dueDate: LocalDate,
)

/**
 * Vadesi gelmiş ve üretilmeye aday tekrarlayan işlem öğesi.
 */
data class RecurringDueOccurrence(
    val key: RecurringOccurrenceKey,
    val recurringTransaction: RecurringTransaction,
    val dueDate: LocalDate,
) {
    init {
        require(dueDate == key.dueDate) {
            "dueDate ($dueDate) ile key.dueDate (${key.dueDate}) eşleşmelidir."
        }
        require(recurringTransaction.id == key.recurringTransactionId) {
            "recurringTransaction.id (${recurringTransaction.id}) ile key.recurringTransactionId (${key.recurringTransactionId}) eşleşmelidir."
        }
    }
}

/**
 * Tekrarlayan işlemlerden vadesi gelen işlemlerin toplu üretim sonucu.
 */
data class GenerateRecurringTransactionsResult(
    val createdCount: Int,
    val alreadyGeneratedCount: Int,
    val staleRecurringIds: List<EntityId>,
    val skippedRecurringIds: List<EntityId>,
)


package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringDueOccurrence
import com.feniqo.mobile.domain.model.RecurringOccurrenceKey
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

class PlanDueRecurringOccurrencesUseCaseTest {

    private val useCase = PlanDueRecurringOccurrencesUseCase()

    private fun createRecurring(
        id: String,
        frequency: RecurrenceFrequency = RecurrenceFrequency.DAILY,
        interval: Int = 1,
        startDate: LocalDate = LocalDate(2026, 8, 1),
        endDate: LocalDate? = null,
        lastGeneratedDate: LocalDate? = null,
        isActive: Boolean = true,
    ): RecurringTransaction = RecurringTransaction(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        amount = Money(5000L, Currency.TRY),
        type = TransactionType.EXPENSE,
        categoryId = EntityId("cat-1"),
        description = "Deneme tekrar",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        rule = RecurrenceRule(
            frequency = frequency,
            interval = interval,
            startDate = startDate,
            endDate = endDate,
        ),
        lastGeneratedDate = lastGeneratedDate,
        isActive = isActive,
        createdAt = Instant.fromEpochMilliseconds(1700000000000L),
    )

    @Test
    fun activeRule_generatesExpectedCandidates() {
        val recurring = createRecurring(
            id = "rec-1",
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
        )

        val result = useCase(
            recurringTransactions = listOf(recurring),
            throughDate = LocalDate(2026, 8, 3),
        )

        assertEquals(3, result.size)
        assertEquals(LocalDate(2026, 8, 1), result[0].dueDate)
        assertEquals(RecurringOccurrenceKey(EntityId("rec-1"), LocalDate(2026, 8, 1)), result[0].key)
        assertEquals(LocalDate(2026, 8, 2), result[1].dueDate)
        assertEquals(LocalDate(2026, 8, 3), result[2].dueDate)
    }

    @Test
    fun inactiveRule_generatesZeroCandidates() {
        val inactive = createRecurring(
            id = "rec-inactive",
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            isActive = false,
        )

        val result = useCase(
            recurringTransactions = listOf(inactive),
            throughDate = LocalDate(2026, 8, 10),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun differentRulesWithSameDueDate_generateDistinctKeys() {
        val rec1 = createRecurring(id = "rec-1", startDate = LocalDate(2026, 8, 1))
        val rec2 = createRecurring(id = "rec-2", startDate = LocalDate(2026, 8, 1))

        val result = useCase(
            recurringTransactions = listOf(rec1, rec2),
            throughDate = LocalDate(2026, 8, 1),
        )

        assertEquals(2, result.size)
        assertEquals(LocalDate(2026, 8, 1), result[0].dueDate)
        assertEquals(LocalDate(2026, 8, 1), result[1].dueDate)
        assertNotEquals(result[0].key, result[1].key)
        assertEquals(EntityId("rec-1"), result[0].key.recurringTransactionId)
        assertEquals(EntityId("rec-2"), result[1].key.recurringTransactionId)
    }

    @Test
    fun candidatesAreDeterministicallySortedByDueDateThenRecurringId() {
        val recB = createRecurring(id = "rec-b", interval = 2, startDate = LocalDate(2026, 8, 1))
        val recA = createRecurring(id = "rec-a", interval = 1, startDate = LocalDate(2026, 8, 1))

        val result = useCase(
            recurringTransactions = listOf(recB, recA),
            throughDate = LocalDate(2026, 8, 3),
        )

        // recB produces: 2026-08-01, 2026-08-03
        // recA produces: 2026-08-01, 2026-08-02, 2026-08-03
        // Sorted:
        // 1) 2026-08-01 rec-a
        // 2) 2026-08-01 rec-b
        // 3) 2026-08-02 rec-a
        // 4) 2026-08-03 rec-a
        // 5) 2026-08-03 rec-b
        assertEquals(5, result.size)
        assertEquals(LocalDate(2026, 8, 1) to "rec-a", result[0].dueDate to result[0].recurringTransaction.id.value)
        assertEquals(LocalDate(2026, 8, 1) to "rec-b", result[1].dueDate to result[1].recurringTransaction.id.value)
        assertEquals(LocalDate(2026, 8, 2) to "rec-a", result[2].dueDate to result[2].recurringTransaction.id.value)
        assertEquals(LocalDate(2026, 8, 3) to "rec-a", result[3].dueDate to result[3].recurringTransaction.id.value)
        assertEquals(LocalDate(2026, 8, 3) to "rec-b", result[4].dueDate to result[4].recurringTransaction.id.value)
    }

    @Test
    fun boundaries_endDate_lastGeneratedDate_throughDate_appliedCorrectly() {
        val recurring = createRecurring(
            id = "rec-1",
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
            endDate = LocalDate(2026, 8, 4),
            lastGeneratedDate = LocalDate(2026, 8, 2),
        )

        val result = useCase(
            recurringTransactions = listOf(recurring),
            throughDate = LocalDate(2026, 8, 10),
        )

        // lastGenerated is Aug 2 -> next are Aug 3, Aug 4. (Aug 5 > endDate, so stopped).
        assertEquals(listOf(LocalDate(2026, 8, 3), LocalDate(2026, 8, 4)), result.map { it.dueDate })
    }

    @Test
    fun maxOccurrencesPerRule_and_maxTotalOccurrences_limitsAppliedDeterministically() {
        val rec1 = createRecurring(id = "rec-1", startDate = LocalDate(2026, 8, 1))
        val rec2 = createRecurring(id = "rec-2", startDate = LocalDate(2026, 8, 1))

        // Per rule limit = 2 -> rec-1: Aug 1, Aug 2; rec-2: Aug 1, Aug 2
        // Total limit = 3 -> First 3 items in sorted order:
        // (Aug 1, rec-1), (Aug 1, rec-2), (Aug 2, rec-1)
        val result = useCase(
            recurringTransactions = listOf(rec1, rec2),
            throughDate = LocalDate(2026, 8, 10),
            maxOccurrencesPerRule = 2,
            maxTotalOccurrences = 3,
        )

        assertEquals(3, result.size)
        assertEquals(LocalDate(2026, 8, 1) to "rec-1", result[0].dueDate to result[0].recurringTransaction.id.value)
        assertEquals(LocalDate(2026, 8, 1) to "rec-2", result[1].dueDate to result[1].recurringTransaction.id.value)
        assertEquals(LocalDate(2026, 8, 2) to "rec-1", result[2].dueDate to result[2].recurringTransaction.id.value)
    }

    @Test
    fun invalidLimits_failClosedWithIllegalArgumentException() {
        val rec = createRecurring(id = "rec-1")

        assertFailsWith<IllegalArgumentException> {
            useCase(
                recurringTransactions = listOf(rec),
                throughDate = LocalDate(2026, 8, 5),
                maxOccurrencesPerRule = 0,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            useCase(
                recurringTransactions = listOf(rec),
                throughDate = LocalDate(2026, 8, 5),
                maxTotalOccurrences = -1,
            )
        }
    }

    @Test
    fun invalidScheduleStateFromCalculator_isNotSwallowed() {
        // lastGeneratedDate is off-interval (1 -> 4 -> 7, but lastGenerated is 2)
        val recWithBadState = createRecurring(
            id = "rec-bad",
            interval = 3,
            startDate = LocalDate(2026, 8, 1),
            lastGeneratedDate = LocalDate(2026, 8, 2),
        )

        assertFailsWith<IllegalArgumentException> {
            useCase(
                recurringTransactions = listOf(recWithBadState),
                throughDate = LocalDate(2026, 8, 10),
            )
        }
    }

    @Test
    fun recurringDueOccurrence_invariantsEnforced() {
        val rec = createRecurring(id = "rec-1")
        val validKey = RecurringOccurrenceKey(EntityId("rec-1"), LocalDate(2026, 8, 1))

        // Valid
        val occurrence = RecurringDueOccurrence(
            key = validKey,
            recurringTransaction = rec,
            dueDate = LocalDate(2026, 8, 1),
        )
        assertEquals(LocalDate(2026, 8, 1), occurrence.dueDate)

        // Mismatched dueDate
        assertFailsWith<IllegalArgumentException> {
            RecurringDueOccurrence(
                key = validKey,
                recurringTransaction = rec,
                dueDate = LocalDate(2026, 8, 2),
            )
        }

        // Mismatched id
        val otherRec = createRecurring(id = "rec-2")
        assertFailsWith<IllegalArgumentException> {
            RecurringDueOccurrence(
                key = validKey,
                recurringTransaction = otherRec,
                dueDate = LocalDate(2026, 8, 1),
            )
        }
    }

    @Test
    fun throughDateBeforeStartDate_returnsEmptyList_andGeneratesNoFutureCandidates() {
        val recurring = createRecurring(
            id = "rec-1",
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 10),
            endDate = LocalDate(2026, 8, 20),
        )

        val result = useCase(
            recurringTransactions = listOf(recurring),
            throughDate = LocalDate(2026, 8, 5),
        )

        assertTrue(result.isEmpty(), "Başlangıçtan önceki throughDate için hiçbir aday üretilmemelidir.")
    }

    @Test
    fun duplicateRecurringTransactionInput_failsClosedWithIllegalArgumentException() {
        val rec = createRecurring(
            id = "rec-duplicate",
            frequency = RecurrenceFrequency.DAILY,
            interval = 1,
            startDate = LocalDate(2026, 8, 1),
        )

        // Same recurring transaction provided twice in input list
        val exception = assertFailsWith<IllegalArgumentException> {
            useCase(
                recurringTransactions = listOf(rec, rec),
                throughDate = LocalDate(2026, 8, 5),
            )
        }

        assertTrue(
            exception.message?.contains("Mükerrer aday tekrar anahtarı") == true,
            "seenKeys koruması mükerrer anahtar tespitinde hata fırlatmalıdır: ${exception.message}",
        )
    }
}

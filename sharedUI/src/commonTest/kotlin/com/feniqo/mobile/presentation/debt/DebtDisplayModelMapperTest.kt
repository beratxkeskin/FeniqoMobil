package com.feniqo.mobile.presentation.debt

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebtDisplayModelMapperTest {

    private fun sampleDebt(
        id: String,
        title: String = "Test Borç",
        amountMinor: Long = 50_000L,
        currency: Currency = Currency.TRY,
        type: DebtType = DebtType.DEBT,
        dueDate: LocalDate = LocalDate(2026, 10, 15),
        status: DebtStatus = DebtStatus.OPEN,
        description: String? = "Açıklama",
    ): Debt = Debt(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        title = title,
        amount = Money(amountMinor, currency),
        type = type,
        dueDate = dueDate,
        status = status,
        description = description,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    @Test
    fun mapItem_mapsDebtAndReceivableLabelsCorrectly() {
        val debt = sampleDebt(
            id = "d-1",
            title = "Kredi Kartı",
            amountMinor = 50_000L,
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 10, 15),
            status = DebtStatus.OPEN,
            description = "Banka borcu",
        )
        val receivable = sampleDebt(
            id = "d-2",
            title = "Ahmet'e Verilen Borç",
            amountMinor = 30_000L,
            type = DebtType.RECEIVABLE,
            dueDate = LocalDate(2026, 11, 20),
            status = DebtStatus.OPEN,
            description = null,
        )

        val paymentsReceivable = listOf(
            com.feniqo.mobile.domain.model.DebtPayment(
                id = EntityId("p-1"),
                debtId = EntityId("d-2"),
                amount = Money(30_000L, Currency.TRY),
                paidOn = LocalDate(2026, 11, 20),
                createdAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val debtModel = DebtDisplayModelMapper.mapItem(debt)
        val receivableModel = DebtDisplayModelMapper.mapItem(receivable, paymentsReceivable)

        assertEquals(EntityId("d-1"), debtModel.id)
        assertEquals("Kredi Kartı", debtModel.title)
        assertEquals(DebtType.DEBT, debtModel.type)
        assertEquals(DebtStatus.OPEN, debtModel.status)
        assertEquals("Borç", debtModel.typeLabel)
        assertTrue(debtModel.isOpen)
        assertFalse(debtModel.isSettled)
        assertEquals(50_000L, debtModel.principalAmount.amountMinor)
        assertEquals("500,00 ₺", debtModel.formattedPrincipalAmount)
        assertEquals(0L, debtModel.totalPaid.amountMinor)
        assertEquals("0,00 ₺", debtModel.formattedTotalPaid)
        assertEquals(50_000L, debtModel.remainingAmount.amountMinor)
        assertEquals("500,00 ₺", debtModel.formattedRemainingAmount)
        assertEquals("15 Ekim 2026", debtModel.formattedDueDate)
        assertEquals("Banka borcu", debtModel.description)

        assertEquals(EntityId("d-2"), receivableModel.id)
        assertEquals("Ahmet'e Verilen Borç", receivableModel.title)
        assertEquals(DebtType.RECEIVABLE, receivableModel.type)
        assertEquals(DebtStatus.SETTLED, receivableModel.status)
        assertEquals("Alacak", receivableModel.typeLabel)
        assertFalse(receivableModel.isOpen)
        assertTrue(receivableModel.isSettled)
        assertEquals(30_000L, receivableModel.principalAmount.amountMinor)
        assertEquals("300,00 ₺", receivableModel.formattedPrincipalAmount)
        assertEquals(30_000L, receivableModel.totalPaid.amountMinor)
        assertEquals("300,00 ₺", receivableModel.formattedTotalPaid)
        assertEquals(0L, receivableModel.remainingAmount.amountMinor)
        assertEquals("0,00 ₺", receivableModel.formattedRemainingAmount)
        assertEquals("20 Kasım 2026", receivableModel.formattedDueDate)
        assertNull(receivableModel.description)
    }

    @Test
    fun mapItem_withPartialPayments_calculatesRemainingAmountCorrectly() {
        val debt = sampleDebt(
            id = "d-1",
            title = "Kredi",
            amountMinor = 100_000L,
            type = DebtType.DEBT,
        )
        val payments = listOf(
            com.feniqo.mobile.domain.model.DebtPayment(
                id = EntityId("p-1"),
                debtId = EntityId("d-1"),
                amount = Money(40_000L, Currency.TRY),
                paidOn = LocalDate(2026, 9, 1),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
        )

        val model = DebtDisplayModelMapper.mapItem(debt, payments)
        assertEquals(100_000L, model.principalAmount.amountMinor)
        assertEquals(40_000L, model.totalPaid.amountMinor)
        assertEquals("400,00 ₺", model.formattedTotalPaid)
        assertEquals(60_000L, model.remainingAmount.amountMinor)
        assertEquals("600,00 ₺", model.formattedRemainingAmount)
        assertTrue(model.isOpen)
        assertFalse(model.isSettled)
    }

    @Test
    fun map_deterministicOrdering_openFirstThenSettled_thenByDueDateAndId() {
        val d1 = sampleDebt(id = "d-1", dueDate = LocalDate(2026, 12, 15), amountMinor = 10_000L)
        val d2 = sampleDebt(id = "d-2", dueDate = LocalDate(2026, 9, 1), amountMinor = 10_000L)
        val d3 = sampleDebt(id = "d-3", dueDate = LocalDate(2026, 10, 1), amountMinor = 10_000L)
        val d4 = sampleDebt(id = "d-4", dueDate = LocalDate(2026, 9, 1), amountMinor = 10_000L)
        val d5 = sampleDebt(id = "d-5", dueDate = LocalDate(2026, 10, 1), amountMinor = 10_000L)

        // d2 and d4 are fully paid (SETTLED)
        val paymentsByDebtId = mapOf(
            EntityId("d-2") to listOf(
                com.feniqo.mobile.domain.model.DebtPayment(
                    id = EntityId("p-d2"),
                    debtId = EntityId("d-2"),
                    amount = Money(10_000L, Currency.TRY),
                    paidOn = LocalDate(2026, 9, 1),
                    createdAt = Instant.fromEpochMilliseconds(1000L),
                ),
            ),
            EntityId("d-4") to listOf(
                com.feniqo.mobile.domain.model.DebtPayment(
                    id = EntityId("p-d4"),
                    debtId = EntityId("d-4"),
                    amount = Money(10_000L, Currency.TRY),
                    paidOn = LocalDate(2026, 9, 1),
                    createdAt = Instant.fromEpochMilliseconds(1000L),
                ),
            ),
        )

        val result = DebtDisplayModelMapper.map(listOf(d1, d2, d3, d4, d5), paymentsByDebtId)

        // Expected order:
        // Group 1 (OPEN):
        // 1. d3 (2026-10-01, d-3)
        // 2. d5 (2026-10-01, d-5)
        // 3. d1 (2026-12-15, d-1)
        // Group 2 (SETTLED):
        // 4. d2 (2026-09-01, d-2)
        // 5. d4 (2026-09-01, d-4)
        assertEquals(listOf("d-3", "d-5", "d-1", "d-2", "d-4"), result.map { it.id.value })
    }

    @Test
    fun debtsUiState_isEmptyContract() {
        assertTrue(DebtsUiState(isLoading = false, debts = emptyList(), observationError = null).isEmpty)
        assertFalse(DebtsUiState(isLoading = true, debts = emptyList(), observationError = null).isEmpty)
        assertFalse(DebtsUiState(isLoading = false, debts = emptyList(), observationError = com.feniqo.mobile.presentation.common.FinanceUiMessage.GENERIC_ERROR).isEmpty)
        assertFalse(
            DebtsUiState(
                isLoading = false,
                debts = listOf(DebtDisplayModelMapper.mapItem(sampleDebt("d-1"))),
                observationError = null,
            ).isEmpty,
        )
    }
}


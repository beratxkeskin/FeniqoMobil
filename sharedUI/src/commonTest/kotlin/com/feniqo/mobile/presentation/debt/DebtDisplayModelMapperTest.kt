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
import kotlin.test.assertNotNull
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
        val today = LocalDate(2026, 9, 13)
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

        val debtModel = DebtDisplayModelMapper.mapItem(debt, today = today)
        val receivableModel = DebtDisplayModelMapper.mapItem(receivable, paymentsReceivable, today = today)

        assertEquals(EntityId("d-1"), debtModel.id)
        assertEquals("Kredi Kartı", debtModel.title)
        assertEquals("K", debtModel.avatarInitial)
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
        assertEquals("15 Eki 2026", debtModel.formattedShortDueDate)
        assertEquals("Banka borcu", debtModel.description)
        assertTrue(debtModel.dueStatus is DebtDueStatus.OnTime)
        assertEquals("Zamanında", debtModel.formattedDueStatus)

        assertEquals(EntityId("d-2"), receivableModel.id)
        assertEquals("Ahmet'e Verilen Borç", receivableModel.title)
        assertEquals("A", receivableModel.avatarInitial)
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
        assertEquals("20 Kas 2026", receivableModel.formattedShortDueDate)
        assertEquals(DebtDueStatus.Settled, receivableModel.dueStatus)
        assertEquals("Kapandı", receivableModel.formattedDueStatus)
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

        assertEquals(listOf("d-3", "d-5", "d-1", "d-2", "d-4"), result.map { it.id.value })
    }

    @Test
    fun dueStatusCalculation_matchesDateRules() {
        val today = LocalDate(2026, 9, 13)

        // 1. Overdue: due yesterday
        val (overdueStatus, overdueDiff) = DebtDisplayModelMapper.calculateDueStatus(
            dueDate = LocalDate(2026, 9, 12),
            today = today,
            isSettled = false,
        )
        assertEquals(DebtDueStatus.Overdue(1L), overdueStatus)
        assertEquals(-1L, overdueDiff)
        assertEquals("Gecikti", DebtDisplayModelMapper.formatDueStatusLabel(overdueStatus))

        // 2. DueToday: due today
        val (todayStatus, todayDiff) = DebtDisplayModelMapper.calculateDueStatus(
            dueDate = LocalDate(2026, 9, 13),
            today = today,
            isSettled = false,
        )
        assertEquals(DebtDueStatus.DueToday, todayStatus)
        assertEquals(0L, todayDiff)
        assertEquals("Bugün", DebtDisplayModelMapper.formatDueStatusLabel(todayStatus))

        // 3. DueSoon: due in 5 days (within 7-day window)
        val (dueSoonStatus, dueSoonDiff) = DebtDisplayModelMapper.calculateDueStatus(
            dueDate = LocalDate(2026, 9, 18),
            today = today,
            isSettled = false,
        )
        assertEquals(DebtDueStatus.DueSoon(5L), dueSoonStatus)
        assertEquals(5L, dueSoonDiff)
        assertEquals("Yaklaşıyor", DebtDisplayModelMapper.formatDueStatusLabel(dueSoonStatus))

        // 4. OnTime: due in 15 days (> 7 days)
        val (onTimeStatus, onTimeDiff) = DebtDisplayModelMapper.calculateDueStatus(
            dueDate = LocalDate(2026, 9, 28),
            today = today,
            isSettled = false,
        )
        assertEquals(DebtDueStatus.OnTime(15L), onTimeStatus)
        assertEquals(15L, onTimeDiff)
        assertEquals("Zamanında", DebtDisplayModelMapper.formatDueStatusLabel(onTimeStatus))

        // 5. Settled: always Settled regardless of date
        val (settledStatus, settledDiff) = DebtDisplayModelMapper.calculateDueStatus(
            dueDate = LocalDate(2026, 9, 1),
            today = today,
            isSettled = true,
        )
        assertEquals(DebtDueStatus.Settled, settledStatus)
        assertEquals(0L, settledDiff)
        assertEquals("Kapandı", DebtDisplayModelMapper.formatDueStatusLabel(settledStatus))
    }

    @Test
    fun deriveAvatarInitial_extractsUpperInitialFromTitle() {
        assertEquals("A", DebtDisplayModelMapper.deriveAvatarInitial("Ahmet Yılmaz"))
        assertEquals("E", DebtDisplayModelMapper.deriveAvatarInitial("emre kaya"))
        assertEquals("K", DebtDisplayModelMapper.deriveAvatarInitial("  Kredi Kartı "))
        assertEquals("1", DebtDisplayModelMapper.deriveAvatarInitial("123 Banka"))
        assertEquals("?", DebtDisplayModelMapper.deriveAvatarInitial("   "))
    }

    @Test
    fun debtsSummaryCalculator_multiCurrencySafety_separatesForeignCurrencies() {
        val today = LocalDate(2026, 9, 13)
        val d1 = sampleDebt("d-1", "TRY Borç 1", amountMinor = 125_000L, type = DebtType.DEBT, currency = Currency.TRY) // 1.250 TL
        val d2 = sampleDebt("d-2", "TRY Alacak 1", amountMinor = 82_000L, type = DebtType.RECEIVABLE, currency = Currency.TRY) // 820 TL
        val d3 = sampleDebt("d-3", "USD Borç", amountMinor = 50_000L, type = DebtType.DEBT, currency = Currency.USD) // 500 USD (farklı para birimi)
        val d4 = sampleDebt("d-4", "EUR Alacak", amountMinor = 30_000L, type = DebtType.RECEIVABLE, currency = Currency.EUR) // 300 EUR (farklı para birimi)

        val mapped = DebtDisplayModelMapper.map(listOf(d1, d2, d3, d4), today = today)
        val summary = DebtsSummaryCalculator.calculate(mapped, baseCurrency = Currency.TRY)

        // Only TRY items should be summed
        assertEquals(125_000L, summary.totalDebt.amountMinor)
        assertEquals("1.250,00 ₺", summary.formattedTotalDebt)
        assertEquals(1, summary.activeDebtCount)

        assertEquals(82_000L, summary.totalReceivable.amountMinor)
        assertEquals("820,00 ₺", summary.formattedTotalReceivable)
        assertEquals(1, summary.activeReceivableCount)

        // Net balance = 820 - 1250 = -430 TL
        assertEquals(-43_000L, summary.netBalanceMinor)
        assertEquals("-430,00 ₺", summary.formattedNetBalance)
        assertTrue(summary.isNetNegative)
        assertFalse(summary.isNetPositive)
        assertEquals("Borcunuz alacağınızdan fazla.", summary.netStatusText)

        // Excluded currencies
        assertEquals(2, summary.excludedCurrenciesCount)
        assertEquals(listOf(Currency.USD, Currency.EUR), summary.excludedCurrencies)
    }

    @Test
    fun debtInsightBuilder_buildsExplainableDataDrivenInsights() {
        val today = LocalDate(2026, 9, 13)

        // 1. Overdue debt warning
        val overdueDebt = sampleDebt("d-1", "Gecikmiş Borç", amountMinor = 20_000L, dueDate = LocalDate(2026, 9, 5))
        val mappedOverdue = DebtDisplayModelMapper.map(listOf(overdueDebt), today = today)
        val uiStateOverdue = DebtDisplayModelMapper.buildUiState(mappedOverdue)
        assertNotNull(uiStateOverdue.insight)
        assertEquals("Gecikmiş Borç Uyarısı", uiStateOverdue.insight?.title)
        assertTrue(uiStateOverdue.insight?.message?.contains("1 adet vadesi geçmiş") == true)

        // 2. Pending receivable reminder
        val recDebt = sampleDebt("r-1", "Alacak", amountMinor = 820_000L, type = DebtType.RECEIVABLE, dueDate = LocalDate(2026, 9, 20))
        val mappedRec = DebtDisplayModelMapper.map(listOf(recDebt), today = today)
        val uiStateRec = DebtDisplayModelMapper.buildUiState(mappedRec)
        assertNotNull(uiStateRec.insight)
        assertEquals("Feniqo İçgörü", uiStateRec.insight?.title)
        assertTrue(uiStateRec.insight?.message?.contains("8.200,00 ₺") == true)

        // 3. Multiple active debts: snowball plan recommendation
        val dA = sampleDebt("dA", "Borç A", amountMinor = 20_000L, dueDate = LocalDate(2026, 10, 1))
        val dB = sampleDebt("dB", "Borç B", amountMinor = 50_000L, dueDate = LocalDate(2026, 10, 5))
        val mappedMultiple = DebtDisplayModelMapper.map(listOf(dA, dB), today = today)
        val uiStateMultiple = DebtDisplayModelMapper.buildUiState(mappedMultiple)
        assertNotNull(uiStateMultiple.insight)
        assertEquals("Kartopu Planı Önerisi", uiStateMultiple.insight?.title)

        // 4. Empty debts: no insight
        val emptyUiState = DebtDisplayModelMapper.buildUiState(emptyList())
        assertNull(emptyUiState.insight)
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

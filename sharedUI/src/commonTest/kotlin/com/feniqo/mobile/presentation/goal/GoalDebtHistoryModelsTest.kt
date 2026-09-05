package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.debt.toSortedHistoryUiModels
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalDebtHistoryModelsTest {

    @Test
    fun goalContributions_toSortedHistoryUiModels_sortsByOccurredOnDescThenIdDesc() {
        val contributions = listOf(
            GoalContribution(
                id = EntityId("gc-1"),
                goalId = EntityId("g-1"),
                amount = Money(500_00L, Currency.TRY),
                direction = GoalContributionDirection.ADD,
                occurredOn = LocalDate(2026, 9, 1),
                note = "Not 1",
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
            GoalContribution(
                id = EntityId("gc-3"),
                goalId = EntityId("g-1"),
                amount = Money(200_00L, Currency.TRY),
                direction = GoalContributionDirection.REMOVE,
                occurredOn = LocalDate(2026, 9, 2),
                note = null,
                createdAt = Instant.fromEpochMilliseconds(3000L),
            ),
            GoalContribution(
                id = EntityId("gc-2"),
                goalId = EntityId("g-1"),
                amount = Money(300_00L, Currency.TRY),
                direction = GoalContributionDirection.ADD,
                occurredOn = LocalDate(2026, 9, 2),
                note = "Not 2",
                createdAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val result = contributions.toSortedHistoryUiModels()

        assertEquals(3, result.size)
        // 1. 2026-09-02, gc-3 (id desc)
        assertEquals(EntityId("gc-3"), result[0].id)
        assertEquals(GoalContributionDirection.REMOVE, result[0].direction)
        assertTrue(result[0].formattedAmount.startsWith("-"))
        assertNull(result[0].note)

        // 2. 2026-09-02, gc-2
        assertEquals(EntityId("gc-2"), result[1].id)
        assertEquals(GoalContributionDirection.ADD, result[1].direction)
        assertTrue(result[1].formattedAmount.startsWith("+"))
        assertEquals("Not 2", result[1].note)

        // 3. 2026-09-01, gc-1
        assertEquals(EntityId("gc-1"), result[2].id)
        assertEquals(GoalContributionDirection.ADD, result[2].direction)
    }

    @Test
    fun debtPayments_toSortedHistoryUiModels_sortsByPaidOnDescThenIdDesc() {
        val payments = listOf(
            DebtPayment(
                id = EntityId("dp-1"),
                debtId = EntityId("d-1"),
                amount = Money(1000_00L, Currency.TRY),
                paidOn = LocalDate(2026, 8, 10),
                createdAt = Instant.fromEpochMilliseconds(1000L),
            ),
            DebtPayment(
                id = EntityId("dp-3"),
                debtId = EntityId("d-1"),
                amount = Money(500_00L, Currency.TRY),
                paidOn = LocalDate(2026, 9, 1),
                createdAt = Instant.fromEpochMilliseconds(3000L),
            ),
            DebtPayment(
                id = EntityId("dp-2"),
                debtId = EntityId("d-1"),
                amount = Money(750_00L, Currency.TRY),
                paidOn = LocalDate(2026, 9, 1),
                createdAt = Instant.fromEpochMilliseconds(2000L),
            ),
        )

        val result = payments.toSortedHistoryUiModels()

        assertEquals(3, result.size)
        // 1. 2026-09-01, dp-3 (id desc)
        assertEquals(EntityId("dp-3"), result[0].id)
        assertEquals(LocalDate(2026, 9, 1), result[0].paidOn)

        // 2. 2026-09-01, dp-2
        assertEquals(EntityId("dp-2"), result[1].id)

        // 3. 2026-08-10, dp-1
        assertEquals(EntityId("dp-1"), result[2].id)
        assertEquals(LocalDate(2026, 8, 10), result[2].paidOn)
    }
}

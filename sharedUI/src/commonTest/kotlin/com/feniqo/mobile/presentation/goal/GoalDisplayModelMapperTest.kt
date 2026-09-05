package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RateBasisPoints
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalDisplayModelMapperTest {

    private fun sampleGoal(
        id: String,
        name: String = "Test Hedef",
        targetAmountMinor: Long = 100_000L,
        currentAmountMinor: Long = 20_000L,
        currency: Currency = Currency.TRY,
        targetDate: LocalDate = LocalDate(2026, 12, 31),
        color: CategoryColor = CategoryColor("#2E7D32"),
        icon: CategoryIcon? = CategoryIcon("savings"),
    ): Goal = Goal(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        name = name,
        targetAmount = Money(targetAmountMinor, currency),
        currentAmount = Money(currentAmountMinor, currency),
        targetDate = targetDate,
        color = color,
        icon = icon,
        createdAt = Instant.fromEpochMilliseconds(1000L),
    )

    @Test
    fun mapItem_calculatesStatusAndProgressCorrectly() {
        val inProgressGoal = sampleGoal(
            id = "g-1",
            name = "Tatil Fonu",
            targetAmountMinor = 100_000L,
            currentAmountMinor = 25_000L,
            targetDate = LocalDate(2026, 12, 31),
        )

        val model = GoalDisplayModelMapper.mapItem(inProgressGoal)

        assertEquals(EntityId("g-1"), model.id)
        assertEquals("Tatil Fonu", model.name)
        assertEquals(GoalStatus.IN_PROGRESS, model.status)
        assertFalse(model.isAchieved)
        assertEquals(100_000L, model.targetAmount.amountMinor)
        assertEquals(25_000L, model.currentAmount.amountMinor)
        assertEquals(75_000L, model.remainingAmount.amountMinor)
        assertEquals(RateBasisPoints(2_500), model.progressBasisPoints)
        assertEquals(0.25f, model.progressFraction, 0.001f)
        assertEquals("1.000,00 ₺", model.formattedTargetAmount)
        assertEquals("250,00 ₺", model.formattedCurrentAmount)
        assertEquals("750,00 ₺", model.formattedRemainingAmount)
        assertEquals("31 Aralık 2026", model.formattedTargetDate)
        assertEquals("#2E7D32", model.colorHex)
        assertEquals("savings", model.iconKey)
    }

    @Test
    fun mapItem_calculatesFiftyPercentProgressCorrectly() {
        val halfGoal = sampleGoal(
            id = "g-half",
            name = "Yarı Hedef",
            targetAmountMinor = 100_000L,
            currentAmountMinor = 50_000L,
        )

        val model = GoalDisplayModelMapper.mapItem(halfGoal)

        assertEquals(GoalStatus.IN_PROGRESS, model.status)
        assertFalse(model.isAchieved)
        assertEquals(50_000L, model.currentAmount.amountMinor)
        assertEquals(50_000L, model.remainingAmount.amountMinor)
        assertEquals(RateBasisPoints(5_000), model.progressBasisPoints)
        assertEquals(0.5f, model.progressFraction, 0.001f)
        assertEquals("1.000,00 ₺", model.formattedTargetAmount)
        assertEquals("500,00 ₺", model.formattedCurrentAmount)
        assertEquals("500,00 ₺", model.formattedRemainingAmount)
    }

    @Test
    fun mapItem_preservesExceededAmountAndBasisPointsWhileClampingProgressFractionTo1() {
        val exceededGoal = sampleGoal(
            id = "g-2",
            name = "Aşılan Hedef",
            targetAmountMinor = 100_000L,
            currentAmountMinor = 120_000L, // 120%
        )

        val model = GoalDisplayModelMapper.mapItem(exceededGoal)

        assertEquals(GoalStatus.ACHIEVED, model.status)
        assertTrue(model.isAchieved)
        assertEquals(120_000L, model.currentAmount.amountMinor) // Gerçek miktar korunur!
        assertEquals(0L, model.remainingAmount.amountMinor)
        assertEquals(RateBasisPoints(12_000), model.progressBasisPoints) // Gerçek oran %120 = 12_000 basis points!
        assertEquals(1.0f, model.progressFraction, 0.001f) // Yalnız UI progress bar için clamp edilir!
        assertEquals("1.200,00 ₺", model.formattedCurrentAmount)
        assertEquals("0,00 ₺", model.formattedRemainingAmount)
    }

    @Test
    fun mapItem_exactAchievedGoal() {
        val exactGoal = sampleGoal(
            id = "g-3",
            targetAmountMinor = 50_000L,
            currentAmountMinor = 50_000L,
        )

        val model = GoalDisplayModelMapper.mapItem(exactGoal)

        assertEquals(GoalStatus.ACHIEVED, model.status)
        assertTrue(model.isAchieved)
        assertEquals(0L, model.remainingAmount.amountMinor)
        assertEquals(RateBasisPoints(10_000), model.progressBasisPoints)
        assertEquals(1.0f, model.progressFraction, 0.001f)
    }

    @Test
    fun map_deterministicOrdering_inProgressFirstThenAchieved_thenByDateAndId() {
        val g1 = sampleGoal(id = "g-1", targetAmountMinor = 100_000L, currentAmountMinor = 20_000L, targetDate = LocalDate(2026, 12, 31)) // In progress
        val g2 = sampleGoal(id = "g-2", targetAmountMinor = 100_000L, currentAmountMinor = 100_000L, targetDate = LocalDate(2026, 10, 1)) // Achieved, earlier date
        val g3 = sampleGoal(id = "g-3", targetAmountMinor = 50_000L, currentAmountMinor = 10_000L, targetDate = LocalDate(2026, 8, 15)) // In progress, earlier date
        val g4 = sampleGoal(id = "g-4", targetAmountMinor = 50_000L, currentAmountMinor = 60_000L, targetDate = LocalDate(2026, 10, 1)) // Achieved, same date as g2, id g-4
        val g5 = sampleGoal(id = "g-5", targetAmountMinor = 50_000L, currentAmountMinor = 10_000L, targetDate = LocalDate(2026, 8, 15)) // In progress, same date as g3, id g-5

        val result = GoalDisplayModelMapper.map(listOf(g1, g2, g3, g4, g5))

        // Expected order:
        // Group 1 (IN_PROGRESS):
        // 1. g3 (2026-08-15, g-3)
        // 2. g5 (2026-08-15, g-5)
        // 3. g1 (2026-12-31, g-1)
        // Group 2 (ACHIEVED):
        // 4. g2 (2026-10-01, g-2)
        // 5. g4 (2026-10-01, g-4)
        assertEquals(listOf("g-3", "g-5", "g-1", "g-2", "g-4"), result.map { it.id.value })
    }

    @Test
    fun goalsUiState_isEmptyContract() {
        assertTrue(GoalsUiState(isLoading = false, goals = emptyList(), observationError = null).isEmpty)
        assertFalse(GoalsUiState(isLoading = true, goals = emptyList(), observationError = null).isEmpty)
        assertFalse(GoalsUiState(isLoading = false, goals = emptyList(), observationError = com.feniqo.mobile.presentation.common.FinanceUiMessage.GENERIC_ERROR).isEmpty)
        assertFalse(
            GoalsUiState(
                isLoading = false,
                goals = listOf(GoalDisplayModelMapper.mapItem(sampleGoal("g-1"))),
                observationError = null,
            ).isEmpty,
        )
    }
}


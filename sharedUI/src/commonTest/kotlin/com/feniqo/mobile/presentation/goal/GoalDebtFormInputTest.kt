package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.debt.DebtFormFieldError
import com.feniqo.mobile.presentation.debt.DebtFormDraft
import com.feniqo.mobile.presentation.debt.DebtFormInput
import com.feniqo.mobile.presentation.debt.DebtFormNormalizationResult
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalDebtFormInputTest {

    // ==========================================
    // GOAL FORM TESTS
    // ==========================================

    @Test
    fun goalFormInput_validCreateInput_producesValidDraftAndCreateCommand() {
        val input = GoalFormInput(
            goalId = null,
            nameInput = "  Yeni Araba Fonu  ",
            targetAmountInput = "500000",
            currency = Currency.TRY,
            initialAmountInput = "50000",
            targetDate = LocalDate(2027, 6, 30),
            colorHex = "#1976D2",
            iconKey = "car",
        )

        val result = input.toDraft()
        val validResult = assertIs<GoalFormNormalizationResult.Valid>(result)
        val draft = validResult.draft

        assertTrue(draft.isCreateMode)
        assertEquals("Yeni Araba Fonu", draft.name)
        assertEquals(Money(50_000_000L, Currency.TRY), draft.targetAmount)
        assertEquals(Money(5_000_000L, Currency.TRY), draft.initialAmount)
        assertEquals(LocalDate(2027, 6, 30), draft.targetDate)
        assertEquals("#1976D2", draft.colorHex)
        assertEquals("car", draft.iconKey)

        val createCmd = draft.toCreateCommand()
        assertEquals("Yeni Araba Fonu", createCmd.name)
        assertEquals(Money(50_000_000L, Currency.TRY), createCmd.targetAmount)
        assertEquals(Money(5_000_000L, Currency.TRY), createCmd.initialAmount)
        assertEquals(CategoryColor("#1976D2"), createCmd.color)
        assertEquals(CategoryIcon("car"), createCmd.icon)

        // Edit modunda create komutu fail-closed patlamalıdır
        assertFailsWith<IllegalStateException> {
            draft.copy(goalId = EntityId("goal-123")).toCreateCommand()
        }
    }

    @Test
    fun goalFormInput_validEditInput_producesUpdateCommandWithoutInitialAmount() {
        val input = GoalFormInput(
            goalId = EntityId("goal-99"),
            nameInput = "Revize Tatil",
            targetAmountInput = "10000",
            currency = Currency.EUR,
            initialAmountInput = "", // Editte başlangıç girilemez
            targetDate = LocalDate(2026, 12, 31),
            colorHex = "#2E7D32",
            iconKey = null,
        )

        val result = input.toDraft()
        val validResult = assertIs<GoalFormNormalizationResult.Valid>(result)
        val draft = validResult.draft

        assertTrue(draft.isEditMode)
        assertNull(draft.initialAmount)

        val updateCmd = draft.toUpdateCommand()
        assertEquals(EntityId("goal-99"), updateCmd.id)
        assertEquals("Revize Tatil", updateCmd.name)
        assertEquals(Money(1_000_000L, Currency.EUR), updateCmd.targetAmount)
        assertEquals(CategoryColor("#2E7D32"), updateCmd.color)
        assertNull(updateCmd.icon)

        // Create modunda update komutu fail-closed patlamalıdır
        assertFailsWith<IllegalStateException> {
            draft.copy(goalId = null).toUpdateCommand()
        }
    }

    @Test
    fun goalFormInput_initialAmountNotAllowedInEditMode() {
        val input = GoalFormInput(
            goalId = EntityId("goal-1"),
            nameInput = "Test",
            targetAmountInput = "1000",
            initialAmountInput = "200", // Editte girilirse hata dönmeli
            targetDate = LocalDate(2026, 12, 31),
        )

        val result = input.toDraft()
        val invalidResult = assertIs<GoalFormNormalizationResult.Invalid>(result)
        assertEquals(GoalFormFieldError.INITIAL_AMOUNT_NOT_ALLOWED_IN_EDIT, invalidResult.errors.initialAmountError)
    }

    @Test
    fun goalFormInput_fieldValidations() {
        // Boş isim
        val r1 = GoalFormInput(nameInput = "  ", targetAmountInput = "100", targetDate = LocalDate(2026, 12, 31)).toDraft()
        assertEquals(GoalFormFieldError.NAME_REQUIRED, assertIs<GoalFormNormalizationResult.Invalid>(r1).errors.nameError)

        // Hedef tutar boş veya <= 0
        val r2 = GoalFormInput(nameInput = "Test", targetAmountInput = "0", targetDate = LocalDate(2026, 12, 31)).toDraft()
        assertEquals(GoalFormFieldError.TARGET_AMOUNT_NON_POSITIVE, assertIs<GoalFormNormalizationResult.Invalid>(r2).errors.targetAmountError)

        // Tarih seçilmemiş
        val r3 = GoalFormInput(nameInput = "Test", targetAmountInput = "100", targetDate = null).toDraft()
        assertEquals(GoalFormFieldError.TARGET_DATE_REQUIRED, assertIs<GoalFormNormalizationResult.Invalid>(r3).errors.targetDateError)

        // Geçersiz renk
        val r4 = GoalFormInput(nameInput = "Test", targetAmountInput = "100", targetDate = LocalDate(2026, 12, 31), colorHex = "invalid").toDraft()
        assertEquals(GoalFormFieldError.COLOR_INVALID, assertIs<GoalFormNormalizationResult.Invalid>(r4).errors.colorError)
    }

    @Test
    fun goalFormDraft_fromDomainPreservesAllFields() {
        val domain = Goal(
            id = EntityId("g-1"),
            ownerId = EntityId("u-1"),
            workspaceId = null,
            name = "Ev",
            targetAmount = Money(2_000_000L, Currency.TRY),
            currentAmount = Money(500_000L, Currency.TRY),
            targetDate = LocalDate(2028, 1, 1),
            color = CategoryColor("#2E7D32"),
            icon = CategoryIcon("savings"),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        val draft = GoalFormDraft.fromDomain(domain)
        assertEquals(EntityId("g-1"), draft.goalId)
        assertEquals("Ev", draft.name)
        assertEquals(Money(2_000_000L, Currency.TRY), draft.targetAmount)
        assertNull(draft.initialAmount)
        assertEquals(LocalDate(2028, 1, 1), draft.targetDate)
        assertEquals("#2E7D32", draft.colorHex)
        assertEquals("savings", draft.iconKey)
    }

    // ==========================================
    // DEBT FORM TESTS
    // ==========================================

    @Test
    fun debtFormInput_validCreateInput_producesValidDraftAndCreateCommand() {
        val input = DebtFormInput(
            debtId = null,
            titleInput = "  Ahmet'e Verilen Borç  ",
            amountInput = "1500",
            currency = Currency.TRY,
            type = DebtType.RECEIVABLE,
            dueDate = LocalDate(2026, 11, 15),
            descriptionInput = "Elden verildi",
        )

        val result = input.toDraft()
        val validResult = assertIs<DebtFormNormalizationResult.Valid>(result)
        val draft = validResult.draft

        assertTrue(draft.isCreateMode)
        assertEquals("Ahmet'e Verilen Borç", draft.title)
        assertEquals(Money(150_000L, Currency.TRY), draft.amount)
        assertEquals(DebtType.RECEIVABLE, draft.type)
        assertEquals(LocalDate(2026, 11, 15), draft.dueDate)
        assertEquals("Elden verildi", draft.description)

        val createCmd = draft.toCreateCommand()
        assertEquals("Ahmet'e Verilen Borç", createCmd.title)
        assertEquals(Money(150_000L, Currency.TRY), createCmd.amount)
        assertEquals(DebtType.RECEIVABLE, createCmd.type)
        assertEquals(LocalDate(2026, 11, 15), createCmd.dueDate)
        assertEquals("Elden verildi", createCmd.description)

        // Edit modunda create komutu fail-closed patlamalıdır
        assertFailsWith<IllegalStateException> {
            draft.copy(debtId = EntityId("debt-123")).toCreateCommand()
        }
    }

    @Test
    fun debtFormInput_validEditInput_supportsTypeChangeAndUpdateCommand() {
        val input = DebtFormInput(
            debtId = EntityId("d-1"),
            titleInput = "Güncellenen Kredi",
            amountInput = "2000",
            currency = Currency.USD,
            type = DebtType.DEBT, // Tür değişimi serbest
            dueDate = LocalDate(2026, 12, 1),
            descriptionInput = "",
        )

        val result = input.toDraft()
        val validResult = assertIs<DebtFormNormalizationResult.Valid>(result)
        val draft = validResult.draft

        assertTrue(draft.isEditMode)
        assertNull(draft.description) // Boşluk null'a normalize edilir

        val updateCmd = draft.toUpdateCommand()
        assertEquals(EntityId("d-1"), updateCmd.id)
        assertEquals("Güncellenen Kredi", updateCmd.title)
        assertEquals(Money(200_000L, Currency.USD), updateCmd.amount)
        assertEquals(DebtType.DEBT, updateCmd.type)
        assertEquals(LocalDate(2026, 12, 1), updateCmd.dueDate)
        assertNull(updateCmd.description)

        // Create modunda update komutu fail-closed patlamalıdır
        assertFailsWith<IllegalStateException> {
            draft.copy(debtId = null).toUpdateCommand()
        }
    }

    @Test
    fun debtFormInput_fieldValidations() {
        // Boş başlık
        val r1 = DebtFormInput(titleInput = "  ", amountInput = "100", dueDate = LocalDate(2026, 12, 31)).toDraft()
        assertEquals(DebtFormFieldError.TITLE_REQUIRED, assertIs<DebtFormNormalizationResult.Invalid>(r1).errors.titleError)

        // Tutar <= 0
        val r2 = DebtFormInput(titleInput = "Test", amountInput = "0", dueDate = LocalDate(2026, 12, 31)).toDraft()
        assertEquals(DebtFormFieldError.AMOUNT_NON_POSITIVE, assertIs<DebtFormNormalizationResult.Invalid>(r2).errors.amountError)

        // Vade tarihi yok
        val r3 = DebtFormInput(titleInput = "Test", amountInput = "100", dueDate = null).toDraft()
        assertEquals(DebtFormFieldError.DUE_DATE_REQUIRED, assertIs<DebtFormNormalizationResult.Invalid>(r3).errors.dueDateError)
    }

    @Test
    fun debtFormDraft_fromDomainPreservesAllFields() {
        val domain = Debt(
            id = EntityId("d-1"),
            ownerId = EntityId("u-1"),
            workspaceId = null,
            title = "Kredi",
            amount = Money(100_000L, Currency.TRY),
            type = DebtType.DEBT,
            dueDate = LocalDate(2026, 10, 10),
            status = DebtStatus.OPEN,
            description = "Açıklama",
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

        val draft = DebtFormDraft.fromDomain(domain)
        assertEquals(EntityId("d-1"), draft.debtId)
        assertEquals("Kredi", draft.title)
        assertEquals(Money(100_000L, Currency.TRY), draft.amount)
        assertEquals(DebtType.DEBT, draft.type)
        assertEquals(LocalDate(2026, 10, 10), draft.dueDate)
        assertEquals("Açıklama", draft.description)
    }
}

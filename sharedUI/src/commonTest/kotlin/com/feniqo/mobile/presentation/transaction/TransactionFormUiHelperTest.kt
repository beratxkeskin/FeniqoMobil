package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.component.formatDisplayDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionFormUiHelperTest {

    @Test
    fun splitEditingRequiresSharedWorkspaceAndWriteRole() {
        val member = com.feniqo.mobile.presentation.workspace.WorkspaceMemberUiModel(
            com.feniqo.mobile.domain.model.EntityId("member"), "Ben",
            com.feniqo.mobile.domain.model.WorkspaceRole.EDITOR, true,
        )
        val shared = TransactionFormUiState(
            activeWorkspaceId = com.feniqo.mobile.domain.model.EntityId("workspace"),
            workspaceMembers = listOf(member),
        )
        assertTrue(shared.canManageSplit)
        assertFalse(shared.copy(workspaceMembers = emptyList()).canManageSplit)
        assertFalse(shared.copy(workspaceMembers = listOf(member.copy(
            role = com.feniqo.mobile.domain.model.WorkspaceRole.VIEWER,
        ))).canManageSplit)
        val personal = shared.copy(activeWorkspaceType = com.feniqo.mobile.domain.model.WorkspaceType.PERSONAL)
        assertFalse(personal.isSharedExpense)
        assertFalse(personal.canManageSplit)
        assertFalse(shared.copy(type = TransactionType.INCOME).canManageSplit)
    }

    @Test
    fun formatDisplayDate_formatsTurkishMonthsCorrectly() {
        assertEquals("1 Ocak 2026", formatDisplayDate(LocalDate(2026, 1, 1)))
        assertEquals("15 Şubat 2026", formatDisplayDate(LocalDate(2026, 2, 15)))
        assertEquals("23 Ağustos 2026", formatDisplayDate(LocalDate(2026, 8, 23)))
        assertEquals("31 Aralık 2026", formatDisplayDate(LocalDate(2026, 12, 31)))
    }

    @Test
    fun transactionFormFieldError_toDisplayText_returnsTurkishMessages() {
        assertEquals("Tutar boş bırakılamaz.", TransactionFormFieldError.AMOUNT_REQUIRED.toDisplayText())
        assertEquals("Geçerli bir tutar girin.", TransactionFormFieldError.AMOUNT_INVALID.toDisplayText())
        assertEquals("Tutar sıfırdan büyük olmalıdır.", TransactionFormFieldError.AMOUNT_NON_POSITIVE.toDisplayText())
        assertEquals("Tutar izin verilen sınırı aşıyor.", TransactionFormFieldError.AMOUNT_TOO_LARGE.toDisplayText())
        assertEquals("Lütfen bir kategori seçin.", TransactionFormFieldError.CATEGORY_REQUIRED.toDisplayText())
        assertEquals("Bu kategori artık kullanılamıyor. Lütfen başka bir kategori seçin.", TransactionFormFieldError.CATEGORY_UNAVAILABLE.toDisplayText())
        assertEquals("Lütfen bir tarih seçin.", TransactionFormFieldError.DATE_REQUIRED.toDisplayText())
        assertEquals("İşlem tarihi bugünden ileri olamaz.", TransactionFormFieldError.DATE_IN_FUTURE.toDisplayText())
        assertEquals("Açıklama 100 karakterden uzun olamaz.", TransactionFormFieldError.DESCRIPTION_TOO_LONG.toDisplayText())
        assertEquals("Taksit sayısı 2 ile 60 arasında olmalıdır.", TransactionFormFieldError.INSTALLMENT_COUNT_INVALID.toDisplayText())
        assertEquals("Toplam tutar seçilen taksit sayısı için çok küçük.", TransactionFormFieldError.INSTALLMENT_AMOUNT_TOO_SMALL.toDisplayText())
        assertEquals("Lütfen harcamayı ödeyen kişiyi seçin.", TransactionFormFieldError.SPLIT_PAYER_REQUIRED.toDisplayText())
        assertEquals("En az bir katılımcı seçilmelidir.", TransactionFormFieldError.SPLIT_PARTICIPANTS_REQUIRED.toDisplayText())
        assertEquals("Ödeyen kişi katılımcılar arasında olmalıdır.", TransactionFormFieldError.SPLIT_PAYER_NOT_IN_PARTICIPANTS.toDisplayText())
    }

    @Test
    fun isInstallmentOptionAvailable_onlyTrueForNewExpenseWithCreditCard() {
        // 1. Yeni gider + kredi kartı -> true
        val state1 = TransactionFormUiState(
            isEditMode = false,
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT_CARD,
        )
        assertTrue(state1.isInstallmentOptionAvailable)

        // 2. Düzenleme modu -> false
        val state2 = TransactionFormUiState(
            isEditMode = true,
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT_CARD,
        )
        assertFalse(state2.isInstallmentOptionAvailable)

        // 3. Gelir -> false
        val state3 = TransactionFormUiState(
            isEditMode = false,
            type = TransactionType.INCOME,
            paymentMethod = PaymentMethod.CREDIT_CARD,
        )
        assertFalse(state3.isInstallmentOptionAvailable)

        // 4. Nakit -> false
        val state4 = TransactionFormUiState(
            isEditMode = false,
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CASH,
        )
        assertFalse(state4.isInstallmentOptionAvailable)
    }

    @Test
    fun splitDerivedProperties_behaveCorrectly() {
        val user1 = com.feniqo.mobile.domain.model.EntityId("user-1")
        val user2 = com.feniqo.mobile.domain.model.EntityId("user-2")
        val wsId = com.feniqo.mobile.domain.model.EntityId("ws-1")

        val members = listOf(
            com.feniqo.mobile.presentation.workspace.WorkspaceMemberUiModel(
                userId = user1,
                displayName = "Ahmet",
                role = com.feniqo.mobile.domain.model.WorkspaceRole.OWNER,
                isCurrentUser = true,
            ),
            com.feniqo.mobile.presentation.workspace.WorkspaceMemberUiModel(
                userId = user2,
                displayName = "Ayşe",
                role = com.feniqo.mobile.domain.model.WorkspaceRole.EDITOR,
                isCurrentUser = false,
            ),
        )

        // 1. Personal mode -> isSharedExpense is false, canSubmitSplit is true
        val personalState = TransactionFormUiState(
            activeWorkspaceId = null,
            type = TransactionType.EXPENSE,
        )
        assertFalse(personalState.isSharedExpense)
        assertTrue(personalState.canSubmitSplit)

        // 2. Shared INCOME -> isSharedExpense is false, canSubmitSplit is true
        val sharedIncomeState = TransactionFormUiState(
            activeWorkspaceId = wsId,
            type = TransactionType.INCOME,
        )
        assertFalse(sharedIncomeState.isSharedExpense)
        assertTrue(sharedIncomeState.canSubmitSplit)

        // 3. Shared EXPENSE while loading members -> canSubmitSplit is false
        val loadingState = TransactionFormUiState(
            activeWorkspaceId = wsId,
            type = TransactionType.EXPENSE,
            isLoadingWorkspaceMembers = true,
            workspaceMembers = members,
            selectedPaidByUserId = user1,
            selectedParticipantUserIds = setOf(user1),
        )
        assertTrue(loadingState.isSharedExpense)
        assertFalse(loadingState.canSubmitSplit)

        // 4. Shared EXPENSE with valid payer and participants -> canSubmitSplit is true
        val validState = TransactionFormUiState(
            activeWorkspaceId = wsId,
            type = TransactionType.EXPENSE,
            isLoadingWorkspaceMembers = false,
            workspaceMembers = members,
            selectedPaidByUserId = user1,
            selectedParticipantUserIds = setOf(user1, user2),
        )
        assertTrue(validState.isSharedExpense)
        assertTrue(validState.canSubmitSplit)
        assertEquals(2, validState.eligibleSplitMembers.size)

        // 5. Shared EXPENSE without payer -> canSubmitSplit is false
        val noPayerState = validState.copy(selectedPaidByUserId = null)
        assertFalse(noPayerState.canSubmitSplit)

        // 6. Shared EXPENSE without participants -> canSubmitSplit is false
        val noParticipantsState = validState.copy(selectedParticipantUserIds = emptySet())
        assertFalse(noParticipantsState.canSubmitSplit)

        // 7. Shared EXPENSE where payer is not in participants -> canSubmitSplit is false
        val payerNotInParticipantsState = validState.copy(
            selectedPaidByUserId = user1,
            selectedParticipantUserIds = setOf(user2),
        )
        assertFalse(payerNotInParticipantsState.canSubmitSplit)
    }
}

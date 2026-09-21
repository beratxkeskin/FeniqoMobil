package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionDetailsDraftTest {
    @Test fun editingDetailsDoesNotMutateFormAndPayerRemainsParticipant() {
        val first = EntityId("first")
        val second = EntityId("second")
        val state = TransactionFormUiState(note = "Eski", selectedPaidByUserId = first,
            selectedParticipantUserIds = setOf(first))
        val draft = TransactionDetailsDraft.from(state).copy(note = "Yeni").selectPayer(second)
        assertEquals("Eski", state.note)
        assertEquals(setOf(first), state.selectedParticipantUserIds)
        assertTrue(second in draft.participants)
        assertEquals(draft, draft.toggleParticipant(second))
        assertEquals(setOf(second), draft.toggleParticipant(first).participants)
    }

    @Test
    fun draft_preservesSplitModeAndCustomShares_andDoesNotAutoRedistributeOnPayerOrParticipantChange() {
        val user1 = EntityId("user1")
        val user2 = EntityId("user2")
        val user3 = EntityId("user3")

        val state = TransactionFormUiState(
            selectedPaidByUserId = user1,
            selectedParticipantUserIds = setOf(user1, user2),
            splitMode = com.feniqo.mobile.domain.model.TransactionSplitMode.CUSTOM,
            customSharesText = mapOf(user1 to "0,00", user2 to "100,00"),
        )

        val draft = TransactionDetailsDraft.from(state)
        assertEquals(com.feniqo.mobile.domain.model.TransactionSplitMode.CUSTOM, draft.splitMode)
        assertEquals("0,00", draft.customSharesText[user1])
        assertEquals("100,00", draft.customSharesText[user2])

        // Updating custom share
        val updatedDraft = draft.updateCustomShare(user1, "20,00")
        assertEquals("20,00", updatedDraft.customSharesText[user1])
        assertEquals("0,00", state.customSharesText[user1]) // state immutability

        // Selecting new payer does NOT auto-redistribute or alter shares
        val changedPayerDraft = updatedDraft.selectPayer(user2)
        assertEquals(user2, changedPayerDraft.payer)
        assertEquals("20,00", changedPayerDraft.customSharesText[user1])
        assertEquals("100,00", changedPayerDraft.customSharesText[user2])

        // Adding participant does not redistribute existing shares
        val addedParticipantDraft = changedPayerDraft.toggleParticipant(user3)
        assertTrue(user3 in addedParticipantDraft.participants)
        assertEquals("20,00", addedParticipantDraft.customSharesText[user1])
        assertEquals("100,00", addedParticipantDraft.customSharesText[user2])
        assertEquals(null, addedParticipantDraft.customSharesText[user3])

        // Switching mode
        val equalDraft = addedParticipantDraft.setSplitMode(com.feniqo.mobile.domain.model.TransactionSplitMode.EQUAL)
        assertEquals(com.feniqo.mobile.domain.model.TransactionSplitMode.EQUAL, equalDraft.splitMode)
    }
}

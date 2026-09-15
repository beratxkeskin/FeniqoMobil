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
}

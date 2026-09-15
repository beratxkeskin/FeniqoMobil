package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.EntityId

/** Alt ekran iptali ana formdaki değerleri değiştirmesin diye ayrı tutulan taslak. */
data class TransactionDetailsDraft(
    val note: String,
    val installmentEnabled: Boolean,
    val installmentCount: String,
    val payer: EntityId?,
    val participants: Set<EntityId>,
    val hasReceipt: Boolean,
) {
    fun selectPayer(id: EntityId) = copy(payer = id, participants = participants + id)
    fun toggleParticipant(id: EntityId) = if (id == payer) this else copy(
        participants = if (id in participants) participants - id else participants + id,
    )

    companion object {
        fun from(state: TransactionFormUiState) = TransactionDetailsDraft(
            state.note, state.isInstallmentEnabled, state.installmentCountText,
            state.selectedPaidByUserId, state.selectedParticipantUserIds, state.hasReceipt,
        )
    }
}

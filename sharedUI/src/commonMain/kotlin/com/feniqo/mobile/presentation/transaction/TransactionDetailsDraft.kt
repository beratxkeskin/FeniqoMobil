package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionSplitMode

/** Alt ekran iptali ana formdaki değerleri değiştirmesin diye ayrı tutulan taslak. */
data class TransactionDetailsDraft(
    val note: String,
    val installmentEnabled: Boolean,
    val installmentCount: String,
    val payer: EntityId?,
    val participants: Set<EntityId>,
    val hasReceipt: Boolean,
    val splitMode: TransactionSplitMode = TransactionSplitMode.EQUAL,
    val customSharesText: Map<EntityId, String> = emptyMap(),
) {
    fun selectPayer(id: EntityId) = copy(payer = id, participants = participants + id)
    fun toggleParticipant(id: EntityId) = if (id == payer) this else copy(
        participants = if (id in participants) participants - id else participants + id,
    )
    fun setSplitMode(mode: TransactionSplitMode) = copy(splitMode = mode)
    fun updateCustomShare(userId: EntityId, text: String) = copy(
        customSharesText = customSharesText + (userId to text),
    )

    companion object {
        fun from(state: TransactionFormUiState) = TransactionDetailsDraft(
            note = state.note,
            installmentEnabled = state.isInstallmentEnabled,
            installmentCount = state.installmentCountText,
            payer = state.selectedPaidByUserId,
            participants = state.selectedParticipantUserIds,
            hasReceipt = state.hasReceipt,
            splitMode = state.splitMode,
            customSharesText = state.customSharesText,
        )
    }
}

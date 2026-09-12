package com.feniqo.mobile.presentation.workspace

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.presentation.common.FinanceUiMessage

/**
 * Bir üyenin ortak giderler sonundaki net bakiye durumunu temsil eder.
 */
enum class MemberBalanceStatus {
    CREDITOR, // Alacaklı (+ net bakiye)
    DEBTOR,   // Borçlu (- net bakiye)
    SETTLED,  // Dengede (0 net bakiye)
}

/**
 * UI katmanında listelenen üye bakiye modeli.
 */
data class WorkspaceMemberBalanceUiModel(
    val userId: EntityId,
    val displayName: String,
    val role: WorkspaceRole?,
    val isCurrentUser: Boolean,
    val netAmountMinor: Long,
    val formattedAmount: String,
    val status: MemberBalanceStatus,
)

/**
 * Borçludan alacaklıya önerilen tekil transfer modeli.
 */
data class WorkspaceSettlementTransferUiModel(
    val fromUserId: EntityId,
    val fromDisplayName: String,
    val toUserId: EntityId,
    val toDisplayName: String,
    val amount: Money,
    val formattedAmount: String,
    val suggestionText: String,
)

/**
 * Workspace Ödeşme Ekranı UI State modeli.
 */
data class WorkspaceSettlementUiState(
    val isLoading: Boolean = true,
    val workspaceId: EntityId? = null,
    val workspaceName: String = "",
    val memberCount: Int = 0,
    val isCurrentUserMember: Boolean = false,
    val memberBalances: List<WorkspaceMemberBalanceUiModel> = emptyList(),
    val suggestedTransfers: List<WorkspaceSettlementTransferUiModel> = emptyList(),
    val totalExpenseAmount: Money? = null,
    val formattedTotalExpense: String = "",
    val hasExcludedExpenses: Boolean = false,
    val errorMessage: FinanceUiMessage? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && memberBalances.isEmpty()

    val isAllSettled: Boolean
        get() = !isLoading && errorMessage == null && memberBalances.isNotEmpty() && suggestedTransfers.isEmpty()
}

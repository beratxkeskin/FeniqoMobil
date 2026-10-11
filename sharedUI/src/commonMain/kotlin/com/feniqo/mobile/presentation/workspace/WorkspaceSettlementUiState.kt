package com.feniqo.mobile.presentation.workspace

import com.feniqo.mobile.domain.model.Currency
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
    val displayName: String? = null,
    val role: WorkspaceRole?,
    val isCurrentUser: Boolean,
    val netAmountMinor: Long,
    val status: MemberBalanceStatus,
)

/**
 * Borçludan alacaklıya önerilen tekil transfer modeli.
 */
data class WorkspaceSettlementTransferUiModel(
    val fromUserId: EntityId,
    val fromDisplayName: String? = null,
    val fromIsCurrentUser: Boolean,
    val toUserId: EntityId,
    val toDisplayName: String? = null,
    val toIsCurrentUser: Boolean,
    val amount: Money,
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
    val currency: Currency = Currency.TRY,
    val memberBalances: List<WorkspaceMemberBalanceUiModel> = emptyList(),
    val suggestedTransfers: List<WorkspaceSettlementTransferUiModel> = emptyList(),
    val totalExpenseAmount: Money? = null,
    val hasExcludedExpenses: Boolean = false,
    val errorMessage: FinanceUiMessage? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && memberBalances.isEmpty()

    val isAllSettled: Boolean
        get() = !isLoading && errorMessage == null && memberBalances.isNotEmpty() && suggestedTransfers.isEmpty()
}

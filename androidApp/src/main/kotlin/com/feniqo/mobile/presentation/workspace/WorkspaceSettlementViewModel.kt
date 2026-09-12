package com.feniqo.mobile.presentation.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.usecase.ObserveWorkspaceSettlementUseCase
import com.feniqo.mobile.domain.usecase.WorkspaceSettlementResult
import com.feniqo.mobile.navigation.WorkspaceSettlementRouteIdResult
import com.feniqo.mobile.navigation.parseWorkspaceSettlementRouteId
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkspaceSettlementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeWorkspaceSettlementUseCase: ObserveWorkspaceSettlementUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceSettlementUiState())
    val uiState: StateFlow<WorkspaceSettlementUiState> = _uiState.asStateFlow()

    init {
        val rawId = savedStateHandle.get<String>("workspaceId")
        when (val parseResult = parseWorkspaceSettlementRouteId(rawId)) {
            is WorkspaceSettlementRouteIdResult.InvalidId -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        workspaceId = null,
                        errorMessage = FinanceUiMessage.WORKSPACE_NOT_FOUND,
                    )
                }
            }
            is WorkspaceSettlementRouteIdResult.ValidId -> {
                val validId = parseResult.id
                _uiState.update { it.copy(workspaceId = validId) }
                observeSettlement(validId)
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun observeSettlement(workspaceId: EntityId) {
        viewModelScope.launch {
            observeWorkspaceSettlementUseCase(workspaceId).collect { result ->
                when (result) {
                    is WorkspaceSettlementResult.WorkspaceNotFound -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                workspaceId = null,
                                workspaceName = "",
                                memberCount = 0,
                                isCurrentUserMember = false,
                                memberBalances = emptyList(),
                                suggestedTransfers = emptyList(),
                                totalExpenseAmount = null,
                                formattedTotalExpense = "",
                                hasExcludedExpenses = false,
                                errorMessage = FinanceUiMessage.WORKSPACE_NOT_FOUND,
                            )
                        }
                    }
                    is WorkspaceSettlementResult.UserNotMember -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                workspaceId = null,
                                workspaceName = "",
                                memberCount = 0,
                                isCurrentUserMember = false,
                                memberBalances = emptyList(),
                                suggestedTransfers = emptyList(),
                                totalExpenseAmount = null,
                                formattedTotalExpense = "",
                                hasExcludedExpenses = false,
                                errorMessage = FinanceUiMessage.WORKSPACE_ACTOR_NOT_MEMBER,
                            )
                        }
                    }
                    is WorkspaceSettlementResult.Success -> {
                        val memberMap = result.members.associateBy { it.userId }
                        val calculatedBalances = result.settlement?.balances?.associateBy { it.userId } ?: emptyMap()

                        val balanceUiModels = result.members
                            .map { member ->
                                val netMinor = calculatedBalances[member.userId]?.netAmountMinor ?: 0L
                                val isCurrent = member.userId == result.currentUserId
                                val displayName = if (isCurrent) "Siz" else "Kullanıcı ${member.userId.value.take(8)}"
                                val status = when {
                                    netMinor > 0L -> MemberBalanceStatus.CREDITOR
                                    netMinor < 0L -> MemberBalanceStatus.DEBTOR
                                    else -> MemberBalanceStatus.SETTLED
                                }
                                val formattedAmount = when {
                                    netMinor > 0L -> "+${MoneyFormatter.format(Money(netMinor, result.workspace.currency))}"
                                    netMinor < 0L -> "-${MoneyFormatter.format(Money(-netMinor, result.workspace.currency))}"
                                    else -> MoneyFormatter.format(Money(0L, result.workspace.currency))
                                }
                                WorkspaceMemberBalanceUiModel(
                                    userId = member.userId,
                                    displayName = displayName,
                                    role = member.role,
                                    isCurrentUser = isCurrent,
                                    netAmountMinor = netMinor,
                                    formattedAmount = formattedAmount,
                                    status = status,
                                )
                            }
                            .sortedWith(
                                compareByDescending<WorkspaceMemberBalanceUiModel> { it.isCurrentUser }
                                    .thenByDescending { it.netAmountMinor }
                                    .thenBy { it.displayName },
                            )

                        val transferUiModels = (result.settlement?.transfers ?: emptyList()).map { transfer ->
                            val fromIsCurrent = transfer.fromUserId == result.currentUserId
                            val toIsCurrent = transfer.toUserId == result.currentUserId
                            val fromName = if (fromIsCurrent) "Siz" else memberMap[transfer.fromUserId]?.let { "Kullanıcı ${it.userId.value.take(8)}" } ?: "Kullanıcı ${transfer.fromUserId.value.take(8)}"
                            val toName = if (toIsCurrent) "Siz" else memberMap[transfer.toUserId]?.let { "Kullanıcı ${it.userId.value.take(8)}" } ?: "Kullanıcı ${transfer.toUserId.value.take(8)}"
                            val formattedTransferAmount = MoneyFormatter.format(transfer.amount)
                            val suggestionText = if (fromIsCurrent) {
                                "Siz, $toName kişisine $formattedTransferAmount ödeyin."
                            } else {
                                "$fromName, $toName kişisine $formattedTransferAmount ödesin."
                            }
                            WorkspaceSettlementTransferUiModel(
                                fromUserId = transfer.fromUserId,
                                fromDisplayName = fromName,
                                toUserId = transfer.toUserId,
                                toDisplayName = toName,
                                amount = transfer.amount,
                                formattedAmount = formattedTransferAmount,
                                suggestionText = suggestionText,
                            )
                        }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                workspaceId = result.workspace.id,
                                workspaceName = result.workspace.name,
                                memberCount = result.members.size,
                                isCurrentUserMember = true,
                                memberBalances = balanceUiModels,
                                suggestedTransfers = transferUiModels,
                                totalExpenseAmount = result.totalExpenseAmount,
                                formattedTotalExpense = MoneyFormatter.format(result.totalExpenseAmount),
                                hasExcludedExpenses = result.hasExcludedExpenses,
                                errorMessage = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

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
                                hasExcludedExpenses = false,
                                errorMessage = FinanceUiMessage.WORKSPACE_ACTOR_NOT_MEMBER,
                            )
                        }
                    }
                    is WorkspaceSettlementResult.Success -> {
                        val calculatedBalances = result.settlement?.balances?.associateBy { it.userId } ?: emptyMap()

                        val balanceUiModels = result.members
                            .map { member ->
                                val netMinor = calculatedBalances[member.userId]?.netAmountMinor ?: 0L
                                val isCurrent = member.userId == result.currentUserId
                                val status = when {
                                    netMinor > 0L -> MemberBalanceStatus.CREDITOR
                                    netMinor < 0L -> MemberBalanceStatus.DEBTOR
                                    else -> MemberBalanceStatus.SETTLED
                                }
                                WorkspaceMemberBalanceUiModel(
                                    userId = member.userId,
                                    displayName = null,
                                    role = member.role,
                                    isCurrentUser = isCurrent,
                                    netAmountMinor = netMinor,
                                    status = status,
                                )
                            }
                            .sortedWith(
                                compareByDescending<WorkspaceMemberBalanceUiModel> { it.isCurrentUser }
                                    .thenByDescending { it.netAmountMinor }
                                    .thenBy { it.userId.value },
                            )

                        val transferUiModels = (result.settlement?.transfers ?: emptyList()).map { transfer ->
                            val fromIsCurrent = transfer.fromUserId == result.currentUserId
                            val toIsCurrent = transfer.toUserId == result.currentUserId
                            WorkspaceSettlementTransferUiModel(
                                fromUserId = transfer.fromUserId,
                                fromDisplayName = null,
                                fromIsCurrentUser = fromIsCurrent,
                                toUserId = transfer.toUserId,
                                toDisplayName = null,
                                toIsCurrentUser = toIsCurrent,
                                amount = transfer.amount,
                            )
                        }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                workspaceId = result.workspace.id,
                                workspaceName = result.workspace.name,
                                memberCount = result.members.size,
                                isCurrentUserMember = true,
                                currency = result.workspace.currency,
                                memberBalances = balanceUiModels,
                                suggestedTransfers = transferUiModels,
                                totalExpenseAmount = result.totalExpenseAmount,
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

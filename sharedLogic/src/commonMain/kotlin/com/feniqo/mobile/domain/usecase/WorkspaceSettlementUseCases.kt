package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.Workspace
import com.feniqo.mobile.domain.model.WorkspaceMember
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.repository.WorkspaceRepository
import com.feniqo.mobile.domain.validation.WorkspaceSettlement
import com.feniqo.mobile.domain.validation.WorkspaceSettlementCalculator
import com.feniqo.mobile.domain.validation.WorkspaceSharedExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Workspace ödeşme hesaplama sonucunun etki alanını temsil eden domain modeli.
 */
sealed interface WorkspaceSettlementResult {
    data object WorkspaceNotFound : WorkspaceSettlementResult
    data object UserNotMember : WorkspaceSettlementResult
    data class Success(
        val workspace: Workspace,
        val members: List<WorkspaceMember>,
        val currentUserId: EntityId,
        val settlement: WorkspaceSettlement?,
        val totalExpenseAmount: Money,
        val hasExcludedExpenses: Boolean,
    ) : WorkspaceSettlementResult
}

/**
 * Belirtilen ortak çalışma alanının aktif üyelerini ve EXPENSE işlemlerini Room SSOT
 * üzerinden gözlemleyerek net bakiyeleri ve ödeşme transfer önerilerini hesaplar.
 *
 * Güvenlik ve Doğrulama Kuralları:
 * - Çalışma alanının var olduğunu doğrular (silinmiş/mevcut olmayan için WorkspaceNotFound).
 * - Aktif oturum kullanıcısının çalışma alanında aktif üye olduğunu doğrular (UserNotMember).
 * - Yalnızca transaction.workspaceId == workspaceId, type == EXPENSE ve amount.amountMinor > 0 olan işlemleri alır.
 * - Katılımcı/payer geçerliliği bozulan veya eksik olan split kayıtlarını fail-closed olarak dışlar.
 */
class ObserveWorkspaceSettlementUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
) {
    operator fun invoke(workspaceId: EntityId): Flow<WorkspaceSettlementResult> =
        combine(
            authRepository.observeSession(),
            workspaceRepository.observeWorkspaces(),
            workspaceRepository.observeMembers(workspaceId),
            transactionRepository.observeTransactions(TransactionFilter(workspaceId = workspaceId)),
        ) { session, workspaces, members, transactions ->
            if (session == null) {
                return@combine WorkspaceSettlementResult.UserNotMember
            }

            val workspace = workspaces.firstOrNull { it.id == workspaceId }
                ?: return@combine WorkspaceSettlementResult.WorkspaceNotFound

            val isMember = members.any { it.userId == session.userId }
            if (!isMember) {
                return@combine WorkspaceSettlementResult.UserNotMember
            }

            val activeMemberIds = members.map { it.userId }.toSet()
            val targetCurrency = workspace.currency

            var hasExcludedExpenses = false
            val validExpenses = mutableListOf<WorkspaceSharedExpense>()
            var totalExpenseMinor = 0L

            for (transaction in transactions) {
                if (transaction.workspaceId != workspaceId) continue
                if (transaction.type != TransactionType.EXPENSE) continue

                val isValidAmount = transaction.amount.amountMinor > 0 && transaction.amount.currency == targetCurrency
                val isPayerActive = transaction.paidByUserId in activeMemberIds
                val areParticipantsValid = transaction.participantUserIds.isNotEmpty() &&
                    transaction.participantUserIds.distinct().size == transaction.participantUserIds.size &&
                    transaction.paidByUserId in transaction.participantUserIds &&
                    transaction.participantUserIds.all { it in activeMemberIds }

                if (isValidAmount && isPayerActive && areParticipantsValid) {
                    try {
                        val sharedExpense = WorkspaceSharedExpense(
                            id = transaction.id,
                            paidByUserId = transaction.paidByUserId,
                            amount = transaction.amount,
                            participantUserIds = transaction.participantUserIds,
                        )
                        validExpenses.add(sharedExpense)
                        totalExpenseMinor += transaction.amount.amountMinor
                    } catch (_: Throwable) {
                        hasExcludedExpenses = true
                    }
                } else {
                    hasExcludedExpenses = true
                }
            }

            val settlement = if (validExpenses.isNotEmpty()) {
                try {
                    WorkspaceSettlementCalculator.calculate(validExpenses)
                } catch (_: Throwable) {
                    hasExcludedExpenses = true
                    null
                }
            } else {
                null
            }

            WorkspaceSettlementResult.Success(
                workspace = workspace,
                members = members,
                currentUserId = session.userId,
                settlement = settlement,
                totalExpenseAmount = Money(totalExpenseMinor, targetCurrency),
                hasExcludedExpenses = hasExcludedExpenses,
            )
        }
}

package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.core.CategoryRemoteQuery
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.DebtPaymentRemoteQuery
import com.feniqo.mobile.data.remote.core.DebtRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalContributionRemoteQuery
import com.feniqo.mobile.data.remote.core.GoalRemoteQuery
import com.feniqo.mobile.data.remote.core.RecurringTransactionRemoteQuery
import com.feniqo.mobile.data.remote.core.RemotePage
import com.feniqo.mobile.data.remote.core.RemotePageRequest
import com.feniqo.mobile.data.remote.core.RemoteWorkspaceScope
import com.feniqo.mobile.data.remote.core.SubscriptionRemoteQuery
import com.feniqo.mobile.data.remote.core.TransactionRemoteQuery
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.mapper.toDomain
import com.feniqo.mobile.domain.model.EntityId
import kotlin.time.Instant

/** İlk girişte V1/V2 kişisel verisini Room'a tek ve atomik bir anlık görüntü olarak uygular. */
class InitialRemoteSync(
    private val remote: CoreRemoteDataSource,
    private val remoteSyncDao: RemoteSyncDao,
    private val nowEpochMillisProvider: () -> Long,
) {
    suspend fun pullFor(userId: EntityId): InitialSyncResult {
        val profile = requireNotNull(remote.fetchProfile(userId.value)) {
            "Geçerli oturum için uzak profil bulunamadı."
        }
        val categories = fetchAll { page ->
            remote.fetchCategories(CategoryRemoteQuery(page, workspaceScope = RemoteWorkspaceScope.Personal))
        }
        val recurringTransactions = fetchAll { page ->
            remote.fetchRecurringTransactions(RecurringTransactionRemoteQuery(page, workspaceScope = RemoteWorkspaceScope.Personal))
        }
        val subscriptions = fetchAll { page ->
            remote.fetchSubscriptions(SubscriptionRemoteQuery(page, workspaceScope = RemoteWorkspaceScope.Personal))
        }
        val goals = fetchAll { page ->
            remote.fetchGoals(GoalRemoteQuery(page, workspaceScope = RemoteWorkspaceScope.Personal))
        }
        val goalContributions = fetchAll { page ->
            remote.fetchGoalContributions(GoalContributionRemoteQuery(page))
        }
        val debts = fetchAll { page ->
            remote.fetchDebts(DebtRemoteQuery(page, workspaceScope = RemoteWorkspaceScope.Personal))
        }
        val debtPayments = fetchAll { page ->
            remote.fetchDebtPayments(DebtPaymentRemoteQuery(page))
        }

        val transactions = fetchAll { page ->
            remote.fetchTransactions(TransactionRemoteQuery(page, workspaceScope = RemoteWorkspaceScope.Personal))
        }

        // Fail-closed parent validation: Live children must have a parent in the fetched set or local DB
        val goalIds = goals.map { it.id }.toSet()
        for (contrib in goalContributions) {
            if (contrib.deletedAt == null) {
                check(goalIds.contains(contrib.goalId)) {
                    "Canlı hedef katkısının üst hedefi (${contrib.goalId}) snapshot içinde bulunamadı."
                }
            }
        }
        val debtIds = debts.map { it.id }.toSet()
        for (payment in debtPayments) {
            if (payment.deletedAt == null) {
                check(debtIds.contains(payment.debtId)) {
                    "Canlı borç ödemesinin üst borcu (${payment.debtId}) snapshot içinde bulunamadı."
                }
            }
        }

        val receivedAt = nowEpochMillisProvider()

        remoteSyncDao.applyInitialSnapshot(
            profile = profile.toDomain().toEntity(profile.toRemoteSyncMetadata(receivedAt)),
            categories = categories.map { dto ->
                dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt), slug = dto.slug)
            },
            recurringTransactions = recurringTransactions.map { dto ->
                dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt))
            },
            subscriptions = subscriptions.map { dto ->
                dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt))
            },
            goals = goals.map { dto ->
                dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt))
            },
            goalContributions = goalContributions.map { dto ->
                dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt))
            },
            debts = debts.map { dto ->
                dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt))
            },
            debtPayments = debtPayments.map { dto ->
                dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt))
            },
            transactions = transactions.map { dto ->
                dto.toDomain().toEntity(dto.toRemoteSyncMetadata(receivedAt))
            },
            cursors = listOfNotNull(
                cursorFor(PROFILE, listOf(profile.id to (profile.updatedAt ?: profile.createdAt))),
                cursorFor(CATEGORY, categories.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(RECURRING_TRANSACTION, recurringTransactions.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(SUBSCRIPTION, subscriptions.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(GOAL, goals.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(GOAL_CONTRIBUTION, goalContributions.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(DEBT, debts.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(DEBT_PAYMENT, debtPayments.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(TRANSACTION, transactions.map { it.id to (it.updatedAt ?: it.createdAt) }),
            ),
        )
        return InitialSyncResult(
            categoryCount = categories.size,
            transactionCount = transactions.size,
            recurringTransactionCount = recurringTransactions.size,
            subscriptionCount = subscriptions.size,
            goalCount = goals.size,
            goalContributionCount = goalContributions.size,
            debtCount = debts.size,
            debtPaymentCount = debtPayments.size,
        )
    }

    private suspend fun <T> fetchAll(fetch: suspend (RemotePageRequest) -> RemotePage<T>): List<T> {
        val result = mutableListOf<T>()
        var request = RemotePageRequest()
        do {
            val page = fetch(request)
            result += page.items
            request = RemotePageRequest(pageIndex = request.pageIndex + 1, pageSize = request.pageSize)
        } while (page.hasNextPage)
        return result
    }

    private fun cursorFor(entityType: String, records: List<Pair<String, String>>): SyncCursorEntity? {
        val last = records.maxWithOrNull(
            compareBy<Pair<String, String>> { Instant.parse(it.second) }.thenBy { it.first },
        ) ?: return null
        return SyncCursorEntity(
            entityTypeCode = entityType,
            updatedAtEpochMillis = Instant.parse(last.second).toEpochMilliseconds(),
            entityId = last.first,
        )
    }

    private companion object {
        const val PROFILE = "PROFILE"
        const val CATEGORY = "CATEGORY"
        const val RECURRING_TRANSACTION = "RECURRING_TRANSACTION"
        const val SUBSCRIPTION = "SUBSCRIPTION"
        const val GOAL = "GOAL"
        const val GOAL_CONTRIBUTION = "GOAL_CONTRIBUTION"
        const val DEBT = "DEBT"
        const val DEBT_PAYMENT = "DEBT_PAYMENT"
        const val TRANSACTION = "TRANSACTION"
    }
}

data class InitialSyncResult(
    val categoryCount: Int,
    val transactionCount: Int,
    val recurringTransactionCount: Int = 0,
    val subscriptionCount: Int = 0,
    val goalCount: Int = 0,
    val goalContributionCount: Int = 0,
    val debtCount: Int = 0,
    val debtPaymentCount: Int = 0,
)

package com.feniqo.mobile.data.sync

import com.feniqo.mobile.data.local.dao.AssetDao
import com.feniqo.mobile.data.local.dao.InitialMerchantVerificationCommand
import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.entity.SyncConflictEntity
import com.feniqo.mobile.data.local.entity.SyncCursorEntity
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.codec.UserMerchantVerificationPayloadCodec
import com.feniqo.mobile.data.remote.core.AssetRemoteQuery
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
import com.feniqo.mobile.data.remote.core.UserMerchantVerificationRemoteQuery
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.UserMerchantVerificationDto
import com.feniqo.mobile.data.remote.dto.toDto
import com.feniqo.mobile.data.remote.mapper.toDomain
import com.feniqo.mobile.data.util.UuidHelper
import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import com.feniqo.mobile.domain.usecase.TransactionMerchantAttributionResolver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** İlk girişte V1/V2 kişisel verisini Room'a tek ve atomik bir anlık görüntü olarak uygular. */
class InitialRemoteSync(
    private val remote: CoreRemoteDataSource,
    private val remoteSyncDao: RemoteSyncDao,
    private val nowEpochMillisProvider: () -> Long,
    private val assetDao: AssetDao? = null,
) {
    constructor(
        remote: CoreRemoteDataSource,
        remoteSyncDao: RemoteSyncDao,
        nowEpochMillisProvider: () -> Long,
    ) : this(remote, remoteSyncDao, nowEpochMillisProvider, null)

    private val snapshotJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun pullFor(
        userId: EntityId,
        syncScopeKey: String,
    ): InitialSyncResult {
        val userScopeFromId = SyncScopeKey.user(userId.value).userId
        val userScopeFromKey = SyncScopeKey.requireUserScope(syncScopeKey).userId
        require(userScopeFromId == userScopeFromKey) {
            "Mismatched userId (${userId.value}) and syncScopeKey ($syncScopeKey)"
        }
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
        val assets = if (assetDao == null) emptyList() else fetchAll { page -> remote.fetchAssets(AssetRemoteQuery(page)) }
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
        val merchantVerifications = fetchAll { page ->
            remote.fetchMerchantVerifications(
                UserMerchantVerificationRemoteQuery(page = page, updatedAfter = null)
            )
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

        // Fail-closed DTO validations before any Room write
        for (dto in merchantVerifications) {
            validateMerchantVerificationDto(dto, userId.value)
        }

        val receivedAt = nowEpochMillisProvider()

        val sortedMerchantVerifications = merchantVerifications.sortedWith(
            compareBy<UserMerchantVerificationDto> { Instant.parse(it.updatedAt!!) }.thenBy { it.id }
        )

        val merchantCommands = mutableListOf<InitialMerchantVerificationCommand>()
        for (dto in sortedMerchantVerifications) {
            val local = remoteSyncDao.getUserMerchantVerificationRow(dto.id)
            val remoteMetadata = dto.toRemoteSyncMetadata(receivedAt)
            val remoteEntity = dto.toEntity(remoteMetadata)
            val cursor = dto.merchantCursor(syncScopeKey)
            val remoteVersion = dto.version!!

            val command = if (local == null || local.sync.syncStatus == "SYNCED") {
                InitialMerchantVerificationCommand.Apply(
                    remoteEntity = remoteEntity,
                    cursor = cursor,
                )
            } else {
                val allowedStatuses = setOf("PENDING_CREATE", "PENDING_UPDATE", "PENDING_DELETE", "CONFLICT")
                require(local.sync.syncStatus in allowedStatuses) {
                    "Geçersiz yerel sync durumu: id=${dto.id}, status=${local.sync.syncStatus}"
                }
                val localBaseVersion = local.sync.baseVersion ?: 0L
                if (remoteVersion > localBaseVersion) {
                    val tailCandidates = remoteSyncDao.getActiveMerchantVerificationTailCandidates(syncScopeKey, dto.id)
                    require(tailCandidates.size == 1) {
                        "Merchant verification için tek aktif tail outbox işlemi bulunamadı: id=${dto.id}, count=${tailCandidates.size}"
                    }
                    val tailOp = tailCandidates.single()
                    val localDomain = local.toDomain()
                    val localDto = localDomain.toDto(
                        version = local.sync.version,
                        deletedAt = local.sync.deletedAtEpochMillis?.let { kotlinx.datetime.Instant.fromEpochMilliseconds(it) },
                    )
                    val conflict = SyncConflictEntity(
                        syncScopeKey = syncScopeKey,
                        entityTypeCode = USER_MERCHANT_VERIFICATION,
                        entityId = dto.id,
                        operationId = tailOp.operationId,
                        localVersion = local.sync.version,
                        remoteVersion = remoteVersion,
                        localPayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(localDto),
                        remotePayloadJson = UserMerchantVerificationPayloadCodec.encodeSnapshot(dto),
                        detectedAtEpochMillis = receivedAt,
                    )
                    InitialMerchantVerificationCommand.Conflict(
                        conflict = conflict,
                        remoteEntity = remoteEntity,
                        cursor = cursor,
                        expectedLocalEntity = local,
                    )
                } else {
                    InitialMerchantVerificationCommand.Preserve(
                        remoteEntity = remoteEntity,
                        cursor = cursor,
                    )
                }
            }
            merchantCommands += command
        }

        remoteSyncDao.applyInitialSnapshot(
            syncScopeKey = syncScopeKey,
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
            merchantVerificationCommands = merchantCommands,
            transactions = transactions.map { dto ->
                val candidateMerchant = TransactionMerchantAttributionResolver.resolve(dto.description)
                val domainTransaction = dto.toDomain().copy(merchantInfo = candidateMerchant)
                domainTransaction.toEntity(dto.toRemoteSyncMetadata(receivedAt))
            },
            cursors = listOfNotNull(
                cursorFor(syncScopeKey, PROFILE, listOf(profile.id to (profile.updatedAt ?: profile.createdAt))),
                cursorFor(syncScopeKey, CATEGORY, categories.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(syncScopeKey, RECURRING_TRANSACTION, recurringTransactions.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(syncScopeKey, SUBSCRIPTION, subscriptions.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(syncScopeKey, GOAL, goals.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(syncScopeKey, GOAL_CONTRIBUTION, goalContributions.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(syncScopeKey, DEBT, debts.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(syncScopeKey, DEBT_PAYMENT, debtPayments.map { it.id to (it.updatedAt ?: it.createdAt) }),
                cursorFor(syncScopeKey, TRANSACTION, transactions.map { it.id to (it.updatedAt ?: it.createdAt) }),
            ),
        )
        assetDao?.applyInitialSnapshot(
            syncScopeKey = syncScopeKey,
            assets = assets.map { it.toDomain().toEntity(it.toRemoteSyncMetadata(receivedAt)) },
            cursor = cursorFor(syncScopeKey, ASSET, assets.map { it.id to (it.updatedAt ?: it.createdAt) }),
        )
        return InitialSyncResult(
            categoryCount = categories.size,
            transactionCount = transactions.size,
            recurringTransactionCount = recurringTransactions.size,
            subscriptionCount = subscriptions.size,
            assetCount = assets.size,
            goalCount = goals.size,
            goalContributionCount = goalContributions.size,
            debtCount = debts.size,
            debtPaymentCount = debtPayments.size,
            merchantVerificationCount = merchantVerifications.size,
        )
    }

    private fun validateMerchantVerificationDto(dto: UserMerchantVerificationDto, actorUserId: String) {
        require(dto.userId == actorUserId) {
            "DTO userId (${dto.userId}) actor userId ($actorUserId) ile eşleşmiyor."
        }
        require(dto.workspaceId == null) {
            "DTO workspaceId null olmalıdır: ${dto.workspaceId}"
        }
        require(dto.scopeCode == "PERSONAL") {
            "DTO scopeCode PERSONAL olmalıdır: ${dto.scopeCode}"
        }
        val updatedStr = requireNotNull(dto.updatedAt) {
            "USER_MERCHANT_VERIFICATION uzak kaydı updatedAt taşımıyor: ${dto.id}"
        }
        try {
            Instant.parse(updatedStr)
        } catch (e: Exception) {
            throw IllegalArgumentException("USER_MERCHANT_VERIFICATION geçersiz updatedAt formatı: $updatedStr", e)
        }
        if (dto.deletedAt != null) {
            try {
                Instant.parse(dto.deletedAt)
            } catch (e: Exception) {
                throw IllegalArgumentException("USER_MERCHANT_VERIFICATION geçersiz deletedAt formatı: ${dto.deletedAt}", e)
            }
        }
        val version = requireNotNull(dto.version) {
            "USER_MERCHANT_VERIFICATION uzak kaydı version taşımıyor: ${dto.id}"
        }
        require(version >= 1L) {
            "USER_MERCHANT_VERIFICATION version en az 1 olmalıdır: $version"
        }
        val alias = dto.normalizedAlias
        require(alias.isNotBlank() && alias.length <= com.feniqo.mobile.domain.model.Transaction.MAX_DESCRIPTION_LENGTH) {
            "Geçersiz alias uzunluğu: $alias"
        }
        require(TurkishDescriptionNormalizer.normalize(alias) == alias) {
            "Alias normalize edilmiş (canonical) olmalıdır: $alias"
        }
        require(alias !in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            "Genel sözcükler merchant alias'ı olamaz: $alias"
        }
        require(UuidHelper.isCanonicalUuid(dto.id)) {
            "Geçersiz ID UUID: ${dto.id}"
        }
        val expectedId = UuidHelper.deterministicMerchantVerificationUuid(dto.userId, alias)
        require(dto.id == expectedId) {
            "Remote ID (${dto.id}) deterministik UUID ($expectedId) ile eşleşmiyor."
        }
        when (dto.decisionCode) {
            "ASSIGN" -> {
                require(!dto.merchantId.isNullOrBlank()) {
                    "ASSIGN kararı için merchantId boş olamaz."
                }
            }
            "BLOCK" -> {
                require(dto.merchantId == null) {
                    "BLOCK kararı için merchantId null olmalıdır: ${dto.merchantId}"
                }
            }
            else -> throw IllegalArgumentException("Geçersiz decisionCode: ${dto.decisionCode}")
        }
    }

    private fun UserMerchantVerificationDto.merchantCursor(syncScopeKey: String): SyncCursorEntity {
        val updatedStr = requireNotNull(updatedAt) {
            "USER_MERCHANT_VERIFICATION uzak kaydı updatedAt taşımıyor: $id"
        }
        val updatedEpochMillis = try {
            Instant.parse(updatedStr).toEpochMilliseconds()
        } catch (e: Exception) {
            throw IllegalArgumentException("USER_MERCHANT_VERIFICATION geçersiz updatedAt formatı: $updatedStr", e)
        }
        return SyncCursorEntity(
            syncScopeKey = syncScopeKey,
            entityTypeCode = USER_MERCHANT_VERIFICATION,
            entityId = id,
            updatedAtEpochMillis = updatedEpochMillis,
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

    private fun cursorFor(syncScopeKey: String, entityType: String, records: List<Pair<String, String>>): SyncCursorEntity? {
        val last = records.maxWithOrNull(
            compareBy<Pair<String, String>> { Instant.parse(it.second) }.thenBy { it.first },
        ) ?: return null
        return SyncCursorEntity(
            syncScopeKey = syncScopeKey,
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
        const val ASSET = "ASSET"
        const val GOAL = "GOAL"
        const val GOAL_CONTRIBUTION = "GOAL_CONTRIBUTION"
        const val DEBT = "DEBT"
        const val DEBT_PAYMENT = "DEBT_PAYMENT"
        const val TRANSACTION = "TRANSACTION"
        const val USER_MERCHANT_VERIFICATION = "USER_MERCHANT_VERIFICATION"
    }
}

data class InitialSyncResult(
    val categoryCount: Int,
    val transactionCount: Int,
    val recurringTransactionCount: Int = 0,
    val subscriptionCount: Int = 0,
    val assetCount: Int = 0,
    val goalCount: Int = 0,
    val goalContributionCount: Int = 0,
    val debtCount: Int = 0,
    val debtPaymentCount: Int = 0,
    val merchantVerificationCount: Int = 0,
)

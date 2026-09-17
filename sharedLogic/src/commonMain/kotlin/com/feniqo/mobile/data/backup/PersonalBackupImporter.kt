package com.feniqo.mobile.data.backup

import com.feniqo.mobile.data.local.dao.BackupCategoryCreateInputV1
import com.feniqo.mobile.data.local.dao.TransactionCreateInputV2
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.remote.mapper.toDto
import com.feniqo.mobile.data.repository.ActiveWorkspaceScope
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class BackupImportPlan(val categories: List<Category>, val transactions: List<Transaction>)

class BackupImportPlanner(private val idGenerator: EntityIdGenerator) {
    fun plan(backup: FeniqoBackupV1, ownerId: EntityId): BackupImportPlan {
        val categoryIds = backup.categories.associate { it.id to idGenerator.nextId() }
        val installmentIds = backup.transactions.mapNotNull { it.installment?.groupId }.distinct()
            .associateWith { idGenerator.nextId() }
        val categories = backup.categories.map {
            Category(
                id = requireNotNull(categoryIds[it.id]), ownerId = ownerId, workspaceId = null,
                name = it.name.trim(), type = TransactionType.valueOf(it.type),
                color = CategoryColor(it.color), icon = CategoryIcon(it.icon), isDefault = false,
                createdAt = Instant.parse(backup.createdAt),
            )
        }
        val transactions = backup.transactions.map {
            Transaction(
                id = idGenerator.nextId(), ownerId = ownerId, workspaceId = null,
                amount = Money(it.amountMinor, Currency.valueOf(it.currency)),
                type = TransactionType.valueOf(it.type), categoryId = requireNotNull(categoryIds[it.categoryId]),
                description = it.description, paymentMethod = PaymentMethod.valueOf(it.paymentMethod),
                transactionDate = LocalDate.parse(it.transactionDate), receiptPath = null,
                installment = it.installment?.let { installment ->
                    InstallmentInfo(installment.number, installment.total, requireNotNull(installmentIds[installment.groupId]))
                },
                createdAt = Instant.parse(it.createdAt), paidByUserId = ownerId,
                participantUserIds = listOf(ownerId),
                note = it.note,
            )
        }
        return BackupImportPlan(categories, transactions)
    }
}

sealed interface BackupImportResult {
    data class Success(val categoryCount: Int, val transactionCount: Int) : BackupImportResult
    data class Failure(val reason: String) : BackupImportResult
}

interface PersonalBackupImporter {
    suspend fun import(raw: String): BackupImportResult

    companion object {
        operator fun invoke(
            authRepository: AuthRepository,
            activeWorkspaceScope: ActiveWorkspaceScope,
            writeQueue: OfflineWriteQueue,
            planner: BackupImportPlanner,
            nowEpochMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
        ): PersonalBackupImporter = DefaultPersonalBackupImporter(
            authRepository = authRepository,
            activeWorkspaceScope = activeWorkspaceScope,
            writeQueue = writeQueue,
            planner = planner,
            nowEpochMillis = nowEpochMillis,
        )
    }
}

class DefaultPersonalBackupImporter(
    private val authRepository: AuthRepository,
    private val activeWorkspaceScope: ActiveWorkspaceScope,
    private val writeQueue: OfflineWriteQueue,
    private val planner: BackupImportPlanner,
    private val nowEpochMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : PersonalBackupImporter {
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    override suspend fun import(raw: String): BackupImportResult {
        val backup = when (val decoded = FeniqoBackupCodec.decode(raw)) {
            is BackupDecodeResult.Invalid -> return BackupImportResult.Failure(decoded.reason)
            is BackupDecodeResult.Valid -> decoded.backup
        }
        val session = authRepository.observeSession().first()
            ?: return BackupImportResult.Failure("auth_session_required")
        if (activeWorkspaceScope.current(session.userId) != null) {
            return BackupImportResult.Failure("backup_personal_scope_required")
        }
        return try {
            val plan = planner.plan(backup, session.userId)
            if (plan.categories.isEmpty() && plan.transactions.isEmpty()) return BackupImportResult.Success(0, 0)
            val sync = newSyncMetadata(nowEpochMillis())
            writeQueue.enqueuePersonalBackupV1(
                categoryInputs = plan.categories.map {
                    BackupCategoryCreateInputV1(it.toEntity(sync), json.encodeToString(it.toDto()))
                },
                transactionInputs = plan.transactions.map {
                    TransactionCreateInputV2(it.toEntity(sync), payloadJson = json.encodeToString(it.toDto()))
                },
            )
            BackupImportResult.Success(plan.categories.size, plan.transactions.size)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            BackupImportResult.Failure("backup_import_failed")
        }
    }
}

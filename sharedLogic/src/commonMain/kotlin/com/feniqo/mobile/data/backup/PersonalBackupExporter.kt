package com.feniqo.mobile.data.backup

import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.TransactionFilter
import com.feniqo.mobile.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

data class BackupScope(
    val categoryCount: Int,
    val transactionCount: Int,
)

/**
 * Kişisel çalışma alanındaki kategorileri ve işlemleri FeniqoBackupV1 formatında dışa aktarır.
 * Makbuz yolları ve kullanıcı kimlikleri dışlanır; para minor unit olarak korunur.
 */
interface PersonalBackupExporter {
    suspend fun calculateScope(): BackupScope
    suspend fun export(): String

    companion object {
        operator fun invoke(
            categoryRepository: CategoryRepository,
            transactionRepository: TransactionRepository,
            nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
        ): PersonalBackupExporter = DefaultPersonalBackupExporter(
            categoryRepository = categoryRepository,
            transactionRepository = transactionRepository,
            nowEpochMillisProvider = nowEpochMillisProvider,
        )
    }
}

/**
 * Kişisel çalışma alanındaki kategorileri ve işlemleri FeniqoBackupV1 formatında dışa aktarır.
 * Makbuz yolları ve kullanıcı kimlikleri dışlanır; para minor unit olarak korunur.
 */
class DefaultPersonalBackupExporter(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val nowEpochMillisProvider: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : PersonalBackupExporter {
    override suspend fun calculateScope(): BackupScope {
        val categories = categoryRepository.observeCategories(workspaceId = null).first()
        val transactions = transactionRepository.observeTransactions(TransactionFilter(workspaceId = null)).first()
        return BackupScope(categoryCount = categories.size, transactionCount = transactions.size)
    }

    override suspend fun export(): String {
        val categories = categoryRepository.observeCategories(workspaceId = null).first()
        val transactions = transactionRepository.observeTransactions(TransactionFilter(workspaceId = null)).first()

        val categoryIdSet = categories.map { it.id.value }.toSet()
        val validTransactions = transactions.filter { it.categoryId.value in categoryIdSet }

        val backup = FeniqoBackupV1(
            formatVersion = FENIQO_BACKUP_FORMAT_VERSION,
            createdAt = Instant.fromEpochMilliseconds(nowEpochMillisProvider()).toString(),
            scope = "PERSONAL",
            categories = categories.map {
                BackupCategoryV1(
                    id = it.id.value,
                    name = it.name,
                    type = it.type.name,
                    color = it.color.hex,
                    icon = it.icon?.key ?: "default",
                )
            },
            transactions = validTransactions.map {
                BackupTransactionV1(
                    id = it.id.value,
                    amountMinor = it.amount.amountMinor,
                    currency = it.amount.currency.name,
                    type = it.type.name,
                    categoryId = it.categoryId.value,
                    description = it.description,
                    paymentMethod = it.paymentMethod.name,
                    transactionDate = it.transactionDate.toString(),
                    createdAt = it.createdAt.toString(),
                    installment = it.installment?.let { inst ->
                        BackupInstallmentV1(
                            groupId = inst.groupId.value,
                            number = inst.number,
                            total = inst.total,
                        )
                    },
                    note = it.note,
                )
            },
        )

        return FeniqoBackupCodec.encode(backup)
    }
}

package com.feniqo.mobile.data.mapper

import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.Tag
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionTag
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import kotlinx.datetime.Instant

fun Category.toEntity(sync: SyncMetadata, slug: String? = null): CategoryEntity = CategoryEntity(
    id = id.value,
    ownerId = ownerId?.value,
    workspaceId = workspaceId?.value,
    scopeKey = scopeKey(ownerId, workspaceId),
    name = name.trim(),
    normalizedName = name.normalizeForStorage(),
    slug = slug,
    typeCode = type.name,
    colorHex = color.hex,
    iconKey = icon?.key,
    isDefault = isDefault,
    createdAtEpochMillis = createdAt.toEpochMilliseconds(),
    sync = sync,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = EntityId(id),
    ownerId = ownerId?.let(::EntityId),
    workspaceId = workspaceId?.let(::EntityId),
    name = name,
    type = TransactionType.valueOf(typeCode),
    color = CategoryColor(colorHex),
    icon = iconKey?.let(::CategoryIcon),
    isDefault = isDefault,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
)

fun Transaction.toEntity(sync: SyncMetadata): TransactionEntity = TransactionEntity(
    id = id.value,
    ownerId = ownerId.value,
    workspaceId = workspaceId?.value,
    amountMinor = amount.amountMinor,
    currencyCode = amount.currency.code,
    typeCode = type.name,
    categoryId = categoryId.value,
    description = description,
    searchText = description.orEmpty().normalizeForStorage(),
    paymentMethodCode = paymentMethod.name,
    transactionDate = transactionDate.toString(),
    receiptPath = receiptPath?.value,
    installmentNumber = installment?.number,
    totalInstallments = installment?.total,
    installmentGroupId = installment?.groupId?.value,
    createdAtEpochMillis = createdAt.toEpochMilliseconds(),
    sync = sync,
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = EntityId(id),
    ownerId = EntityId(ownerId),
    workspaceId = workspaceId?.let(::EntityId),
    amount = Money(amountMinor, Currency.valueOf(currencyCode)),
    type = TransactionType.valueOf(typeCode),
    categoryId = EntityId(categoryId),
    description = description,
    paymentMethod = PaymentMethod.valueOf(paymentMethodCode),
    transactionDate = LocalDate.parse(transactionDate),
    receiptPath = receiptPath?.let(::ReceiptPath),
    installment = installmentInfo(),
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
)

private fun TransactionEntity.installmentInfo(): InstallmentInfo? {
    val values = listOf(installmentNumber, totalInstallments, installmentGroupId)
    if (values.all { it == null }) return null
    require(values.none { it == null }) { "Yerel taksit alanları birlikte dolu veya birlikte boş olmalıdır." }
    return InstallmentInfo(
        number = requireNotNull(installmentNumber),
        total = requireNotNull(totalInstallments),
        groupId = EntityId(requireNotNull(installmentGroupId)),
    )
}

fun Budget.toEntity(sync: SyncMetadata): BudgetEntity = BudgetEntity(
    id = id.value,
    ownerId = ownerId.value,
    workspaceId = workspaceId?.value,
    scopeKey = scopeKey(ownerId, workspaceId),
    categoryId = categoryId.value,
    month = month.value,
    limitMinor = limit.amountMinor,
    currencyCode = limit.currency.code,
    createdAtEpochMillis = createdAt.toEpochMilliseconds(),
    sync = sync,
)

fun BudgetEntity.toDomain(): Budget = Budget(
    id = EntityId(id),
    ownerId = EntityId(ownerId),
    workspaceId = workspaceId?.let(::EntityId),
    categoryId = EntityId(categoryId),
    month = YearMonth(month),
    limit = Money(limitMinor, Currency.valueOf(currencyCode)),
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
)

fun Tag.toEntity(sync: SyncMetadata): TagEntity = TagEntity(
    id = id.value,
    ownerId = ownerId.value,
    workspaceId = workspaceId?.value,
    scopeKey = scopeKey(ownerId, workspaceId),
    name = name.trim(),
    normalizedName = name.normalizeForStorage(),
    createdAtEpochMillis = createdAt.toEpochMilliseconds(),
    sync = sync,
)

fun TagEntity.toDomain(): Tag = Tag(
    id = EntityId(id),
    ownerId = EntityId(ownerId),
    workspaceId = workspaceId?.let(::EntityId),
    name = name,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
)

fun TransactionTag.toEntity(
    createdAtEpochMillis: Long,
    sync: SyncMetadata,
): TransactionTagCrossRef = TransactionTagCrossRef(
    transactionId = transactionId.value,
    tagId = tagId.value,
    createdAtEpochMillis = createdAtEpochMillis,
    sync = sync,
)

fun TransactionTagCrossRef.toDomain(): TransactionTag = TransactionTag(
    transactionId = EntityId(transactionId),
    tagId = EntityId(tagId),
)

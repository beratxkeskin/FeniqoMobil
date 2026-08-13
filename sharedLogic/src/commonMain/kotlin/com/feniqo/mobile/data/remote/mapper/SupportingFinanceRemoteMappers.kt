package com.feniqo.mobile.data.remote.mapper

import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.domain.model.Budget
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.Tag
import com.feniqo.mobile.domain.model.TransactionTag
import com.feniqo.mobile.domain.model.YearMonth
import kotlin.time.Instant

fun BudgetDto.toDomain(): Budget {
    if (limitMinor <= 0) throw RemoteMappingException("budgets.limit_minor sıfırdan büyük olmalıdır.")

    return Budget(
        id = EntityId(id.requiredField("budgets.id")),
        ownerId = EntityId(userId.requiredField("budgets.user_id")),
        workspaceId = workspaceId?.requiredField("budgets.workspace_id")?.let(::EntityId),
        categoryId = EntityId(categoryId.requiredField("budgets.category_id")),
        month = YearMonth(month.trim()),
        limit = Money(limitMinor, currency.toBudgetCurrency()),
        createdAt = createdAt.toSupportingInstant("budgets.created_at"),
    )
}

fun Budget.toDto(): BudgetDto = BudgetDto(
    id = id.value,
    userId = ownerId.value,
    workspaceId = workspaceId?.value,
    categoryId = categoryId.value,
    month = month.value,
    limitMinor = limit.amountMinor,
    currency = limit.currency.code,
    createdAt = createdAt.toString(),
)

fun TagDto.toDomain(): Tag = Tag(
    id = EntityId(id.requiredField("tags.id")),
    ownerId = EntityId(userId.requiredField("tags.user_id")),
    workspaceId = workspaceId?.requiredField("tags.workspace_id")?.let(::EntityId),
    name = name.requiredField("tags.name"),
    createdAt = createdAt.toSupportingInstant("tags.created_at"),
)

fun Tag.toDto(): TagDto = TagDto(
    id = id.value,
    userId = ownerId.value,
    workspaceId = workspaceId?.value,
    name = name.trim(),
    createdAt = createdAt.toString(),
)

fun TransactionTagDto.toDomain(): TransactionTag = TransactionTag(
    transactionId = EntityId(transactionId.requiredField("transaction_tags.transaction_id")),
    tagId = EntityId(tagId.requiredField("transaction_tags.tag_id")),
)

fun TransactionTag.toDto(): TransactionTagDto = TransactionTagDto(
    transactionId = transactionId.value,
    tagId = tagId.value,
)

private fun String.toBudgetCurrency(): Currency = Currency.entries.firstOrNull {
    it.code.equals(trim(), ignoreCase = true)
} ?: throw RemoteMappingException("Desteklenmeyen budgets.currency değeri: $this")

private fun String.toSupportingInstant(field: String): Instant = try {
    Instant.parse(this)
} catch (error: IllegalArgumentException) {
    throw RemoteMappingException("$field geçerli bir UTC zaman damgası değil.", error)
}

private fun String.requiredField(field: String): String = trim().takeIf(String::isNotEmpty)
    ?: throw RemoteMappingException("$field boş olamaz.")

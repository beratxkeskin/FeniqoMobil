package com.feniqo.mobile.data.remote.mapper

import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
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
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import kotlin.time.Instant

fun CategoryDto.toDomain(): Category {
    if (isDefault && userId != null) {
        throw RemoteMappingException("Sistem kategorisinin user_id alanı boş olmalıdır.")
    }

    return Category(
        id = EntityId(id.required("categories.id")),
        ownerId = userId?.required("categories.user_id")?.let(::EntityId),
        workspaceId = workspaceId?.required("categories.workspace_id")?.let(::EntityId),
        name = name.required("categories.name"),
        type = type.toTransactionType("categories.type"),
        color = CategoryColor(color.trim()),
        icon = icon?.trim()?.takeIf(String::isNotEmpty)?.let(::CategoryIcon),
        isDefault = isDefault,
        createdAt = createdAt.toInstant("categories.created_at"),
    )
}

fun Category.toDto(): CategoryDto = CategoryDto(
    id = id.value,
    userId = ownerId?.value,
    workspaceId = workspaceId?.value,
    name = name.trim(),
    type = type.toRemoteCode(),
    color = color.hex,
    icon = icon?.key,
    isDefault = isDefault,
    createdAt = createdAt.toString(),
)

fun TransactionDto.toDomain(): Transaction {
    if (amountMinor <= 0) {
        throw RemoteMappingException("transactions.amount_minor sıfırdan büyük olmalıdır.")
    }

    return Transaction(
        id = EntityId(id.required("transactions.id")),
        ownerId = EntityId(userId.required("transactions.user_id")),
        workspaceId = workspaceId?.required("transactions.workspace_id")?.let(::EntityId),
        amount = Money(amountMinor, currency.toCurrency()),
        type = type.toTransactionType("transactions.type"),
        categoryId = EntityId(categoryId.required("transactions.category_id")),
        description = Transaction.normalizeDescription(description),
        paymentMethod = paymentMethod.toPaymentMethod(),
        transactionDate = transactionDate.toLocalDate(),
        receiptPath = receiptPath?.trim()?.takeIf(String::isNotEmpty)?.let(::ReceiptPath),
        installment = toInstallmentInfo(),
        createdAt = createdAt.toInstant("transactions.created_at"),
    )
}

fun Transaction.toDto(): TransactionDto = TransactionDto(
    id = id.value,
    userId = ownerId.value,
    workspaceId = workspaceId?.value,
    amountMinor = amount.amountMinor,
    currency = amount.currency.code,
    type = type.toRemoteCode(),
    categoryId = categoryId.value,
    description = Transaction.normalizeDescription(description),
    paymentMethod = paymentMethod.toRemoteCode(),
    transactionDate = transactionDate.toString(),
    receiptPath = receiptPath?.value,
    installmentNumber = installment?.number,
    totalInstallments = installment?.total,
    installmentGroupId = installment?.groupId?.value,
    createdAt = createdAt.toString(),
)

private fun TransactionDto.toInstallmentInfo(): InstallmentInfo? {
    val values = listOf(installmentNumber, totalInstallments, installmentGroupId)
    if (values.all { it == null }) return null
    if (values.any { it == null }) {
        throw RemoteMappingException("Transaction taksit alanları birlikte dolu veya birlikte boş olmalıdır.")
    }

    return try {
        InstallmentInfo(
            number = requireNotNull(installmentNumber),
            total = requireNotNull(totalInstallments),
            groupId = EntityId(requireNotNull(installmentGroupId).required("transactions.installment_group_id")),
        )
    } catch (error: IllegalArgumentException) {
        if (error is RemoteMappingException) throw error
        throw RemoteMappingException("Transaction taksit bilgisi geçersiz.", error)
    }
}

private fun String.toCurrency(): Currency = Currency.entries.firstOrNull {
    it.code.equals(trim(), ignoreCase = true)
} ?: throw RemoteMappingException("Desteklenmeyen transactions.currency değeri: $this")

private fun String.toTransactionType(field: String): TransactionType = when (trim().lowercase()) {
    "income" -> TransactionType.INCOME
    "expense" -> TransactionType.EXPENSE
    else -> throw RemoteMappingException("Desteklenmeyen $field değeri: $this")
}

private fun TransactionType.toRemoteCode(): String = name.lowercase()

private fun String.toPaymentMethod(): PaymentMethod = when (trim().lowercase()) {
    "cash", "nakit" -> PaymentMethod.CASH
    "credit_card", "kredi kartı" -> PaymentMethod.CREDIT_CARD
    "debit_card", "banka kartı" -> PaymentMethod.DEBIT_CARD
    "bank_transfer", "havale/eft", "havale", "eft" -> PaymentMethod.BANK_TRANSFER
    "other", "diğer" -> PaymentMethod.OTHER
    else -> throw RemoteMappingException("Desteklenmeyen transactions.payment_method değeri: $this")
}

private fun PaymentMethod.toRemoteCode(): String = when (this) {
    PaymentMethod.CASH -> "cash"
    PaymentMethod.CREDIT_CARD -> "credit_card"
    PaymentMethod.DEBIT_CARD -> "debit_card"
    PaymentMethod.BANK_TRANSFER -> "bank_transfer"
    PaymentMethod.OTHER -> "other"
}

private fun String.toLocalDate(): LocalDate = try {
    LocalDate.parse(this)
} catch (error: IllegalArgumentException) {
    throw RemoteMappingException("transactions.transaction_date geçerli bir ISO tarih değil.", error)
}

private fun String.toInstant(field: String): Instant = try {
    Instant.parse(this)
} catch (error: IllegalArgumentException) {
    throw RemoteMappingException("$field geçerli bir UTC zaman damgası değil.", error)
}

private fun String.required(field: String): String = trim().takeIf(String::isNotEmpty)
    ?: throw RemoteMappingException("$field boş olamaz.")

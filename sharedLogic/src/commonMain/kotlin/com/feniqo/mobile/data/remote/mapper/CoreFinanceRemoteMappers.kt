package com.feniqo.mobile.data.remote.mapper

import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
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
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringTransaction
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

fun RecurringTransactionDto.toDomain(): RecurringTransaction {
    if (amountMinor <= 0) {
        throw RemoteMappingException("recurring_transactions.amount_minor sıfırdan büyük olmalıdır.")
    }
    if (workspaceId != null) {
        throw RemoteMappingException("V1'de recurring_transactions.workspace_id null olmalıdır.")
    }
    if (interval <= 0) {
        throw RemoteMappingException("recurring_transactions.interval sıfırdan büyük olmalıdır.")
    }

    val parsedStartDate = startDate.toLocalDate("recurring_transactions.start_date")
    val parsedEndDate = endDate?.toLocalDate("recurring_transactions.end_date")

    if (parsedEndDate != null && parsedEndDate < parsedStartDate) {
        throw RemoteMappingException("recurring_transactions.end_date start_date öncesinde olamaz.")
    }

    val parsedLastGeneratedDate = lastGeneratedDate?.toLocalDate("recurring_transactions.last_generated_date")
    if (parsedLastGeneratedDate != null) {
        if (parsedLastGeneratedDate < parsedStartDate) {
            throw RemoteMappingException("recurring_transactions.last_generated_date start_date öncesinde olamaz.")
        }
        if (parsedEndDate != null && parsedLastGeneratedDate > parsedEndDate) {
            throw RemoteMappingException("recurring_transactions.last_generated_date end_date sonrasında olamaz.")
        }
    }

    val normalizedDesc = Transaction.normalizeDescription(description)
    if (normalizedDesc != null && normalizedDesc.length > Transaction.MAX_DESCRIPTION_LENGTH) {
        throw RemoteMappingException("recurring_transactions.description 500 karakterden uzun olamaz.")
    }

    val parsedRule = RecurrenceRule(
        frequency = frequency.toRecurrenceFrequency("recurring_transactions.frequency"),
        interval = interval,
        startDate = parsedStartDate,
        endDate = parsedEndDate,
    )

    return RecurringTransaction(
        id = EntityId(id.required("recurring_transactions.id")),
        ownerId = EntityId(userId.required("recurring_transactions.user_id")),
        workspaceId = null,
        amount = Money(amountMinor, currency.toCurrency("recurring_transactions.currency")),
        type = type.toTransactionType("recurring_transactions.type"),
        categoryId = EntityId(categoryId.required("recurring_transactions.category_id")),
        description = normalizedDesc,
        paymentMethod = paymentMethod.toPaymentMethod("recurring_transactions.payment_method"),
        rule = parsedRule,
        lastGeneratedDate = parsedLastGeneratedDate,
        isActive = isActive,
        createdAt = createdAt.toInstant("recurring_transactions.created_at"),
    )
}

fun RecurringTransaction.toDto(): RecurringTransactionDto = RecurringTransactionDto(
    id = id.value,
    userId = ownerId.value,
    workspaceId = workspaceId?.value,
    amountMinor = amount.amountMinor,
    currency = amount.currency.code,
    type = type.toRemoteCode(),
    categoryId = categoryId.value,
    description = Transaction.normalizeDescription(description),
    paymentMethod = paymentMethod.name,
    frequency = rule.frequency.name,
    interval = rule.interval,
    startDate = rule.startDate.toString(),
    endDate = rule.endDate?.toString(),
    lastGeneratedDate = lastGeneratedDate?.toString(),
    isActive = isActive,
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

private fun String.toCurrency(field: String = "transactions.currency"): Currency = Currency.entries.firstOrNull {
    it.code.equals(trim(), ignoreCase = true)
} ?: throw RemoteMappingException("Desteklenmeyen $field değeri: $this")

private fun String.toTransactionType(field: String): TransactionType = when (trim().lowercase()) {
    "income" -> TransactionType.INCOME
    "expense" -> TransactionType.EXPENSE
    else -> throw RemoteMappingException("Desteklenmeyen $field değeri: $this")
}

private fun TransactionType.toRemoteCode(): String = name.lowercase()

private fun String.toPaymentMethod(field: String = "transactions.payment_method"): PaymentMethod = when (trim().lowercase()) {
    "cash", "nakit" -> PaymentMethod.CASH
    "credit_card", "kredi kartı" -> PaymentMethod.CREDIT_CARD
    "debit_card", "banka kartı" -> PaymentMethod.DEBIT_CARD
    "bank_transfer", "havale/eft", "havale", "eft" -> PaymentMethod.BANK_TRANSFER
    "other", "diğer" -> PaymentMethod.OTHER
    else -> throw RemoteMappingException("Desteklenmeyen $field değeri: $this")
}

private fun PaymentMethod.toRemoteCode(): String = when (this) {
    PaymentMethod.CASH -> "cash"
    PaymentMethod.CREDIT_CARD -> "credit_card"
    PaymentMethod.DEBIT_CARD -> "debit_card"
    PaymentMethod.BANK_TRANSFER -> "bank_transfer"
    PaymentMethod.OTHER -> "other"
}

private fun String.toRecurrenceFrequency(field: String): RecurrenceFrequency = RecurrenceFrequency.entries.firstOrNull {
    it.name.equals(trim(), ignoreCase = true)
} ?: throw RemoteMappingException("Desteklenmeyen $field değeri: $this")

private fun String.toLocalDate(field: String = "transactions.transaction_date"): LocalDate = try {
    LocalDate.parse(this)
} catch (error: IllegalArgumentException) {
    throw RemoteMappingException("$field geçerli bir ISO tarih değil.", error)
}

private fun String.toInstant(field: String): Instant = try {
    Instant.parse(this)
} catch (error: IllegalArgumentException) {
    throw RemoteMappingException("$field geçerli bir UTC zaman damgası değil.", error)
}

private fun String.required(field: String): String = trim().takeIf(String::isNotEmpty)
    ?: throw RemoteMappingException("$field boş olamaz.")

package com.feniqo.mobile.data.remote.mapper

import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtPayment
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContribution
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.model.Subscription
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
        paidByUserId = paidByUserId?.required("transactions.paid_by_user_id")?.let(::EntityId) ?: EntityId(userId.required("transactions.user_id")),
        participantUserIds = participantUserIds.map(::EntityId).ifEmpty {
            listOf(paidByUserId?.let(::EntityId) ?: EntityId(userId.required("transactions.user_id")))
        },
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
    paidByUserId = paidByUserId.value,
    participantUserIds = participantUserIds.map(EntityId::value),
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

fun SubscriptionDto.toDomain(): Subscription {
    if (workspaceId != null) {
        throw RemoteMappingException("subscriptions.workspace_id V2 kişisel kapsamda null olmalıdır.")
    }
    if (amountMinor <= 0L) {
        throw RemoteMappingException("subscriptions.amount_minor sıfırdan büyük olmalıdır: $amountMinor")
    }
    if (interval <= 0) {
        throw RemoteMappingException("subscriptions.interval pozitif olmalıdır: $interval")
    }

    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) {
        throw RemoteMappingException("subscriptions.name boş olamaz.")
    }
    if (trimmedName.length > Subscription.MAX_NAME_LENGTH) {
        throw RemoteMappingException("subscriptions.name ${Subscription.MAX_NAME_LENGTH} karakterden uzun olamaz: ${trimmedName.length}")
    }

    val parsedStartDate = startDate.toLocalDate("subscriptions.start_date")
    val parsedEndDate = endDate?.toLocalDate("subscriptions.end_date")
    val parsedNextRenewalDate = nextRenewalDate.toLocalDate("subscriptions.next_renewal_date")

    if (parsedEndDate != null && parsedEndDate < parsedStartDate) {
        throw RemoteMappingException("subscriptions.end_date ($parsedEndDate) start_date ($parsedStartDate) öncesinde olamaz.")
    }
    if (parsedNextRenewalDate < parsedStartDate) {
        throw RemoteMappingException("subscriptions.next_renewal_date ($parsedNextRenewalDate) start_date ($parsedStartDate) öncesinde olamaz.")
    }
    if (parsedEndDate != null && parsedNextRenewalDate > parsedEndDate) {
        throw RemoteMappingException("subscriptions.next_renewal_date ($parsedNextRenewalDate) end_date ($parsedEndDate) sonrasında olamaz.")
    }

    val parsedRule = RecurrenceRule(
        frequency = frequency.toRecurrenceFrequency("subscriptions.frequency"),
        interval = interval,
        startDate = parsedStartDate,
        endDate = parsedEndDate,
    )

    return Subscription(
        id = EntityId(id.required("subscriptions.id")),
        ownerId = EntityId(userId.required("subscriptions.user_id")),
        workspaceId = null,
        name = trimmedName,
        amount = Money(amountMinor, currency.toCurrency("subscriptions.currency")),
        categoryId = categoryId?.let { EntityId(it.required("subscriptions.category_id")) },
        renewalRule = parsedRule,
        nextRenewalDate = parsedNextRenewalDate,
        isActive = isActive,
        createdAt = createdAt.toInstant("subscriptions.created_at"),
    )
}


fun Subscription.toDto(): SubscriptionDto = SubscriptionDto(
    id = id.value,
    userId = ownerId.value,
    workspaceId = workspaceId?.value,
    name = name.trim(),
    amountMinor = amount.amountMinor,
    currency = amount.currency.code,
    categoryId = categoryId?.value,
    frequency = renewalRule.frequency.name,
    interval = renewalRule.interval,
    startDate = renewalRule.startDate.toString(),
    endDate = renewalRule.endDate?.toString(),
    nextRenewalDate = nextRenewalDate.toString(),
    isActive = isActive,
    createdAt = createdAt.toString(),
)

fun GoalDto.toDomain(): Goal {
    if (workspaceId != null) {
        throw RemoteMappingException("goals.workspace_id V2 kişisel kapsamda null olmalıdır.")
    }
    if (targetAmountMinor <= 0L) {
        throw RemoteMappingException("goals.target_amount_minor sıfırdan büyük olmalıdır: $targetAmountMinor")
    }
    if (currentAmountMinor < 0L) {
        throw RemoteMappingException("goals.current_amount_minor negatif olamaz: $currentAmountMinor")
    }

    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) {
        throw RemoteMappingException("goals.name boş olamaz.")
    }
    if (trimmedName.length > Goal.MAX_NAME_LENGTH) {
        throw RemoteMappingException("goals.name ${Goal.MAX_NAME_LENGTH} karakterden uzun olamaz: ${trimmedName.length}")
    }

    val parsedTargetDate = targetDate.toLocalDate("goals.target_date")
    val parsedCurrency = currency.toCurrency("goals.currency")

    val parsedColor = try {
        CategoryColor(colorHex.trim())
    } catch (error: IllegalArgumentException) {
        throw RemoteMappingException("goals.color_hex geçersiz formatta: $colorHex", error)
    }

    val parsedIcon = iconKey?.let { rawKey ->
        val trimmedKey = rawKey.trim()
        if (trimmedKey.isEmpty()) {
            throw RemoteMappingException("goals.icon_key boş olamaz.")
        }
        try {
            CategoryIcon(trimmedKey)
        } catch (error: IllegalArgumentException) {
            throw RemoteMappingException("goals.icon_key geçersiz: $rawKey", error)
        }
    }

    return Goal(
        id = EntityId(id.required("goals.id")),
        ownerId = EntityId(userId.required("goals.user_id")),
        workspaceId = null,
        name = trimmedName,
        targetAmount = Money(targetAmountMinor, parsedCurrency),
        currentAmount = Money(currentAmountMinor, parsedCurrency),
        targetDate = parsedTargetDate,
        color = parsedColor,
        icon = parsedIcon,
        createdAt = createdAt.toInstant("goals.created_at"),
    )
}

fun Goal.toDto(): GoalDto = GoalDto(
    id = id.value,
    userId = ownerId.value,
    workspaceId = workspaceId?.value,
    name = name.trim(),
    targetAmountMinor = targetAmount.amountMinor,
    currentAmountMinor = currentAmount.amountMinor,
    currency = targetAmount.currency.code,
    targetDate = targetDate.toString(),
    colorHex = color.hex,
    iconKey = icon?.key,
    createdAt = createdAt.toString(),
)

fun GoalContributionDto.toDomain(): GoalContribution {
    if (amountMinor <= 0L) {
        throw RemoteMappingException("goal_contributions.amount_minor sıfırdan büyük olmalıdır: $amountMinor")
    }

    val parsedDirection = direction.toGoalContributionDirection("goal_contributions.direction")
    val parsedOccurredOn = occurredOn.toLocalDate("goal_contributions.occurred_on")
    val parsedCurrency = currency.toCurrency("goal_contributions.currency")

    val normalizedNote = note?.let { rawNote ->
        val trimmed = rawNote.trim()
        if (trimmed.isEmpty()) {
            throw RemoteMappingException("goal_contributions.note boş olamaz.")
        }
        if (trimmed.length > GoalContribution.MAX_NOTE_LENGTH) {
            throw RemoteMappingException("goal_contributions.note ${GoalContribution.MAX_NOTE_LENGTH} karakterden uzun olamaz: ${trimmed.length}")
        }
        trimmed
    }

    return GoalContribution(
        id = EntityId(id.required("goal_contributions.id")),
        goalId = EntityId(goalId.required("goal_contributions.goal_id")),
        amount = Money(amountMinor, parsedCurrency),
        direction = parsedDirection,
        occurredOn = parsedOccurredOn,
        note = normalizedNote,
        createdAt = createdAt.toInstant("goal_contributions.created_at"),
    )
}

fun GoalContribution.toDto(): GoalContributionDto = GoalContributionDto(
    id = id.value,
    goalId = goalId.value,
    amountMinor = amount.amountMinor,
    currency = amount.currency.code,
    direction = direction.name,
    occurredOn = occurredOn.toString(),
    note = note?.trim(),
    createdAt = createdAt.toString(),
)

fun DebtDto.toDomain(): Debt {
    if (workspaceId != null) {
        throw RemoteMappingException("debts.workspace_id V2 kişisel kapsamda null olmalıdır.")
    }
    if (amountMinor <= 0L) {
        throw RemoteMappingException("debts.amount_minor sıfırdan büyük olmalıdır: $amountMinor")
    }

    val trimmedTitle = title.trim()
    if (trimmedTitle.isEmpty()) {
        throw RemoteMappingException("debts.title boş olamaz.")
    }
    if (trimmedTitle.length > Debt.MAX_TITLE_LENGTH) {
        throw RemoteMappingException("debts.title ${Debt.MAX_TITLE_LENGTH} karakterden uzun olamaz: ${trimmedTitle.length}")
    }

    val parsedType = type.toDebtType("debts.type")
    val parsedStatus = status.toDebtStatus("debts.status")
    val parsedDueDate = dueDate.toLocalDate("debts.due_date")
    val parsedCurrency = currency.toCurrency("debts.currency")

    val normalizedDescription = description?.let { rawDesc ->
        val trimmed = rawDesc.trim()
        if (trimmed.isEmpty()) {
            throw RemoteMappingException("debts.description boş olamaz.")
        }
        if (trimmed.length > Debt.MAX_DESCRIPTION_LENGTH) {
            throw RemoteMappingException("debts.description ${Debt.MAX_DESCRIPTION_LENGTH} karakterden uzun olamaz: ${trimmed.length}")
        }
        trimmed
    }

    return Debt(
        id = EntityId(id.required("debts.id")),
        ownerId = EntityId(userId.required("debts.user_id")),
        workspaceId = null,
        title = trimmedTitle,
        amount = Money(amountMinor, parsedCurrency),
        type = parsedType,
        dueDate = parsedDueDate,
        status = parsedStatus,
        description = normalizedDescription,
        createdAt = createdAt.toInstant("debts.created_at"),
    )
}

fun Debt.toDto(): DebtDto = DebtDto(
    id = id.value,
    userId = ownerId.value,
    workspaceId = workspaceId?.value,
    title = title.trim(),
    amountMinor = amount.amountMinor,
    currency = amount.currency.code,
    type = type.name,
    dueDate = dueDate.toString(),
    status = status.name,
    description = description?.trim(),
    createdAt = createdAt.toString(),
)

fun DebtPaymentDto.toDomain(): DebtPayment {
    if (amountMinor <= 0L) {
        throw RemoteMappingException("debt_payments.amount_minor sıfırdan büyük olmalıdır: $amountMinor")
    }

    val parsedPaidOn = paidOn.toLocalDate("debt_payments.paid_on")
    val parsedCurrency = currency.toCurrency("debt_payments.currency")

    return DebtPayment(
        id = EntityId(id.required("debt_payments.id")),
        debtId = EntityId(debtId.required("debt_payments.debt_id")),
        amount = Money(amountMinor, parsedCurrency),
        paidOn = parsedPaidOn,
        createdAt = createdAt.toInstant("debt_payments.created_at"),
    )
}

fun DebtPayment.toDto(): DebtPaymentDto = DebtPaymentDto(
    id = id.value,
    debtId = debtId.value,
    amountMinor = amount.amountMinor,
    currency = amount.currency.code,
    paidOn = paidOn.toString(),
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

private fun String.toGoalContributionDirection(field: String): GoalContributionDirection = when (trim().uppercase()) {
    "ADD" -> GoalContributionDirection.ADD
    "REMOVE" -> GoalContributionDirection.REMOVE
    else -> throw RemoteMappingException("Desteklenmeyen $field değeri: $this")
}

private fun String.toDebtType(field: String): DebtType = when (trim().uppercase()) {
    "DEBT" -> DebtType.DEBT
    "RECEIVABLE" -> DebtType.RECEIVABLE
    else -> throw RemoteMappingException("Desteklenmeyen $field değeri: $this")
}

private fun String.toDebtStatus(field: String): DebtStatus = when (trim().uppercase()) {
    "OPEN" -> DebtStatus.OPEN
    "SETTLED" -> DebtStatus.SETTLED
    else -> throw RemoteMappingException("Desteklenmeyen $field değeri: $this")
}

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

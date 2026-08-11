package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

data class Goal(
    val id: EntityId,
    val ownerId: EntityId,
    val workspaceId: EntityId?,
    val name: String,
    val targetAmount: Money,
    val currentAmount: Money,
    val targetDate: LocalDate,
    val color: CategoryColor,
    val icon: CategoryIcon?,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Hedef adı boş olamaz." }
        require(targetAmount.amountMinor > 0) { "Hedef tutarı sıfırdan büyük olmalıdır." }
        require(targetAmount.currency == currentAmount.currency) {
            "Hedef ve birikmiş tutar aynı para biriminde olmalıdır."
        }
    }
}

enum class DebtType {
    DEBT,
    RECEIVABLE,
}

enum class DebtStatus {
    OPEN,
    SETTLED,
}

data class Debt(
    val id: EntityId,
    val ownerId: EntityId,
    val workspaceId: EntityId?,
    val title: String,
    val amount: Money,
    val type: DebtType,
    val dueDate: LocalDate,
    val status: DebtStatus,
    val description: String?,
    val createdAt: Instant,
) {
    init {
        require(title.isNotBlank()) { "Borç/alacak başlığı boş olamaz." }
        require(amount.amountMinor > 0) { "Borç/alacak tutarı sıfırdan büyük olmalıdır." }
        require(description == null || description.isNotBlank()) { "Borç açıklaması boş olamaz." }
    }
}

/** Borç ödeme geçmişini ana borç kaydından ayrı tutan ilişki modeli. */
data class DebtPayment(
    val id: EntityId,
    val debtId: EntityId,
    val amount: Money,
    val paidOn: LocalDate,
    val createdAt: Instant,
) {
    init {
        require(amount.amountMinor > 0) { "Borç ödeme tutarı sıfırdan büyük olmalıdır." }
    }
}

/** Abonelik aynı tekrar kuralını kullanır; silinmiş kategori nedeniyle categoryId nullable'dır. */
data class Subscription(
    val id: EntityId,
    val ownerId: EntityId,
    val workspaceId: EntityId?,
    val name: String,
    val amount: Money,
    val categoryId: EntityId?,
    val renewalRule: RecurrenceRule,
    val nextRenewalDate: LocalDate,
    val isActive: Boolean,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Abonelik adı boş olamaz." }
        require(amount.amountMinor > 0) { "Abonelik tutarı sıfırdan büyük olmalıdır." }
        require(nextRenewalDate >= renewalRule.startDate) {
            "Sonraki yenileme tarihi abonelik başlangıcından önce olamaz."
        }
    }
}

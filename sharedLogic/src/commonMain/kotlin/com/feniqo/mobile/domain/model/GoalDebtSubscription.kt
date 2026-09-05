package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

enum class GoalStatus {
    IN_PROGRESS,
    ACHIEVED,
}

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
        require(name.trim().length <= MAX_NAME_LENGTH) {
            "Hedef adı en fazla $MAX_NAME_LENGTH karakter olabilir: ${name.length}"
        }
        require(targetAmount.amountMinor > 0) { "Hedef tutarı sıfırdan büyük olmalıdır." }
        require(currentAmount.amountMinor >= 0) { "Mevcut birikim tutarı negatif olamaz." }
        require(targetAmount.currency == currentAmount.currency) {
            "Hedef ve birikmiş tutar aynı para biriminde olmalıdır."
        }
    }

    companion object {
        const val MAX_NAME_LENGTH: Int = 500
    }
}

enum class GoalContributionDirection {
    ADD,
    REMOVE,
}

data class GoalContribution(
    val id: EntityId,
    val goalId: EntityId,
    val amount: Money,
    val direction: GoalContributionDirection,
    val occurredOn: LocalDate,
    val note: String?,
    val createdAt: Instant,
) {
    init {
        require(amount.amountMinor > 0) { "Katkı tutarı sıfırdan büyük olmalıdır." }
        require(note == null || (note.isNotBlank() && note.trim().length <= MAX_NOTE_LENGTH)) {
            "Katkı notu boş olamaz ve en fazla $MAX_NOTE_LENGTH karakter olabilir."
        }
    }

    companion object {
        const val MAX_NOTE_LENGTH: Int = 500
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
        require(title.trim().length <= MAX_TITLE_LENGTH) {
            "Borç/alacak başlığı en fazla $MAX_TITLE_LENGTH karakter olabilir: ${title.length}"
        }
        require(amount.amountMinor > 0) { "Borç/alacak tutarı sıfırdan büyük olmalıdır." }
        require(description == null || (description.isNotBlank() && description.trim().length <= MAX_DESCRIPTION_LENGTH)) {
            "Borç açıklaması boş olamaz ve en fazla $MAX_DESCRIPTION_LENGTH karakter olabilir."
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH: Int = 500
        const val MAX_DESCRIPTION_LENGTH: Int = 500
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
        require(name.trim().length <= MAX_NAME_LENGTH) {
            "Abonelik adı en fazla $MAX_NAME_LENGTH karakter olabilir: ${name.length}"
        }
        require(amount.amountMinor > 0) { "Abonelik tutarı sıfırdan büyük olmalıdır." }
        require(nextRenewalDate >= renewalRule.startDate) {
            "Sonraki yenileme tarihi abonelik başlangıcından önce olamaz."
        }
    }

    companion object {
        const val MAX_NAME_LENGTH: Int = 500
    }
}

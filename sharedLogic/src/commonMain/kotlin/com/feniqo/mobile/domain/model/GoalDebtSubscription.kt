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
    val isActive: Boolean = true,
    val createdAt: Instant,
    val lifecycleStatus: SubscriptionLifecycleStatus = if (isActive) SubscriptionLifecycleStatus.ACTIVE else SubscriptionLifecycleStatus.PAUSED,
    val trialEndDate: LocalDate? = null,
    val cancellationDate: LocalDate? = null,
    val accessEndDate: LocalDate? = null,
    val reminderEnabled: Boolean = true,
    val websiteUrl: String? = null,
    val notes: String? = null,
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
        if (lifecycleStatus == SubscriptionLifecycleStatus.TRIAL) {
            requireNotNull(trialEndDate) { "Deneme süresi (TRIAL) durumunda deneme bitiş tarihi zorunludur." }
        }
        if (trialEndDate != null) {
            require(trialEndDate >= renewalRule.startDate) {
                "Deneme bitiş tarihi abonelik başlangıç tarihinden önce olamaz."
            }
        }
        if (lifecycleStatus == SubscriptionLifecycleStatus.CANCELLED) {
            requireNotNull(cancellationDate) { "İptal (CANCELLED) durumunda iptal tarihi zorunludur." }
        }
        if (accessEndDate != null && cancellationDate != null) {
            require(accessEndDate >= cancellationDate) {
                "Erişim bitiş tarihi iptal tarihinden önce olamaz."
            }
        }
        require(websiteUrl == null || websiteUrl.trim().length <= MAX_WEBSITE_URL_LENGTH) {
            "Web sitesi URL'si en fazla $MAX_WEBSITE_URL_LENGTH karakter olabilir."
        }
        require(notes == null || notes.trim().length <= MAX_NOTES_LENGTH) {
            "Notlar en fazla $MAX_NOTES_LENGTH karakter olabilir."
        }
    }

    companion object {
        const val MAX_NAME_LENGTH: Int = 500
        const val MAX_WEBSITE_URL_LENGTH: Int = 500
        const val MAX_NOTES_LENGTH: Int = 1000
    }
}

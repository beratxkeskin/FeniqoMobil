package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

/** Tekrarlama motorunun platformdan bağımsız tarih sözleşmesi. */
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate?,
) {
    init {
        require(interval > 0) { "Tekrar aralığı sıfırdan büyük olmalıdır." }
        require(endDate == null || endDate >= startDate) {
            "Tekrar bitiş tarihi başlangıç tarihinden önce olamaz."
        }
    }
}

/** WorkManager yalnız bu kuralı çalıştırır; tekrar üretme kararı ortak domain'de kalır. */
data class RecurringTransaction(
    val id: EntityId,
    val ownerId: EntityId,
    val workspaceId: EntityId?,
    val amount: Money,
    val type: TransactionType,
    val categoryId: EntityId,
    val description: String?,
    val paymentMethod: PaymentMethod,
    val rule: RecurrenceRule,
    val lastGeneratedDate: LocalDate?,
    val isActive: Boolean,
    val createdAt: Instant,
) {
    init {
        require(amount.amountMinor > 0) { "Tekrarlayan işlem tutarı sıfırdan büyük olmalıdır." }
        require(description == null || (description.isNotBlank() && description.length <= Transaction.MAX_DESCRIPTION_LENGTH)) {
            "Tekrarlayan işlem açıklaması boş olamaz ve 500 karakteri geçemez."
        }
        require(lastGeneratedDate == null || lastGeneratedDate >= rule.startDate) {
            "Son üretim tarihi tekrar başlangıcından önce olamaz."
        }
    }
}

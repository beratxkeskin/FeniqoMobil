package com.feniqo.mobile.domain.model

/**
 * Yeni tekrarlayan işlem kuralı oluşturma komutu.
 * UI'dan yalnızca kullanıcı tarafından belirlenen değerleri taşır;
 * oturum (ownerId), workspaceId, createdAt veya senkronizasyon metadata'sı repository katmanında atanır.
 */
data class CreateRecurringTransactionCommand(
    val amount: Money,
    val type: TransactionType,
    val categoryId: EntityId,
    val description: String?,
    val paymentMethod: PaymentMethod,
    val rule: RecurrenceRule,
)

/**
 * Mevcut tekrarlayan işlem kuralını güncelleme komutu.
 * Yalnızca kullanıcı tarafından değiştirilebilir alanları taşır.
 */
data class UpdateRecurringTransactionCommand(
    val id: EntityId,
    val amount: Money,
    val type: TransactionType,
    val categoryId: EntityId,
    val description: String?,
    val paymentMethod: PaymentMethod,
    val rule: RecurrenceRule,
)

/**
 * Tekrarlayan işlem kuralını duraklatma veya devam ettirme komutu.
 */
data class SetRecurringTransactionActiveCommand(
    val id: EntityId,
    val isActive: Boolean,
)

package com.feniqo.mobile.domain.model

/**
 * Yeni abonelik kuralı oluşturma komutu.
 * UI'dan yalnızca kullanıcı tarafından belirlenen değerleri taşır;
 * oturum (ownerId), workspaceId, createdAt veya senkronizasyon metadata'sı repository katmanında atanır.
 */
data class CreateSubscriptionCommand(
    val name: String,
    val amount: Money,
    val categoryId: EntityId?,
    val renewalRule: RecurrenceRule,
    val nextRenewalDate: LocalDate,
    val lifecycleStatus: SubscriptionLifecycleStatus = SubscriptionLifecycleStatus.ACTIVE,
    val trialEndDate: LocalDate? = null,
    val reminderEnabled: Boolean = true,
    val websiteUrl: String? = null,
    val notes: String? = null,
)

/**
 * Mevcut abonelik kuralını güncelleme komutu.
 * Yalnızca kullanıcı tarafından değiştirilebilir alanları taşır.
 */
data class UpdateSubscriptionCommand(
    val id: EntityId,
    val name: String,
    val amount: Money,
    val categoryId: EntityId?,
    val renewalRule: RecurrenceRule,
    val lifecycleStatus: SubscriptionLifecycleStatus = SubscriptionLifecycleStatus.ACTIVE,
    val trialEndDate: LocalDate? = null,
    val cancellationDate: LocalDate? = null,
    val accessEndDate: LocalDate? = null,
    val reminderEnabled: Boolean = true,
    val websiteUrl: String? = null,
    val notes: String? = null,
)

/**
 * Abonelik kuralını duraklatma veya devam ettirme komutu.
 */
data class SetSubscriptionActiveCommand(
    val id: EntityId,
    val isActive: Boolean,
)

/**
 * Abonelik yaşam döngüsü durumunu doğrudan değiştirme komutu.
 */
data class SetSubscriptionLifecycleCommand(
    val id: EntityId,
    val status: SubscriptionLifecycleStatus,
    val cancellationDate: LocalDate? = null,
    val accessEndDate: LocalDate? = null,
)

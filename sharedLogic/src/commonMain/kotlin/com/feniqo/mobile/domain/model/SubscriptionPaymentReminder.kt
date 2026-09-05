package com.feniqo.mobile.domain.model

/**
 * Abonelik ödeme hatırlatıcı türü.
 */
enum class SubscriptionReminderKind {
    /** Vadeye tam 7 gün (veya belirlenen pencere günü) kala gönderilen hatırlatıcı. */
    UPCOMING,
    /** Vade günü gönderilen hatırlatıcı. */
    DUE_TODAY,
}

/**
 * Bir abonelik hatırlatıcısının deterministik ve kalıcı idempotency anahtarı.
 */
data class SubscriptionPaymentReminderKey(
    val subscriptionId: EntityId,
    val nextRenewalDate: LocalDate,
    val reminderKind: SubscriptionReminderKind,
) {
    /**
     * Kalıcı depolama ve WorkManager/Notification idempotency için kararlı metin anahtarı.
     */
    val stableKey: String
        get() = "${subscriptionId.value}_${nextRenewalDate}_${reminderKind.name}"
}

/**
 * Bildirim gönderilmeye aday saf abonelik ödeme hatırlatıcısı.
 */
data class SubscriptionPaymentReminderCandidate(
    val subscriptionId: EntityId,
    val subscriptionName: String,
    val nextRenewalDate: LocalDate,
    val reminderKind: SubscriptionReminderKind,
    val key: SubscriptionPaymentReminderKey,
) {
    val stableKey: String
        get() = key.stableKey

    init {
        require(subscriptionId == key.subscriptionId) {
            "subscriptionId ($subscriptionId) ile key.subscriptionId (${key.subscriptionId}) eşleşmelidir."
        }
        require(nextRenewalDate == key.nextRenewalDate) {
            "nextRenewalDate ($nextRenewalDate) ile key.nextRenewalDate (${key.nextRenewalDate}) eşleşmelidir."
        }
        require(reminderKind == key.reminderKind) {
            "reminderKind ($reminderKind) ile key.reminderKind (${key.reminderKind}) eşleşmelidir."
        }
    }
}

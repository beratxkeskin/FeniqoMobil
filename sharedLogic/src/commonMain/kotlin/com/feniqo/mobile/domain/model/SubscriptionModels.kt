package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

/**
 * Bir aboneliğin ürün yaşam döngüsü durumu.
 */
enum class SubscriptionLifecycleStatus {
    /** Aktif ve yenileme üreten abonelik. */
    ACTIVE,
    /** Kullanıcı tarafından geçici olarak duraklatılmış abonelik. */
    PAUSED,
    /** Kullanıcı tarafından iptal edilmiş abonelik. */
    CANCELLED,
    /** Ücretsiz veya indirimli deneme süresindeki abonelik. */
    TRIAL,
    /** Bitiş tarihine ulaşmış veya yenilenmeyen süresi dolmuş abonelik. */
    EXPIRED,
}

/**
 * Bir aboneliğin tarihsel fiyat değişiklik kaydı.
 */
data class SubscriptionPriceHistory(
    val id: EntityId,
    val subscriptionId: EntityId,
    val oldAmount: Money,
    val newAmount: Money,
    val changedAt: Instant,
) {
    init {
        require(oldAmount.amountMinor > 0L) { "Eski tutar sıfırdan büyük olmalıdır: ${oldAmount.amountMinor}" }
        require(newAmount.amountMinor > 0L) { "Yeni tutar sıfırdan büyük olmalıdır: ${newAmount.amountMinor}" }
        require(oldAmount.currency == newAmount.currency) {
            "Fiyat geçmişi aynı para biriminde olmalıdır: ${oldAmount.currency} != ${newAmount.currency}"
        }
    }

    /** Fiyat artışı varsa pozitif fark, aksi takdirde 0L. */
    val increaseAmountMinor: Long
        get() = (newAmount.amountMinor - oldAmount.amountMinor).coerceAtLeast(0L)

    /** Fiyatın bir önceki tutara göre artıp artmadığı. */
    val isPriceIncreased: Boolean
        get() = newAmount.amountMinor > oldAmount.amountMinor

    /**
     * Fiyat artış oranı basis-point cinsinden (1 bp = %0.01 = 1/10000).
     * Artış yoksa veya taşma riski varsa null döner.
     */
    val increaseBasisPoints: Long?
        get() {
            if (!isPriceIncreased || oldAmount.amountMinor <= 0L) return null
            return try {
                // (diff * 10_000) / oldAmount
                val diff = newAmount.amountMinor - oldAmount.amountMinor
                if (diff <= 0L) return null
                val scaled = safeMultiply(diff, 10_000L) ?: return null
                scaled / oldAmount.amountMinor
            } catch (_: Exception) {
                null
            }
        }

    private companion object {
        fun safeMultiply(a: Long, b: Long): Long? {
            if (a == 0L || b == 0L) return 0L
            val result = a * b
            return if (a != 0L && result / a != b) null else result
        }
    }
}

/**
 * Abonelik ödeme olayının kaynağı.
 */
enum class SubscriptionPaymentSourceType {
    /** Kullanıcı tarafından "Ödendi olarak işaretle" butonuyla tetiklenen ödeme. */
    MANUAL,
    /** Arka plan veya otomatik kural tarafından üretilen ödeme. */
    AUTOMATIC,
}

/**
 * Gerçekleşen tekil bir abonelik ödeme olayı.
 * Bir abonelik ve renewalDueDate için en fazla tek bir kayıt üretilir (idempotent).
 */
data class SubscriptionPayment(
    val id: EntityId,
    val subscriptionId: EntityId,
    val amount: Money,
    val paymentDate: LocalDate,
    val renewalDueDate: LocalDate,
    val sourceType: SubscriptionPaymentSourceType,
    val createdAt: Instant,
) {
    init {
        require(amount.amountMinor > 0L) { "Ödeme tutarı sıfırdan büyük olmalıdır: ${amount.amountMinor}" }
    }
}

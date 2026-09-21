package com.feniqo.mobile.domain.model

/**
 * E-posta doğrulamasının gerçek sunucu durumunu ifade eder.
 * Durum bilinmediğinde veya oturum yokken asla varsayılan olarak doğrulanmış kabul edilmez.
 */
enum class EmailVerificationStatus {
    VERIFIED,             // Doğrulandı
    PENDING_VERIFICATION, // Doğrulama bekleniyor
    UNKNOWN,              // Durum alınamadı
}

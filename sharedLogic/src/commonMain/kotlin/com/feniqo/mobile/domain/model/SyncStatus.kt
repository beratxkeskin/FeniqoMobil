package com.feniqo.mobile.domain.model

/** Yerel kayıt ile outbox/sunucu arasındaki senkronizasyon durumunu açıkça ifade eder. */
enum class SyncStatus {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    CONFLICT,
    FAILED,
}

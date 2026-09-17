package com.feniqo.mobile.domain.model

/**
 * Makbuz için izin verilen güvenli MIME türleri.
 * İlk dilimde (C1) yalnızca JPEG ve PNG desteklenir.
 */
enum class ReceiptMimeType(val rawValue: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png");

    companion object {
        fun fromMimeOrNull(value: String): ReceiptMimeType? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.rawValue == normalized }
        }
    }
}

/**
 * Makbuz dosyasının yerel ve uzak hazırlık/yükleme durumu.
 */
enum class ReceiptUploadStatus {
    LOCAL_READY,
    UPLOADING,
    UPLOADED,
    FAILED,
}

/**
 * Karar anında doğrulanan aktif oturum snapshot'ı.
 */
data class CurrentSessionSnapshot(
    val userId: EntityId,
    val sessionEpoch: Long,
) {
    init {
        require(sessionEpoch >= 1L) { "Session epoch 1 veya daha büyük olmalıdır." }
    }
}

/**
 * Değişmez fiziksel makbuz dosyasının tanımlayıcısı ve doğruluk metadata'sı.
 * Yerel dosya yolu veya Android URI'si içermez; saf domain nesnesidir.
 */
data class ReceiptFileDescriptor(
    val attachmentId: EntityId,
    val ownerId: EntityId,
    val contentSha256: String,
    val fileSizeBytes: Long,
    val mimeType: ReceiptMimeType,
    val remotePath: ReceiptPath,
    val uploadStatus: ReceiptUploadStatus = ReceiptUploadStatus.LOCAL_READY,
    /**
     * Belirli bir anda uzak nesnenin varlığının doğrulanmış olup olmadığı.
     * Kalıcı bir garanti değildir; UPLOADED durumuyla doğrudan aynı boolean sayılmaz.
     */
    val verifiedRemoteExists: Boolean = false,
) {
    init {
        require(fileSizeBytes in 1..MAX_RECEIPT_BYTES) {
            "Makbuz boyutu 1 ile $MAX_RECEIPT_BYTES byte arasında olmalıdır."
        }
        require(SHA256_REGEX.matches(contentSha256)) {
            "Geçersiz SHA-256 içerik özeti."
        }
    }

    companion object {
        const val MAX_RECEIPT_BYTES: Long = 6 * 1024 * 1024L // 6.291.456 byte
        private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")
    }
}

/**
 * Bir finansal işlemin makbuz bağlantısı ve kullanıcının aktif niyeti.
 * Birden fazla taksit aynı [activeAttachmentId]'ye bağlanabilir.
 * Her işlemde en fazla bir aktif istenen bağlantı olabilir.
 */
data class TransactionReceiptLinkage(
    val transactionId: EntityId,
    val ownerId: EntityId,
    val activeAttachmentId: EntityId?,
    val generation: Long,
    val sessionEpoch: Long,
    val workspaceId: EntityId? = null,
) {
    init {
        require(generation >= 1L) { "Revizyon/generation 1 veya daha büyük olmalıdır." }
        require(sessionEpoch >= 1L) { "Session epoch 1 veya daha büyük olmalıdır." }
    }

    /**
     * Kullanıcı yeni bir makbuz seçtiğinde monoton artan yeni bir linkage üretir.
     */
    fun withNewAttachment(newAttachmentId: EntityId): TransactionReceiptLinkage {
        check(generation < Long.MAX_VALUE) { "Generation taşması engellendi." }
        return copy(
            activeAttachmentId = newAttachmentId,
            generation = generation + 1L,
        )
    }

    /**
     * Kullanıcı makbuzu kaldırdığında aktif bağlantıyı null yapar ve generation'ı artırır.
     */
    fun withRemovedAttachment(): TransactionReceiptLinkage {
        check(generation < Long.MAX_VALUE) { "Generation taşması engellendi." }
        return copy(
            activeAttachmentId = null,
            generation = generation + 1L,
        )
    }
}

/**
 * Tamamlanan bir upload işleminin raporladığı sonuç metadata'sı.
 */
data class ReceiptUploadOutcome(
    val attachmentId: EntityId,
    val ownerId: EntityId,
    val transactionId: EntityId,
    val generation: Long,
    val sessionEpoch: Long,
    val remotePath: ReceiptPath,
    val contentSha256: String,
    val fileSizeBytes: Long,
)

/**
 * Karar anındaki hedef işlemin doğrulanmış varlık, kimlik ve sahiplik durumu.
 */
data class TransactionValidationState(
    val transactionId: EntityId,
    val exists: Boolean,
    val isSoftDeleted: Boolean,
    val ownerId: EntityId,
    val workspaceId: EntityId?,
)

/**
 * Koşullu makbuz bağlama kararının saf sonuçları.
 */
sealed interface ReceiptBindingDecision {
    /**
     * Sonuç uygulanabilir. Sonraki Room diliminde bu karar doğrultusunda
     * receiptPath mutation'ı ve outbox kaydı AYNI Room transaction'ında yazılacaktır.
     */
    data class Applicable(
        val transactionId: EntityId,
        val attachmentId: EntityId,
        val remotePath: ReceiptPath,
    ) : ReceiptBindingDecision

    /**
     * Kullanıcı bu sırada makbuzu değiştirmiş veya kaldırmıştır; eski işin sonucu yok sayılır.
     */
    data class StaleGeneration(
        val expectedGeneration: Long,
        val incomingGeneration: Long,
    ) : ReceiptBindingDecision

    /**
     * Oturum yok, oturum değişmiş, kullanıcı çıkış yapmış veya farklı hesapla oturum açılmıştır.
     */
    data object SessionMismatch : ReceiptBindingDecision

    /**
     * İşlem hedefi uyuşmazlığı: linkage, outcome veya transactionState farklı işlemleri işaret ediyor.
     */
    data class TransactionMismatch(val reason: String) : ReceiptBindingDecision

    /**
     * İşlem bulunamadı veya silinmiştir.
     */
    data class TransactionUnavailable(val reason: String) : ReceiptBindingDecision

    /**
     * Ortak çalışma alanı makbuz eki ilk dilimde (C1) desteklenmemektedir.
     */
    data object UnsupportedScope : ReceiptBindingDecision

    /**
     * Yüklenen dosyanın kimliği, sahibi, hash'i veya boyutu beklenen dosya ile uyuşmuyor.
     */
    data class IntegrityMismatch(val reason: String) : ReceiptBindingDecision
}

/**
 * Belirsiz upload (ağ koptu, tekrar denendi ve sunucuda dosya zaten var) uzlaştırma sonucu.
 */
sealed interface ReconcileIndeterminateUploadDecision {
    /**
     * Sunucudaki nesne ile yerel dosyanın yolu, hash'i ve boyutu birebir eşleşti; dosya güvenle doğrulanmış sayılır.
     */
    data class Match(val verifiedRemotePath: ReceiptPath) : ReconcileIndeterminateUploadDecision

    /**
     * Sunucudaki dosya ile yerel dosyanın yolu, hash'i veya boyutu uyuşmuyor.
     * Sessizce üzerine yazılmaz; açık çatışma/bütünlük hatası bildirilir.
     */
    data class Conflict(val reason: String) : ReconcileIndeterminateUploadDecision
}

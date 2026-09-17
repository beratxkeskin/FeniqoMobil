package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.CurrentSessionSnapshot
import com.feniqo.mobile.domain.model.ReceiptBindingDecision
import com.feniqo.mobile.domain.model.ReceiptFileDescriptor
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.ReceiptUploadOutcome
import com.feniqo.mobile.domain.model.ReconcileIndeterminateUploadDecision
import com.feniqo.mobile.domain.model.TransactionReceiptLinkage
import com.feniqo.mobile.domain.model.TransactionValidationState

/**
 * Makbuz dosyası yükleme sonucunun finansal işleme bağlanabilirliğini değerlendiren
 * saf karar motoru.
 *
 * DİKKAT / MİMARİ NOT:
 * Bu saf karar fonksiyonu, bellek içi ön kontrolleri ve kuralları doğrular;
 * tek başına çok iş parçacıklı veya süreçler arası yarış güvenliği SAĞLAMAZ.
 * Ayrıca güncel oturum snapshot'ı kontrolü, DB/Auth koordinasyonu yerine geçmez.
 * Sonraki Room altyapı diliminde (C2), bu önkoşulların veritabanı düzeyinde kontrolü,
 * `TransactionEntity.receipt_path` mutation'ı ve outbox kaydının yazılması
 * AYNI Room transaction'ı (tek atomik sınır) içinde gerçekleştirilmelidir.
 * "Önce kontrol et, sonra bağımsız repository.update çağır" şeklinde iki adımlı
 * yarışa açık çağrılar kesinlikle kullanılmamalıdır.
 */
object ReceiptBindingDecisionEngine {

    /**
     * Upload sonucunu kullanıcının güncel niyeti, aktif oturum durumu, beklenen dosya kimliği
     * ve işlem varlığıyla değerlendirir.
     */
    fun evaluateBindingDecision(
        linkage: TransactionReceiptLinkage,
        outcome: ReceiptUploadOutcome,
        transactionState: TransactionValidationState,
        expectedFile: ReceiptFileDescriptor,
        currentSession: CurrentSessionSnapshot?,
    ): ReceiptBindingDecision {
        // 1. Güncel oturum kontrolü: Aktif bir oturum var mı?
        if (currentSession == null) {
            return ReceiptBindingDecision.SessionMismatch
        }

        // 2. Kullanıcı kimliği ve oturum nesli (Session Epoch) doğrulaması:
        // Tüm bileşenlerin (güncel oturum, beklenen dosya, kullanıcı niyeti, upload sonucu, işlem sahibi)
        // aynı kullanıcıya ve aynı aktif oturum nesline ait olması zorunludur.
        val expectedUserId = currentSession.userId
        if (linkage.ownerId != expectedUserId ||
            outcome.ownerId != expectedUserId ||
            transactionState.ownerId != expectedUserId ||
            expectedFile.ownerId != expectedUserId
        ) {
            return ReceiptBindingDecision.SessionMismatch
        }

        val expectedSessionEpoch = currentSession.sessionEpoch
        if (linkage.sessionEpoch != expectedSessionEpoch ||
            outcome.sessionEpoch != expectedSessionEpoch
        ) {
            return ReceiptBindingDecision.SessionMismatch
        }

        // 3. Hedef işlem kimliği doğrulaması:
        // Linkage, outcome ve transactionState kesinlikle aynı hedef işlemi işaret etmelidir.
        if (linkage.transactionId != outcome.transactionId ||
            linkage.transactionId != transactionState.transactionId
        ) {
            return ReceiptBindingDecision.TransactionMismatch("target_transaction_mismatch")
        }

        // 4. İşlem varlığı ve silinme durumu kontrolü
        if (!transactionState.exists) {
            return ReceiptBindingDecision.TransactionUnavailable("transaction_not_found")
        }
        if (transactionState.isSoftDeleted) {
            return ReceiptBindingDecision.TransactionUnavailable("transaction_soft_deleted")
        }

        // 5. Kapsam kontrolü: İlk dilimde (C1) yalnızca kişisel işlemler desteklenir
        if (transactionState.workspaceId != null || linkage.workspaceId != null) {
            return ReceiptBindingDecision.UnsupportedScope
        }

        // 6. Dosya kimliği doğrulaması:
        // Beklenen dosya, upload sonucu ve kullanıcının aktif istediği ek aynı attachmentId olmalıdır.
        if (expectedFile.attachmentId != outcome.attachmentId) {
            return ReceiptBindingDecision.IntegrityMismatch("attachment_id_mismatch")
        }

        // 7. Kullanıcı niyeti ve revizyon (Generation) kontrolü:
        // Kullanıcı bu süreçte makbuzu kaldırmışsa veya başka bir dosya seçmişse eski sonuç ezemez!
        if (linkage.generation != outcome.generation) {
            return ReceiptBindingDecision.StaleGeneration(
                expectedGeneration = linkage.generation,
                incomingGeneration = outcome.generation,
            )
        }
        if (linkage.activeAttachmentId != outcome.attachmentId) {
            return ReceiptBindingDecision.StaleGeneration(
                expectedGeneration = linkage.generation,
                incomingGeneration = outcome.generation,
            )
        }

        // 8. Dosya içerik, boyut ve uzak yol bütünlüğü kontrolü (Hash, Boyut, Yol)
        if (outcome.fileSizeBytes != expectedFile.fileSizeBytes) {
            return ReceiptBindingDecision.IntegrityMismatch("file_size_mismatch")
        }
        if (!outcome.contentSha256.equals(expectedFile.contentSha256, ignoreCase = true)) {
            return ReceiptBindingDecision.IntegrityMismatch("sha256_hash_mismatch")
        }
        if (outcome.remotePath != expectedFile.remotePath) {
            return ReceiptBindingDecision.IntegrityMismatch("remote_path_mismatch")
        }

        // Tüm koşullar sağlandı: Sonuç uygulanabilir.
        return ReceiptBindingDecision.Applicable(
            transactionId = linkage.transactionId,
            attachmentId = outcome.attachmentId,
            remotePath = outcome.remotePath,
        )
    }

    /**
     * Ağ kesintisi veya mükerrer yükleme sonucu sunucuda aynı yolda dosya zaten varsa
     * (400/409 veya ALREADY_EXISTS durumu) dosya bütünlüğünü uzlaştırır.
     *
     * Kurallar:
     * - Doğrulanan uzak nesnenin yolu, beklenen dosya yoluyla birebir eşleşmelidir. Farklı yoldaki nesne Match sayılamaz.
     * - Aynı yoldaki sunucu dosyası yereldeki ile aynı hash ve boyuta sahipse MATCH döner.
     * - Uyuşmazlık varsa CONFLICT döner; asla üzerine yazılmaz (overwrite edilmez) veya sessizce geçiştirilmez.
     */
    fun reconcileIndeterminateUpload(
        expectedFile: ReceiptFileDescriptor,
        remotePath: ReceiptPath,
        remoteFileSizeBytes: Long,
        remoteContentSha256: String,
    ): ReconcileIndeterminateUploadDecision {
        if (expectedFile.remotePath != remotePath) {
            return ReconcileIndeterminateUploadDecision.Conflict(
                reason = "remote_path_mismatch",
            )
        }

        val sizeMatches = expectedFile.fileSizeBytes == remoteFileSizeBytes
        val hashMatches = expectedFile.contentSha256.equals(remoteContentSha256, ignoreCase = true)

        return if (sizeMatches && hashMatches) {
            ReconcileIndeterminateUploadDecision.Match(
                verifiedRemotePath = remotePath,
            )
        } else {
            ReconcileIndeterminateUploadDecision.Conflict(
                reason = "remote_object_exists_with_differing_content",
            )
        }
    }
}

package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

enum class PaymentMethod {
    CASH,
    CREDIT_CARD,
    DEBIT_CARD,
    BANK_TRANSFER,
    OTHER,
}

/** Private Supabase Storage nesne yolu; public URL domain modelinde tutulmaz. */
data class ReceiptPath(val value: String) {
    init {
        require(value.isNotBlank() && "://" !in value) {
            "Makbuz için yalnız private storage yolu kullanılmalıdır."
        }
    }
}

data class InstallmentInfo(
    val number: Int,
    val total: Int,
    val groupId: EntityId,
) {
    init {
        require(number in 1..total) { "Taksit numarası toplam taksit aralığında olmalıdır." }
    }
}

enum class TransactionSplitMode {
    EQUAL,
    CUSTOM,
}

data class TransactionParticipantShare(
    val userId: EntityId,
    val amountMinor: Long,
)

/** Gelir/gider ve kullanıcıya gösterilen sync durumu; teknik sürüm metadata'sı Room entity'sinde kalır. */
data class Transaction(
    val id: EntityId,
    val ownerId: EntityId,
    val workspaceId: EntityId?,
    val amount: Money,
    val type: TransactionType,
    val categoryId: EntityId,
    val description: String?,
    val paymentMethod: PaymentMethod,
    val transactionDate: LocalDate,
    val receiptPath: ReceiptPath?,
    val installment: InstallmentInfo?,
    val createdAt: Instant,
    /** Shared expense payer. Personal and legacy records default to the transaction owner. */
    val paidByUserId: EntityId = ownerId,
    /** Members included in an equal shared-expense split. */
    val participantUserIds: List<EntityId> = listOf(ownerId),
    /** İsteğe bağlı işlem notu / açıklaması. */
    val note: String? = null,
    val syncStatus: SyncStatus? = null,
    val splitMode: TransactionSplitMode = TransactionSplitMode.EQUAL,
    val participantShares: List<TransactionParticipantShare> = emptyList(),
) {
    init {
        require(amount.amountMinor > 0) { "İşlem tutarı sıfırdan büyük olmalıdır." }
        require(description == null || (description.isNotBlank() && description.length <= MAX_DESCRIPTION_LENGTH)) {
            "İşlem açıklaması boş olamaz ve 500 karakteri geçemez."
        }
        require(note == null || (note.isNotBlank() && note.length <= MAX_NOTE_LENGTH)) {
            "İşlem notu boş olamaz ve 500 karakteri geçemez."
        }
        require(participantUserIds.isNotEmpty() && participantUserIds.distinct().size == participantUserIds.size) {
            "Ortak gider katılımcıları boş veya tekrarlı olamaz."
        }
        require(paidByUserId in participantUserIds) { "Ödeme yapan kişi katılımcı olmalıdır." }

        if (splitMode == TransactionSplitMode.EQUAL) {
            require(participantShares.isEmpty()) { "Eşit paylaşım modunda özel pay listesi boş olmalıdır." }
        }
    }

    fun hasEquivalentShares(otherShares: List<TransactionParticipantShare>): Boolean =
        haveEquivalentShares(participantShares, otherShares)

    fun toCanonicalShares(): List<TransactionParticipantShare> =
        participantShares.sortedBy { it.userId.value }

    companion object {
        const val MAX_TITLE_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_NOTE_LENGTH = 500

        /**
         * İki özel pay listesinin sıralamadan bağımsız eşdeğerliğini doğrular.
         * Her iki listede de duplicate kullanıcı varsa veya boyutlar farklıysa false döner.
         */
        fun haveEquivalentShares(
            shares1: List<TransactionParticipantShare>,
            shares2: List<TransactionParticipantShare>,
        ): Boolean {
            if (shares1.size != shares2.size) return false
            if (shares1.distinctBy { it.userId }.size != shares1.size) return false
            if (shares2.distinctBy { it.userId }.size != shares2.size) return false

            val map1 = shares1.associate { it.userId to it.amountMinor }
            val map2 = shares2.associate { it.userId to it.amountMinor }
            return map1 == map2
        }

        /** Form verisini saklamadan önce kırpar; boş açıklamayı null yapar. */
        fun normalizeDescription(value: String?): String? = value?.trim()?.ifBlank { null }

        /** İsteğe bağlı notu saklamadan önce kırpar; boş notu null yapar. */
        fun normalizeNote(value: String?): String? = value?.trim()?.ifBlank { null }
    }
}

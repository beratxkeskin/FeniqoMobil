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

/** Gelir veya gider hareketinin saf domain temsili. Senkronizasyon alanları Room entity'sinde tutulur. */
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
) {
    init {
        require(amount.amountMinor > 0) { "İşlem tutarı sıfırdan büyük olmalıdır." }
        require(description == null || (description.isNotBlank() && description.length <= MAX_DESCRIPTION_LENGTH)) {
            "İşlem açıklaması boş olamaz ve 500 karakteri geçemez."
        }
    }

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 500

        /** Form verisini saklamadan önce kırpar; boş açıklamayı null yapar. */
        fun normalizeDescription(value: String?): String? = value?.trim()?.ifBlank { null }
    }
}

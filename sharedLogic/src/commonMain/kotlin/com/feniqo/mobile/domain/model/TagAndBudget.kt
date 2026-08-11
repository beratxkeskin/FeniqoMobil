package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

/** V2'de transaction_tags çoktan çoğa tablosuna dönüşecek etiket iş modeli. */
data class Tag(
    val id: EntityId,
    val ownerId: EntityId,
    val workspaceId: EntityId?,
    val name: String,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank() && !name.startsWith('#')) {
            "Etiket adı boş olamaz ve # işaretiyle başlamamalıdır."
        }
    }
}

/** Veritabanındaki transaction_tags ilişkisini platformdan bağımsız temsil eder. */
data class TransactionTag(
    val transactionId: EntityId,
    val tagId: EntityId,
)

/** Yalnızca ilgili ay için geçerli kategori bazlı bütçe limiti. */
data class Budget(
    val id: EntityId,
    val ownerId: EntityId,
    val workspaceId: EntityId?,
    val categoryId: EntityId,
    val month: YearMonth,
    val limit: Money,
    val createdAt: Instant,
) {
    init {
        require(limit.amountMinor > 0) { "Bütçe limiti sıfırdan büyük olmalıdır." }
    }
}

/** Bütçe dönemi için yerel, saat diliminden etkilenmeyen YYYY-MM değeri. */
data class YearMonth(val value: String) {
    init {
        require(YEAR_MONTH.matches(value)) { "Bütçe dönemi YYYY-MM biçiminde olmalıdır." }
    }

    private companion object {
        val YEAR_MONTH = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
    }
}

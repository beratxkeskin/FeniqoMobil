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

    val year: Int get() = value.substring(0, 4).toInt()
    val month: Int get() = value.substring(5, 7).toInt()
    val monthNumber: Int get() = month

    fun previousMonth(): YearMonth {
        return if (month == 1) {
            from(year - 1, 12)
        } else {
            from(year, month - 1)
        }
    }

    fun nextMonth(): YearMonth {
        return if (month == 12) {
            from(year + 1, 1)
        } else {
            from(year, month + 1)
        }
    }

    companion object {
        private val YEAR_MONTH = Regex("^\\d{4}-(0[1-9]|1[0-2])$")

        fun from(year: Int, month: Int): YearMonth {
            val formattedMonth = if (month < 10) "0$month" else month.toString()
            return YearMonth("$year-$formattedMonth")
        }

        fun from(date: LocalDate): YearMonth = from(date.year, date.monthNumber)
    }
}

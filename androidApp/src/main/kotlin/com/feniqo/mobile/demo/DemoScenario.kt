package com.feniqo.mobile.demo

import com.feniqo.mobile.domain.model.*
import java.time.LocalDate as JavaDate
import kotlin.time.Instant

/** Takvim aylarını korur; ayın ilk günü ve artık yıllarda da gelecek tarihli geçmiş üretmez. */
class DemoScenario(val today: JavaDate) {
    val firstMonth: JavaDate = today.withDayOfMonth(1).minusMonths(5)
    fun date(value: JavaDate): LocalDate = LocalDate.parse(value.toString())
    fun money(minor: Long, currency: Currency = Currency.TRY) = Money(minor, currency)

    fun transactions(categories: Map<String, EntityId>): List<Transaction> = buildList {
        fun addEntry(day: JavaDate, key: String, title: String, minor: Long,
            method: PaymentMethod = PaymentMethod.DEBIT_CARD, currency: Currency = Currency.TRY,
            installment: InstallmentInfo? = null) {
            if (day > today) return
            val id = EntityId(java.util.UUID.nameUUIDFromBytes("demo:$day:$title".toByteArray(Charsets.UTF_8)).toString())
            add(Transaction(id, DemoAuthRepository.USER_ID, null, money(minor, currency),
                if (key == "salary" || key == "freelance") TransactionType.INCOME else TransactionType.EXPENSE,
                categories.getValue(key), title, method, date(day), null, installment,
                Instant.parse("${day}T12:00:00Z"), note = "Kurgusal demo kaydı"))
        }
        for (offset in 0L..5L) {
            val month = firstMonth.plusMonths(offset)
            addEntry(month, "salary", "Aylık maaş", 6_500_000L + offset * 100_000L, PaymentMethod.BANK_TRANSFER)
            addEntry(month, "housing", "Ev kirası", 1_800_000L, PaymentMethod.BANK_TRANSFER)
            addEntry(month.plusDays(9), "freelance", "Tasarım projesi ödemesi", 850_000L, PaymentMethod.BANK_TRANSFER)
            addEntry(month.plusDays(3), "utilities", "Elektrik ve internet", 165_000L + offset * 3500L)
            addEntry(month.plusDays(11), "healthcare", "Eczane alışverişi", 48_750L)
            addEntry(month.plusDays(14), "education", "Kitap ve eğitim", 92_500L)
            for (day in 0 until month.lengthOfMonth()) {
                val current = month.plusDays(day.toLong())
                if (day % 5 == 0) addEntry(current, "groceries", "Haftalık market alışverişi", 145_000L + offset * 2500L)
                if (day % 3 == 0) addEntry(current, "food_dining", "Kafe ve öğle yemeği", 42_500L + day * 500L, PaymentMethod.CREDIT_CARD)
                if (day % 7 == 1) addEntry(current, "transportation", "Ulaşım kartı yükleme", 35_000L, PaymentMethod.CASH)
                if (day % 10 == 2) addEntry(current, "entertainment", "Sinema ve etkinlik", 65_000L)
            }
        }
        val group = EntityId("de000000-0000-4000-8000-000000000100")
        for (number in 1..3) {
            val day = today.withDayOfMonth(1).minusMonths((3 - number).toLong())
            addEntry(day, "shopping", "Bilgisayar taksiti $number/3", 400_000L, PaymentMethod.CREDIT_CARD,
                installment = InstallmentInfo(number, 3, group))
        }
        addEntry(today, "travel", "Yurt dışı ulaşım", 2500, currency = Currency.EUR)
        addEntry(today, "education", "Yazılım eğitimi", 4900, currency = Currency.USD)
    }
}

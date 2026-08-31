package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule

/**
 * Tekrarlayan işlemler ve abonelikler için platformdan bağımsız, saf tekrar takvimi ve vade hesaplama motoru.
 * Drift engelleme amacıyla hesaplamalar her zaman [RecurrenceRule.startDate] referans alınarak anchor bazlı yürütülür.
 */
object RecurrenceScheduleCalculator {

    const val DEFAULT_MAX_OCCURRENCES = 500

    /**
     * Verilen kural ve son üretim tarihine göre bir sonraki planlı tekrar tarihini hesaplar.
     *
     * @param rule Tekrarlama kuralı (frequency, interval, startDate, endDate).
     * @param lastGeneratedDate En son üretilen tekrar tarihi. Null ise ilk aday [RecurrenceRule.startDate]'dir.
     * @return Bir sonraki tekrar tarihi; eğer bitiş tarihi ([RecurrenceRule.endDate]) aşılmışsa null döner.
     * @throws IllegalArgumentException [lastGeneratedDate] başlangıçtan önceyse veya planlı takvim dizisine uymuyorsa.
     */
    fun nextOccurrenceAfter(
        rule: RecurrenceRule,
        lastGeneratedDate: LocalDate?,
    ): LocalDate? {
        if (lastGeneratedDate == null) {
            val candidate = rule.startDate
            return if (rule.endDate != null && candidate > rule.endDate) null else candidate
        }

        require(lastGeneratedDate >= rule.startDate) {
            "Son üretim tarihi ($lastGeneratedDate) kural başlangıç tarihinden (${rule.startDate}) önce olamaz."
        }
        require(rule.endDate == null || lastGeneratedDate <= rule.endDate) {
            "Son üretim tarihi ($lastGeneratedDate) kural bitiş tarihinden (${rule.endDate}) sonra olamaz."
        }

        val nextDate = when (rule.frequency) {
            RecurrenceFrequency.DAILY -> {
                val daysDiff = lastGeneratedDate.toEpochDays() - rule.startDate.toEpochDays()
                require(daysDiff % rule.interval == 0L) {
                    "Son üretim tarihi ($lastGeneratedDate) günlük tekrar aralığına (${rule.interval}) uymuyor."
                }
                LocalDate.fromEpochDays(lastGeneratedDate.toEpochDays() + rule.interval)
            }
            RecurrenceFrequency.WEEKLY -> {
                val daysDiff = lastGeneratedDate.toEpochDays() - rule.startDate.toEpochDays()
                val stepDays = rule.interval.toLong() * 7L
                require(daysDiff % stepDays == 0L) {
                    "Son üretim tarihi ($lastGeneratedDate) haftalık tekrar aralığına ($stepDays gün) uymuyor."
                }
                LocalDate.fromEpochDays(lastGeneratedDate.toEpochDays() + stepDays)
            }
            RecurrenceFrequency.MONTHLY -> {
                val monthsDiff = (lastGeneratedDate.year - rule.startDate.year) * 12 +
                    (lastGeneratedDate.monthNumber - rule.startDate.monthNumber)
                require(monthsDiff >= 0 && monthsDiff % rule.interval == 0) {
                    "Son üretim tarihi ($lastGeneratedDate) aylık tekrar aralığına (${rule.interval} ay) uymuyor."
                }
                val expectedCurrent = calculateMonthlyOccurrence(rule.startDate, monthsDiff)
                require(lastGeneratedDate == expectedCurrent) {
                    "Son üretim tarihi ($lastGeneratedDate) beklenen aylık planlanan günle ($expectedCurrent) uyuşmuyor."
                }
                calculateMonthlyOccurrence(rule.startDate, monthsDiff + rule.interval)
            }
            RecurrenceFrequency.YEARLY -> {
                val yearsDiff = lastGeneratedDate.year - rule.startDate.year
                require(yearsDiff >= 0 && yearsDiff % rule.interval == 0) {
                    "Son üretim tarihi ($lastGeneratedDate) yıllık tekrar aralığına (${rule.interval} yıl) uymuyor."
                }
                val expectedCurrent = calculateYearlyOccurrence(rule.startDate, yearsDiff)
                require(lastGeneratedDate == expectedCurrent) {
                    "Son üretim tarihi ($lastGeneratedDate) beklenen yıllık planlanan günle ($expectedCurrent) uyuşmuyor."
                }
                calculateYearlyOccurrence(rule.startDate, yearsDiff + rule.interval)
            }
        }

        return if (rule.endDate != null && nextDate > rule.endDate) {
            null
        } else {
            nextDate
        }
    }

    /**
     * [throughDate] tarihine kadar (dahil) vadesi gelmiş ve henüz üretilmemiş tüm tekrar tarihlerini döndürür.
     *
     * @param rule Tekrarlama kuralı.
     * @param lastGeneratedDate En son üretilen tekrar tarihi (null ise startDate'ten başlar).
     * @param throughDate Hangi tarihe kadar olan vadelerin istendiği (ör. bugünün tarihi).
     * @param maxOccurrences Tek seferde üretilebilecek maksimum tekrar sayısı.
     * @return [throughDate] ve [rule.endDate] sınırları içindeki artan sırada tekil tarihler listesi.
     * @throws IllegalArgumentException [maxOccurrences] <= 0 ise veya [lastGeneratedDate] geçersizse.
     */
    fun occurrencesDueThrough(
        rule: RecurrenceRule,
        lastGeneratedDate: LocalDate?,
        throughDate: LocalDate,
        maxOccurrences: Int = DEFAULT_MAX_OCCURRENCES,
    ): List<LocalDate> {
        require(maxOccurrences > 0) {
            "maxOccurrences sıfırdan büyük olmalıdır: $maxOccurrences"
        }

        val result = mutableListOf<LocalDate>()
        var currentLast = lastGeneratedDate

        while (result.size < maxOccurrences) {
            val next = nextOccurrenceAfter(rule, currentLast) ?: break
            if (next > throughDate) {
                break
            }
            result.add(next)
            currentLast = next
        }

        return result
    }

    private fun calculateMonthlyOccurrence(startDate: LocalDate, monthOffset: Int): LocalDate {
        val totalMonths = startDate.year * 12 + (startDate.monthNumber - 1) + monthOffset
        val targetYear = totalMonths / 12
        val targetMonth = (totalMonths % 12) + 1
        val maxDays = daysInMonth(targetYear, targetMonth)
        val targetDay = minOf(startDate.dayOfMonth, maxDays)
        return LocalDate(targetYear, targetMonth, targetDay)
    }

    private fun calculateYearlyOccurrence(startDate: LocalDate, yearOffset: Int): LocalDate {
        val targetYear = startDate.year + yearOffset
        val targetMonth = startDate.monthNumber
        val maxDays = daysInMonth(targetYear, targetMonth)
        val targetDay = minOf(startDate.dayOfMonth, maxDays)
        return LocalDate(targetYear, targetMonth, targetDay)
    }

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> throw IllegalArgumentException("Geçersiz ay: $month")
    }

    fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}

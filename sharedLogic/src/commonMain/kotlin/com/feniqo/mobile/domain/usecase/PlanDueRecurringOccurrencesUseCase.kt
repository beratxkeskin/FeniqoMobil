package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.RecurringDueOccurrence
import com.feniqo.mobile.domain.model.RecurringOccurrenceKey
import com.feniqo.mobile.domain.model.RecurringTransaction
import com.feniqo.mobile.domain.validation.RecurrenceScheduleCalculator

/**
 * Verilen tekrarlayan işlem kuralları arasından vadesi gelen adayları hesaplayan saf use case.
 * Sonuçlar deterministik olarak artan dueDate ve ardından recurringTransaction.id sıralamasıyla döner.
 */
class PlanDueRecurringOccurrencesUseCase {

    operator fun invoke(
        recurringTransactions: List<RecurringTransaction>,
        throughDate: LocalDate,
        maxOccurrencesPerRule: Int = DEFAULT_MAX_PER_RULE,
        maxTotalOccurrences: Int = DEFAULT_MAX_TOTAL,
    ): List<RecurringDueOccurrence> {
        require(maxOccurrencesPerRule > 0) {
            "maxOccurrencesPerRule sıfırdan büyük olmalıdır: $maxOccurrencesPerRule"
        }
        require(maxTotalOccurrences > 0) {
            "maxTotalOccurrences sıfırdan büyük olmalıdır: $maxTotalOccurrences"
        }

        val allCandidates = mutableListOf<RecurringDueOccurrence>()
        val seenKeys = mutableSetOf<RecurringOccurrenceKey>()

        for (recurring in recurringTransactions) {
            if (!recurring.isActive) continue

            val dueDates = RecurrenceScheduleCalculator.occurrencesDueThrough(
                rule = recurring.rule,
                lastGeneratedDate = recurring.lastGeneratedDate,
                throughDate = throughDate,
                maxOccurrences = maxOccurrencesPerRule,
            )

            for (dueDate in dueDates) {
                val key = RecurringOccurrenceKey(
                    recurringTransactionId = recurring.id,
                    dueDate = dueDate,
                )
                require(seenKeys.add(key)) {
                    "Mükerrer aday tekrar anahtarı tespit edildi: $key"
                }
                allCandidates.add(
                    RecurringDueOccurrence(
                        key = key,
                        recurringTransaction = recurring,
                        dueDate = dueDate,
                    )
                )
            }
        }

        allCandidates.sortWith(
            compareBy<RecurringDueOccurrence> { it.dueDate }
                .thenBy { it.recurringTransaction.id.value }
        )

        return if (allCandidates.size > maxTotalOccurrences) {
            allCandidates.take(maxTotalOccurrences)
        } else {
            allCandidates
        }
    }

    companion object {
        const val DEFAULT_MAX_PER_RULE = 50
        const val DEFAULT_MAX_TOTAL = 200
    }
}

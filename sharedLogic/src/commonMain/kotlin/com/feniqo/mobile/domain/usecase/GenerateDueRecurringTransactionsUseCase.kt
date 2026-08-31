package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.GenerateRecurringTransactionsResult
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.repository.RecurringTransactionRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlinx.datetime.Instant

class GenerateDueRecurringTransactionsUseCase(
    private val recurringTransactionRepository: RecurringTransactionRepository,
) {
    suspend operator fun invoke(
        throughDate: LocalDate,
        maxOccurrencesPerRule: Int = RecurringTransactionRepository.DEFAULT_MAX_PER_RULE,
        maxTotalOccurrences: Int = RecurringTransactionRepository.DEFAULT_MAX_TOTAL,
        createdAt: Instant,
    ): RepositoryResult<GenerateRecurringTransactionsResult> {
        if (maxOccurrencesPerRule <= 0) {
            return RepositoryResult.Failure(AppError.Validation("max_occurrences_per_rule_must_be_positive"))
        }
        if (maxTotalOccurrences <= 0) {
            return RepositoryResult.Failure(AppError.Validation("max_total_occurrences_must_be_positive"))
        }
        return recurringTransactionRepository.generateDueTransactions(
            throughDate = throughDate,
            maxOccurrencesPerRule = maxOccurrencesPerRule,
            maxTotalOccurrences = maxTotalOccurrences,
            createdAt = createdAt,
        )
    }
}

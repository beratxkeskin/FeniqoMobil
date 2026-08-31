package com.feniqo.mobile.data.remote.core

import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto

/**
 * Outbox işlemini operation_id ile sunucuda idempotent ve koşullu olarak uygulayan V2 uzak yazıcı sözleşmesi.
 */
interface IdempotentConditionalRemoteWriter {
    suspend fun writeProfile(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: ProfileDto,
    ): ConditionalRemoteWriteResult<ProfileDto>

    suspend fun writeCategory(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: CategoryDto,
    ): ConditionalRemoteWriteResult<CategoryDto>

    suspend fun writeTransaction(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: TransactionDto,
    ): ConditionalRemoteWriteResult<TransactionDto>

    suspend fun writeBudget(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: BudgetDto,
    ): ConditionalRemoteWriteResult<BudgetDto>

    suspend fun writeRecurringTransaction(
        operationId: String,
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: RecurringTransactionDto,
    ): ConditionalRemoteWriteResult<RecurringTransactionDto>
}


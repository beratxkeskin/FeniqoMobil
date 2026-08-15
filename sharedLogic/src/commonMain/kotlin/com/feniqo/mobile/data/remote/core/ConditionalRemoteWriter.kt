package com.feniqo.mobile.data.remote.core

import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.TransactionDto

/** Outbox işleminin sunucuda atomik olarak uygulanmasını isteyen V1 yazma türü. */
enum class RemoteWriteOperation {
    CREATE,
    UPDATE,
    DELETE,
}

/**
 * Koşullu RPC sonucu. Çakışmada uzak kayıt ayrıca döner; böylece yerel veri sessizce ezilmez.
 */
sealed interface ConditionalRemoteWriteResult<out T> {
    data class Applied<T>(val record: T) : ConditionalRemoteWriteResult<T>
    data class Conflict<T>(val remoteRecord: T) : ConditionalRemoteWriteResult<T>
    data object NotFound : ConditionalRemoteWriteResult<Nothing>
}

interface ConditionalRemoteWriter {
    suspend fun writeProfile(
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: ProfileDto,
    ): ConditionalRemoteWriteResult<ProfileDto>

    suspend fun writeCategory(
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: CategoryDto,
    ): ConditionalRemoteWriteResult<CategoryDto>

    suspend fun writeTransaction(
        operation: RemoteWriteOperation,
        baseVersion: Long?,
        dto: TransactionDto,
    ): ConditionalRemoteWriteResult<TransactionDto>
}

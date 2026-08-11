package com.feniqo.mobile.domain.repository

import com.feniqo.mobile.domain.model.AppError

/** Repository yazma işlemlerinin beklenen başarı veya iş hatası sonucudur. */
sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>
    data class Failure(val error: AppError) : RepositoryResult<Nothing>
}

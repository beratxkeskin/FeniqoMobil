package com.feniqo.mobile.data.repository

import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.mapper.toDomain
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.repository.SyncEntityType
import kotlinx.serialization.json.Json

private val transactionConflictJson = Json { ignoreUnknownKeys = true }

internal fun decodeTransactionConflictSnapshot(type: String, payload: String): Transaction? {
    if (type != SyncEntityType.TRANSACTION.name) return null
    return try {
        transactionConflictJson.decodeFromString<TransactionDto>(payload).toDomain()
    } catch (_: Exception) {
        null
    }
}

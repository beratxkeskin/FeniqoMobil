package com.feniqo.mobile.domain.repository

const val PERSONAL_BACKUP_MAX_BYTES: Int = 10 * 1024 * 1024

data class PersonalBackupScope(
    val categoryCount: Int,
    val transactionCount: Int,
)

sealed interface PersonalBackupPreview {
    data class Valid(val categoryCount: Int, val transactionCount: Int) : PersonalBackupPreview
    data class Invalid(val reason: String) : PersonalBackupPreview
}

sealed interface PersonalBackupImportOutcome {
    data class Success(val categoryCount: Int, val transactionCount: Int) : PersonalBackupImportOutcome
    data class Failure(val reason: String) : PersonalBackupImportOutcome
}

/**
 * Presentation katmanını yedek dosya formatı ve data implementasyonlarından ayıran port.
 */
interface PersonalBackupRepository {
    suspend fun calculateScope(): PersonalBackupScope
    suspend fun export(): String
    fun preview(raw: String): PersonalBackupPreview
    suspend fun import(raw: String): PersonalBackupImportOutcome
}

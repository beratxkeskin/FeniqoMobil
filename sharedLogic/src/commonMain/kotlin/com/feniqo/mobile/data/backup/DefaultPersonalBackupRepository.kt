package com.feniqo.mobile.data.backup

import com.feniqo.mobile.domain.repository.PersonalBackupImportOutcome
import com.feniqo.mobile.domain.repository.PersonalBackupPreview
import com.feniqo.mobile.domain.repository.PersonalBackupRepository
import com.feniqo.mobile.domain.repository.PersonalBackupScope

class DefaultPersonalBackupRepository(
    private val exporter: PersonalBackupExporter,
    private val importer: PersonalBackupImporter,
) : PersonalBackupRepository {
    override suspend fun calculateScope(): PersonalBackupScope = exporter.calculateScope().let {
        PersonalBackupScope(it.categoryCount, it.transactionCount)
    }

    override suspend fun export(): String = exporter.export()

    override fun preview(raw: String): PersonalBackupPreview = when (val decoded = FeniqoBackupCodec.decode(raw)) {
        is BackupDecodeResult.Invalid -> PersonalBackupPreview.Invalid(decoded.reason)
        is BackupDecodeResult.Valid -> PersonalBackupPreview.Valid(
            categoryCount = decoded.backup.categories.size,
            transactionCount = decoded.backup.transactions.size,
        )
    }

    override suspend fun import(raw: String): PersonalBackupImportOutcome = when (val result = importer.import(raw)) {
        is BackupImportResult.Success -> PersonalBackupImportOutcome.Success(
            categoryCount = result.categoryCount,
            transactionCount = result.transactionCount,
        )
        is BackupImportResult.Failure -> PersonalBackupImportOutcome.Failure(result.reason)
    }
}

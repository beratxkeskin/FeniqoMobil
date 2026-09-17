package com.feniqo.mobile.presentation.settings

import androidx.lifecycle.ViewModel
import com.feniqo.mobile.data.backup.BackupDecodeResult
import com.feniqo.mobile.data.backup.BackupImportResult
import com.feniqo.mobile.data.backup.FeniqoBackupCodec
import com.feniqo.mobile.data.backup.PersonalBackupImporter
import com.feniqo.mobile.domain.usecase.ObserveTransactionsUseCase
import com.feniqo.mobile.domain.usecase.TransactionCsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class TransactionExportViewModel @Inject constructor(
    private val observeTransactions: ObserveTransactionsUseCase,
    private val csvExporter: TransactionCsvExporter,
    private val backupImporter: PersonalBackupImporter,
) : ViewModel() {
    suspend fun buildCsv(): String = csvExporter.export(observeTransactions().first())

    fun previewBackup(raw: String): BackupPreviewResult = when (val result = FeniqoBackupCodec.decode(raw)) {
        is BackupDecodeResult.Invalid -> BackupPreviewResult.Invalid(result.reason)
        is BackupDecodeResult.Valid -> BackupPreviewResult.Valid(
            categoryCount = result.backup.categories.size,
            transactionCount = result.backup.transactions.size,
        )
    }

    suspend fun importBackup(raw: String): BackupImportResult = backupImporter.import(raw)
}

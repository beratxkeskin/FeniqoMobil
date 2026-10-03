package com.feniqo.mobile.presentation.settings

import androidx.lifecycle.ViewModel
import com.feniqo.mobile.domain.repository.PersonalBackupImportOutcome
import com.feniqo.mobile.domain.repository.PersonalBackupPreview
import com.feniqo.mobile.domain.repository.PersonalBackupRepository
import com.feniqo.mobile.domain.usecase.ObserveTransactionsUseCase
import com.feniqo.mobile.domain.usecase.TransactionCsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class TransactionExportViewModel @Inject constructor(
    private val observeTransactions: ObserveTransactionsUseCase,
    private val csvExporter: TransactionCsvExporter,
    private val personalBackupRepository: PersonalBackupRepository,
) : ViewModel() {
    suspend fun buildCsv(): String = csvExporter.export(observeTransactions().first())

    fun previewBackup(raw: String): BackupPreviewResult = when (val result = personalBackupRepository.preview(raw)) {
        is PersonalBackupPreview.Invalid -> BackupPreviewResult.Invalid(result.reason)
        is PersonalBackupPreview.Valid -> BackupPreviewResult.Valid(result.categoryCount, result.transactionCount)
    }

    suspend fun importBackup(raw: String): PersonalBackupImportOutcome = personalBackupRepository.import(raw)
}

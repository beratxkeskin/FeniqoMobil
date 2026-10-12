package com.feniqo.mobile.presentation.settings

import androidx.compose.runtime.Composable
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.backup_category_count_plural
import feniqomobil.sharedui.generated.resources.backup_counts_summary_format
import feniqomobil.sharedui.generated.resources.backup_export_success_format
import feniqomobil.sharedui.generated.resources.backup_import_error_auth_session_required
import feniqomobil.sharedui.generated.resources.backup_import_error_failed
import feniqomobil.sharedui.generated.resources.backup_import_error_invalid_file
import feniqomobil.sharedui.generated.resources.backup_import_error_personal_scope_required
import feniqomobil.sharedui.generated.resources.backup_import_error_too_large
import feniqomobil.sharedui.generated.resources.backup_import_error_version_unsupported
import feniqomobil.sharedui.generated.resources.backup_import_success_format
import feniqomobil.sharedui.generated.resources.backup_transaction_count_plural
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

sealed interface BackupImportUiError {
    data object PersonalScopeRequired : BackupImportUiError
    data object AuthenticationRequired : BackupImportUiError
    data object FileTooLarge : BackupImportUiError
    data object UnsupportedVersion : BackupImportUiError
    data object ImportFailed : BackupImportUiError
    data object InvalidFile : BackupImportUiError

    fun toStringResource(): StringResource = when (this) {
        PersonalScopeRequired -> Res.string.backup_import_error_personal_scope_required
        AuthenticationRequired -> Res.string.backup_import_error_auth_session_required
        FileTooLarge -> Res.string.backup_import_error_too_large
        UnsupportedVersion -> Res.string.backup_import_error_version_unsupported
        ImportFailed -> Res.string.backup_import_error_failed
        InvalidFile -> Res.string.backup_import_error_invalid_file
    }
}

fun mapBackupFailureReasonToUiError(reason: String): BackupImportUiError = when (reason) {
    "backup_personal_scope_required" -> BackupImportUiError.PersonalScopeRequired
    "auth_session_required" -> BackupImportUiError.AuthenticationRequired
    "backup_too_large" -> BackupImportUiError.FileTooLarge
    "backup_version_unsupported" -> BackupImportUiError.UnsupportedVersion
    "backup_import_failed" -> BackupImportUiError.ImportFailed
    else -> BackupImportUiError.InvalidFile
}

@Composable
fun BackupImportUiError.toLocalizedMessage(): String = stringResource(toStringResource())

suspend fun BackupImportUiError.resolveLocalizedMessage(): String = getString(toStringResource())

@Composable
fun formatLocalizedBackupCounts(categoryCount: Int, transactionCount: Int): String {
    val categories = pluralStringResource(Res.plurals.backup_category_count_plural, categoryCount, categoryCount)
    val transactions = pluralStringResource(Res.plurals.backup_transaction_count_plural, transactionCount, transactionCount)
    return stringResource(Res.string.backup_counts_summary_format, categories, transactions)
}

suspend fun resolveLocalizedBackupCounts(categoryCount: Int, transactionCount: Int): String {
    val categories = getPluralString(Res.plurals.backup_category_count_plural, categoryCount, categoryCount)
    val transactions = getPluralString(Res.plurals.backup_transaction_count_plural, transactionCount, transactionCount)
    return getString(Res.string.backup_counts_summary_format, categories, transactions)
}

@Composable
fun formatLocalizedBackupExportSuccess(categoryCount: Int, transactionCount: Int): String {
    val categories = pluralStringResource(Res.plurals.backup_category_count_plural, categoryCount, categoryCount)
    val transactions = pluralStringResource(Res.plurals.backup_transaction_count_plural, transactionCount, transactionCount)
    return stringResource(Res.string.backup_export_success_format, categories, transactions)
}

suspend fun resolveLocalizedBackupExportSuccess(categoryCount: Int, transactionCount: Int): String {
    val categories = getPluralString(Res.plurals.backup_category_count_plural, categoryCount, categoryCount)
    val transactions = getPluralString(Res.plurals.backup_transaction_count_plural, transactionCount, transactionCount)
    return getString(Res.string.backup_export_success_format, categories, transactions)
}

@Composable
fun formatLocalizedBackupImportSuccess(categoryCount: Int, transactionCount: Int): String {
    val categories = pluralStringResource(Res.plurals.backup_category_count_plural, categoryCount, categoryCount)
    val transactions = pluralStringResource(Res.plurals.backup_transaction_count_plural, transactionCount, transactionCount)
    return stringResource(Res.string.backup_import_success_format, categories, transactions)
}

suspend fun resolveLocalizedBackupImportSuccess(categoryCount: Int, transactionCount: Int): String {
    val categories = getPluralString(Res.plurals.backup_category_count_plural, categoryCount, categoryCount)
    val transactions = getPluralString(Res.plurals.backup_transaction_count_plural, transactionCount, transactionCount)
    return getString(Res.string.backup_import_success_format, categories, transactions)
}

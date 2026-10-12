package com.feniqo.mobile.presentation.settings

import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.account_login_method_account
import feniqomobil.sharedui.generated.resources.account_login_method_email
import feniqomobil.sharedui.generated.resources.backup_import_error_auth_session_required
import feniqomobil.sharedui.generated.resources.backup_import_error_failed
import feniqomobil.sharedui.generated.resources.backup_import_error_invalid_file
import feniqomobil.sharedui.generated.resources.backup_import_error_personal_scope_required
import feniqomobil.sharedui.generated.resources.backup_import_error_too_large
import feniqomobil.sharedui.generated.resources.backup_import_error_version_unsupported
import feniqomobil.sharedui.generated.resources.profile_error_name_too_short
import feniqomobil.sharedui.generated.resources.profile_error_save_failed
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDataAndSyncLocalizationTest {

    @Test
    fun backupImportUiError_mapsReasonsToTypedErrorExhaustively() {
        assertEquals(
            BackupImportUiError.PersonalScopeRequired,
            mapBackupFailureReasonToUiError("backup_personal_scope_required"),
        )
        assertEquals(
            BackupImportUiError.AuthenticationRequired,
            mapBackupFailureReasonToUiError("auth_session_required"),
        )
        assertEquals(
            BackupImportUiError.FileTooLarge,
            mapBackupFailureReasonToUiError("backup_too_large"),
        )
        assertEquals(
            BackupImportUiError.UnsupportedVersion,
            mapBackupFailureReasonToUiError("backup_version_unsupported"),
        )
        assertEquals(
            BackupImportUiError.ImportFailed,
            mapBackupFailureReasonToUiError("backup_import_failed"),
        )
        assertEquals(
            BackupImportUiError.InvalidFile,
            mapBackupFailureReasonToUiError("unknown_arbitrary_error"),
        )
    }

    @Test
    fun backupImportUiError_mapsToStringResources() {
        assertEquals(
            Res.string.backup_import_error_personal_scope_required,
            BackupImportUiError.PersonalScopeRequired.toStringResource(),
        )
        assertEquals(
            Res.string.backup_import_error_auth_session_required,
            BackupImportUiError.AuthenticationRequired.toStringResource(),
        )
        assertEquals(
            Res.string.backup_import_error_too_large,
            BackupImportUiError.FileTooLarge.toStringResource(),
        )
        assertEquals(
            Res.string.backup_import_error_version_unsupported,
            BackupImportUiError.UnsupportedVersion.toStringResource(),
        )
        assertEquals(
            Res.string.backup_import_error_failed,
            BackupImportUiError.ImportFailed.toStringResource(),
        )
        assertEquals(
            Res.string.backup_import_error_invalid_file,
            BackupImportUiError.InvalidFile.toStringResource(),
        )
    }

    @Test
    fun personalProfileUiError_mapsToStringResources() {
        assertEquals(
            Res.string.profile_error_name_too_short,
            PersonalProfileUiError.NAME_TOO_SHORT.toStringResource(),
        )
        assertEquals(
            Res.string.profile_error_save_failed,
            PersonalProfileUiError.SAVE_FAILED.toStringResource(),
        )
    }

    @Test
    fun accountLoginMethod_mapsToStringResources() {
        assertEquals(
            Res.string.account_login_method_email,
            AccountLoginMethod.EMAIL.toStringResource(),
        )
        assertEquals(
            Res.string.account_login_method_account,
            AccountLoginMethod.ACCOUNT.toStringResource(),
        )
    }

    @Test
    fun formatLocalizedSyncDateTime_formatsNullAsNeverSynced() {
        val resultTr = formatLocalizedSyncDateTime(
            epochMillis = null,
            formatTemplate = "%1\$s.%2\$s.%3\$s %4\$s:%5\$s",
            neverSyncedText = "Henüz eşitlenmedi",
        )
        assertEquals("Henüz eşitlenmedi", resultTr)

        val resultEn = formatLocalizedSyncDateTime(
            epochMillis = null,
            formatTemplate = "%2\$s/%1\$s/%3\$s %4\$s:%5\$s",
            neverSyncedText = "Not synced yet",
        )
        assertEquals("Not synced yet", resultEn)
    }

    @Test
    fun formatLocalizedSyncDateTime_formatsValidTimestampWithTemplate() {
        // 2026-10-12T14:30:00Z = 1791815400000 ms
        val epochMillis = 1791815400000L
        val utc = TimeZone.UTC

        val resultTr = formatLocalizedSyncDateTime(
            epochMillis = epochMillis,
            formatTemplate = "%1\$s.%2\$s.%3\$s %4\$s:%5\$s",
            neverSyncedText = "Henüz eşitlenmedi",
            timeZone = utc,
        )
        assertEquals("12.10.2026 14:30", resultTr)

        val resultEn = formatLocalizedSyncDateTime(
            epochMillis = epochMillis,
            formatTemplate = "%2\$s/%1\$s/%3\$s %4\$s:%5\$s",
            neverSyncedText = "Not synced yet",
            timeZone = utc,
        )
        assertEquals("10/12/2026 14:30", resultEn)
    }
}

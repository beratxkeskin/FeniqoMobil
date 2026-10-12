package com.feniqo.mobile.presentation.settings

import androidx.compose.runtime.Composable
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.backup_export_csv_default_filename
import feniqomobil.sharedui.generated.resources.backup_export_csv_failed
import feniqomobil.sharedui.generated.resources.backup_export_csv_success
import feniqomobil.sharedui.generated.resources.backup_export_failed
import feniqomobil.sharedui.generated.resources.backup_export_json_default_filename
import feniqomobil.sharedui.generated.resources.backup_export_write_failed
import feniqomobil.sharedui.generated.resources.backup_import_read_or_large_error
import feniqomobil.sharedui.generated.resources.profile_personal_info_saved
import feniqomobil.sharedui.generated.resources.profile_photo_camera_permission_required
import feniqomobil.sharedui.generated.resources.profile_photo_camera_storage_failed
import feniqomobil.sharedui.generated.resources.profile_photo_corrupted
import feniqomobil.sharedui.generated.resources.profile_photo_read_failed
import feniqomobil.sharedui.generated.resources.profile_photo_process_error
import feniqomobil.sharedui.generated.resources.profile_photo_removed
import feniqomobil.sharedui.generated.resources.profile_photo_save_failed
import feniqomobil.sharedui.generated.resources.profile_photo_save_retry
import feniqomobil.sharedui.generated.resources.profile_photo_saved
import org.jetbrains.compose.resources.stringResource

// Profil Fotoğrafı & Profil Mesajları
@Composable
fun localizedProfilePhotoLoadFailed(): String = stringResource(Res.string.profile_photo_read_failed)

@Composable
fun localizedProfilePhotoCameraStorageFailed(): String = stringResource(Res.string.profile_photo_camera_storage_failed)

@Composable
fun localizedProfilePhotoCameraPermissionRequired(): String = stringResource(Res.string.profile_photo_camera_permission_required)

@Composable
fun localizedProfilePhotoRemoved(): String = stringResource(Res.string.profile_photo_removed)

@Composable
fun localizedProfilePhotoSaved(): String = stringResource(Res.string.profile_photo_saved)

@Composable
fun localizedProfilePhotoSaveFailed(): String = stringResource(Res.string.profile_photo_save_failed)

@Composable
fun localizedProfilePhotoCorrupted(): String = stringResource(Res.string.profile_photo_corrupted)

@Composable
fun localizedProfilePhotoSaveRetry(): String = stringResource(Res.string.profile_photo_save_retry)

@Composable
fun localizedProfilePhotoProcessError(): String = stringResource(Res.string.profile_photo_process_error)

@Composable
fun localizedProfilePersonalInfoSaved(): String = stringResource(Res.string.profile_personal_info_saved)

// Veri Yönetimi & Backup Mesajları
@Composable
fun localizedBackupExportCsvDefaultFilename(): String = stringResource(Res.string.backup_export_csv_default_filename)

@Composable
fun localizedBackupExportJsonDefaultFilename(): String = stringResource(Res.string.backup_export_json_default_filename)

@Composable
fun localizedBackupExportCsvSuccess(): String = stringResource(Res.string.backup_export_csv_success)

@Composable
fun localizedBackupExportCsvFailed(): String = stringResource(Res.string.backup_export_csv_failed)

@Composable
fun localizedBackupExportFailed(): String = stringResource(Res.string.backup_export_failed)

@Composable
fun localizedBackupExportWriteFailed(): String = stringResource(Res.string.backup_export_write_failed)

@Composable
fun localizedBackupImportReadOrLargeError(): String = stringResource(Res.string.backup_import_read_or_large_error)

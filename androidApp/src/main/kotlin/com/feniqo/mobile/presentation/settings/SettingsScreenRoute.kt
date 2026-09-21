package com.feniqo.mobile.presentation.settings

import java.io.File
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.feniqo.mobile.data.backup.BackupImportResult
import com.feniqo.mobile.data.backup.FENIQO_BACKUP_MAX_BYTES
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.repository.AutoLockTimeout
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.ui.graphics.asImageBitmap
import com.feniqo.mobile.presentation.screen.AccountScreen
import com.feniqo.mobile.presentation.screen.AppearanceScreen
import com.feniqo.mobile.presentation.screen.ChangeEmailScreen
import com.feniqo.mobile.presentation.screen.ChangePasswordScreen
import com.feniqo.mobile.presentation.screen.DataManagementScreen
import com.feniqo.mobile.presentation.screen.DeleteAccountScreen
import com.feniqo.mobile.presentation.screen.EmailSettingsScreen
import com.feniqo.mobile.presentation.screen.EmailVerificationScreen
import com.feniqo.mobile.presentation.screen.FeedbackScreen
import com.feniqo.mobile.presentation.screen.HelpAboutScreen
import com.feniqo.mobile.presentation.screen.HelpArticleStatusScreen
import com.feniqo.mobile.presentation.screen.HelpCenterScreen
import com.feniqo.mobile.presentation.screen.LanguageRegionScreen
import com.feniqo.mobile.presentation.screen.LegalInfoScreen
import com.feniqo.mobile.presentation.screen.NotificationsScreen
import com.feniqo.mobile.presentation.screen.PendingBackupInfo
import com.feniqo.mobile.presentation.screen.PersonalInfoScreen
import com.feniqo.mobile.presentation.screen.PhotoPreviewScreen
import com.feniqo.mobile.presentation.screen.SecurityPrivacyScreen
import com.feniqo.mobile.presentation.screen.SettingsScreen
import com.feniqo.mobile.presentation.screen.SignOutConfirmDialog
import com.feniqo.mobile.presentation.screen.SyncStatusScreen
import com.feniqo.mobile.presentation.sync.SyncConnectionUiState
import com.feniqo.mobile.presentation.sync.SyncStatusViewModel
import com.feniqo.mobile.presentation.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 01 Ayarlar Ana Ekran Rotası.
 */
@Composable
fun SettingsScreenRoute(
    onNavigateToAccount: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLanguageRegion: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToDataManagement: () -> Unit,
    onNavigateToHelpAbout: () -> Unit,
    onSignOutSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSignOutConfirm by remember { mutableStateOf(false) }

    val themeLabel = when (uiState.settings.theme) {
        ThemePreference.SYSTEM -> "Sistem teması"
        ThemePreference.LIGHT -> "Açık tema"
        ThemePreference.DARK -> "Koyu tema"
    }

    val languageRegionLabel = "${if (uiState.settings.language == AppLanguage.TR) "Türkçe" else "English"} • ${uiState.settings.currency.code}"

    SettingsScreen(
        displayName = uiState.profile?.fullName?.trim()?.ifBlank { null } ?: "Feniqo Kullanıcısı",
        email = uiState.displayEmail,
        themeLabel = themeLabel,
        languageRegionLabel = languageRegionLabel,
        onBack = onBack,
        onManageProfile = onNavigateToAccount,
        onNavigateToAppearance = onNavigateToAppearance,
        onNavigateToLanguageRegion = onNavigateToLanguageRegion,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToSecurity = onNavigateToSecurity,
        onNavigateToDataManagement = onNavigateToDataManagement,
        onNavigateToHelpAbout = onNavigateToHelpAbout,
        onSignOutClick = {
            if (uiState.syncOverview.pendingOperationCount > 0) {
                showSignOutConfirm = true
            } else {
                viewModel.signOut(onComplete = onSignOutSuccess)
            }
        },
        modifier = modifier,
    )

    // 17 Çıkış Onayı (Bekleyen değişiklik varsa uyaran, yoksa doğrudan onaylayan diyalog)
    if (showSignOutConfirm) {
        SignOutConfirmDialog(
            pendingChangesCount = uiState.syncOverview.pendingOperationCount,
            onDismiss = { showSignOutConfirm = false },
            onNavigateToSync = {
                showSignOutConfirm = false
                onNavigateToDataManagement()
            },
            onConfirmSignOut = {
                showSignOutConfirm = false
                viewModel.signOut(onComplete = onSignOutSuccess)
            },
        )
    }
}

/**
 * 02 Hesabım Rotası (Pano A1 ve kamera/galeri/önizleme tetikleyicisi).
 */
@Composable
fun AccountScreenRoute(
    onNavigateToPersonalInfo: () -> Unit,
    onNavigateToChangeEmail: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToDeleteAccount: () -> Unit,
    onNavigateToPhotoPreview: (draftFileName: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    avatarManager: UserAvatarManager = run {
        val context = LocalContext.current.applicationContext
        remember(context) { UserAvatarManager(context) }
    },
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val userId = uiState.profile?.id?.value.orEmpty()
    val effectiveUserId = userId.ifBlank { "current_user" }

    // Avatar dosyasını diskten oku
    var avatarVersion by remember { mutableStateOf(0) }
    val avatarBitmap = remember(effectiveUserId, avatarVersion) {
        avatarManager.getAvatarFile(effectiveUserId)?.let { file ->
            runCatching {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val draftFile = avatarManager.copyUriToDraftPreview(uri)
            if (draftFile != null) {
                onNavigateToPhotoPreview(draftFile.name)
            } else {
                Toast.makeText(context, "Görsel yüklenemedi veya okunamadı.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success: Boolean ->
        val file = pendingCameraFile
        if (success && file != null && file.exists() && file.length() > 0) {
            onNavigateToPhotoPreview(file.name)
        } else {
            file?.delete()
        }
        pendingCameraUri = null
        pendingCameraFile = null
    }

    val launchCamera = {
        val cameraTarget = avatarManager.createTempCameraFile()
        if (cameraTarget != null) {
            val (file, uri) = cameraTarget
            pendingCameraFile = file
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Kamera için güvenli depolama alanı hazırlanamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Kamera ile fotoğraf çekebilmek için kamera izni vermelisiniz.", Toast.LENGTH_SHORT).show()
        }
    }

    AccountScreen(
        displayName = uiState.profile?.fullName?.trim()?.ifBlank { null } ?: "Feniqo Kullanıcısı",
        email = uiState.displayEmail,
        emailVerificationStatus = uiState.emailVerificationStatus,
        loginMethod = if (uiState.displayEmail.isNotBlank()) "E-posta" else "Hesap",
        avatarBitmap = avatarBitmap,
        onBack = onBack,
        onNavigateToPersonalInfo = onNavigateToPersonalInfo,
        onNavigateToChangeEmail = onNavigateToChangeEmail,
        onNavigateToChangePassword = onNavigateToChangePassword,
        onNavigateToDeleteAccount = onNavigateToDeleteAccount,
        onPickFromGallery = { photoPickerLauncher.launch("image/*") },
        onTakePhoto = {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                launchCamera()
            } else {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        },
        onRemovePhoto = {
            val removed = avatarManager.removeAvatar(effectiveUserId)
            if (removed) {
                avatarVersion++
                Toast.makeText(context, "Profil fotoğrafı kaldırıldı.", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier,
    )
}

/**
 * 03 Kişisel Bilgiler Rotası.
 */
@Composable
fun PersonalInfoScreenRoute(
    onNavigateToChangeEmail: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    avatarManager: UserAvatarManager = run {
        val context = LocalContext.current
        remember { UserAvatarManager(context) }
    },
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val userId = uiState.profile?.id?.value.orEmpty()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = avatarManager.saveAvatarFromUri(userId, uri)
            Toast.makeText(
                context,
                if (saved) "Profil fotoğrafı kaydedildi." else "Fotoğraf kaydedilemedi.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(uiState.profileSaveSuccess) {
        if (uiState.profileSaveSuccess) {
            Toast.makeText(context, "Değişiklikler kaydedildi.", Toast.LENGTH_SHORT).show()
            viewModel.clearProfileStatus()
        }
    }

    LaunchedEffect(uiState.profileError) {
        uiState.profileError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            viewModel.clearProfileStatus()
        }
    }

    PersonalInfoScreen(
        currentFullName = uiState.profile?.fullName.orEmpty(),
        email = uiState.displayEmail,
        isLoading = uiState.isSavingProfile,
        onBack = onBack,
        onChangePhoto = { photoPickerLauncher.launch("image/*") },
        onNavigateToChangeEmail = onNavigateToChangeEmail,
        onSave = { fullName -> viewModel.savePersonalInfo(fullName) },
        modifier = modifier,
    )
}

/**
 * 04 Görünüm Rotası.
 */
@Composable
fun AppearanceScreenRoute(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    AppearanceScreen(
        currentTheme = uiState.settings.theme,
        maskAmounts = uiState.settings.maskAmounts,
        onBack = onBack,
        onThemeSelect = { theme ->
            viewModel.setTheme(theme)
            onThemeChange(
                when (theme) {
                    ThemePreference.SYSTEM -> ThemeMode.SYSTEM
                    ThemePreference.LIGHT -> ThemeMode.LIGHT
                    ThemePreference.DARK -> ThemeMode.DARK
                },
            )
        },
        onMaskAmountsChange = { viewModel.setMaskAmounts(it) },
        modifier = modifier,
    )
}

/**
 * 05 Dil ve Bölge Rotası.
 */
@Composable
fun LanguageRegionScreenRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LanguageRegionScreen(
        currentLanguage = uiState.settings.language,
        currentRegion = uiState.settings.region,
        currentCurrency = uiState.settings.currency,
        currentDateFormat = uiState.settings.dateFormat,
        currentNumberFormat = uiState.settings.numberFormat,
        currentFirstDayOfWeek = uiState.settings.firstDayOfWeek,
        onBack = onBack,
        onLanguageChange = { viewModel.setAppLanguage(it) },
        onRegionChange = { viewModel.setRegion(it) },
        onCurrencyChange = { viewModel.setDefaultCurrency(it) },
        onDateFormatChange = { viewModel.setDateFormat(it) },
        onNumberFormatChange = { viewModel.setNumberFormat(it) },
        onFirstDayOfWeekChange = { viewModel.setFirstDayOfWeek(it) },
        modifier = modifier,
    )
}

/**
 * 07 Bildirimler ve Hatırlatma Tercihleri Rotası.
 */
@Composable
fun NotificationsScreenRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasPermission = isGranted
    }

    NotificationsScreen(
        isSystemPermissionGranted = hasPermission,
        preferences = uiState.settings.notifications,
        onBack = onBack,
        onOpenSystemSettings = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
            }
        },
        onPreferencesChange = { newPrefs ->
            viewModel.updateNotificationPreferences(newPrefs)
        },
        modifier = modifier,
    )
}

/**
 * 09 Güvenlik ve Gizlilik Rotası.
 */
@Composable
fun SecurityPrivacyScreenRoute(
    appLockEnabled: Boolean,
    biometricAvailable: Boolean,
    currentTimeout: AutoLockTimeout,
    onAppLockChange: (Boolean) -> Unit,
    onTimeoutChange: (AutoLockTimeout) -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SecurityPrivacyScreen(
        biometricLockEnabled = appLockEnabled,
        biometricLockAvailable = biometricAvailable,
        autoLockTimeout = currentTimeout,
        hideAmounts = uiState.settings.notifications.hideAmountsInNotifications,
        onBack = onBack,
        onBiometricLockChange = onAppLockChange,
        onAutoLockTimeoutChange = onTimeoutChange,
        onHideAmountsChange = { viewModel.setHideAmountsInNotifications(it) },
        onNavigateToChangePassword = onNavigateToChangePassword,
        modifier = modifier,
    )
}

/**
 * 10 Veri Yönetimi Rotası.
 */
@Composable
fun DataManagementScreenRoute(
    onNavigateToSyncStatus: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingBackup by remember { mutableStateOf<PendingBackupImport?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshBackupScope()
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = runCatching {
                    val csv = viewModel.buildCsv()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            output.write(csv.toByteArray(Charsets.UTF_8))
                        } ?: error("output_stream_unavailable")
                    }
                }.isSuccess
                Toast.makeText(
                    context,
                    if (saved) "İşlemler CSV olarak kaydedildi." else "CSV dışa aktarılamadı.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val createJsonBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = viewModel.exportJsonBackup()) {
                    is BackupExportResult.Failure -> {
                        Toast.makeText(context, "Yedek oluşturulamadı: ${result.reason}", Toast.LENGTH_LONG).show()
                    }
                    is BackupExportResult.Success -> {
                        val written = runCatching {
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                                    output.write(result.json.toByteArray(Charsets.UTF_8))
                                } ?: error("output_stream_unavailable")
                            }
                        }.isSuccess
                        Toast.makeText(
                            context,
                            if (written) "JSON yedeği başarıyla kaydedildi (${result.scope.categoryCount} kategori, ${result.scope.transactionCount} işlem)."
                            else "Yedek dosyaya yazılamadı.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = runCatching {
                    withContext(Dispatchers.IO) { context.readBoundedUtf8(uri) }
                }.getOrElse {
                    Toast.makeText(context, "Yedek dosyası okunamadı veya çok büyük.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                when (val preview = viewModel.previewBackup(raw)) {
                    is BackupPreviewResult.Invalid -> Toast.makeText(
                        context,
                        backupFailureMessage(preview.reason),
                        Toast.LENGTH_LONG,
                    ).show()
                    is BackupPreviewResult.Valid -> pendingBackup = PendingBackupImport(
                        raw = raw,
                        categoryCount = preview.categoryCount,
                        transactionCount = preview.transactionCount,
                    )
                }
            }
        }
    }

    DataManagementScreen(
        categoryScopeCount = uiState.backupScope.categoryCount,
        transactionScopeCount = uiState.backupScope.transactionCount,
        pendingImport = pendingBackup?.let { PendingBackupInfo(it.categoryCount, it.transactionCount) },
        onBack = onBack,
        onExportCsv = { createCsvLauncher.launch("feniqo-islemler.csv") },
        onExportJsonBackup = { createJsonBackupLauncher.launch("feniqo-yedek.json") },
        onSelectBackupFile = { openBackupLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
        onConfirmImport = {
            val current = pendingBackup
            pendingBackup = null
            if (current != null) {
                scope.launch {
                    val message = when (val result = viewModel.importBackup(current.raw)) {
                        is BackupImportResult.Success ->
                            "${result.categoryCount} kategori ve ${result.transactionCount} işlem içe aktarıldı."
                        is BackupImportResult.Failure -> backupFailureMessage(result.reason)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    viewModel.refreshBackupScope()
                }
            }
        },
        onDismissImport = { pendingBackup = null },
        onNavigateToSyncStatus = onNavigateToSyncStatus,
        modifier = modifier,
    )
}

/**
 * 11 Senkronizasyon Durumu Rotası.
 */
@Composable
fun SyncStatusScreenRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    syncViewModel: SyncStatusViewModel = hiltViewModel(),
) {
    val syncState by syncViewModel.uiState.collectAsState()

    SyncStatusScreen(
        isOnline = syncState.connectionState == SyncConnectionUiState.ONLINE,
        isSyncing = syncState.isSyncing,
        pendingChangesCount = syncState.pendingCount,
        lastSyncFormatted = syncState.lastSuccessfulSyncAtEpochMillis?.let { epochMillis ->
            java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(epochMillis))
        } ?: "Henüz eşitlenmedi",
        conflictCount = syncState.conflictCount,
        onBack = onBack,
        onRetrySync = { syncViewModel.requestManualSync() },
        onInspectConflict = { syncViewModel.openConflictDialog() },
        modifier = modifier,
    )

    // Çakışma çözüm diyaloğu
    val activeDialog = syncState.activeConflictDialog
    if (activeDialog != null) {
        com.feniqo.mobile.presentation.component.WorkspaceConflictResolutionDialog(
            dialogState = activeDialog,
            onResolve = { resolution -> syncViewModel.resolveWorkspaceConflict(resolution) },
            onDismiss = { syncViewModel.dismissConflictDialog() },
        )
    }
}

/**
 * A2 Fotoğraf Önizleme ve Kırpma Rotası.
 */
@Composable
fun PhotoPreviewScreenRoute(
    draftFileName: String,
    onBack: () -> Unit,
    onReselect: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    avatarManager: UserAvatarManager = run {
        val context = LocalContext.current.applicationContext
        remember(context) { UserAvatarManager(context) }
    },
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val userId = uiState.profile?.id?.value.orEmpty()
    val effectiveUserId = userId.ifBlank { "current_user" }

    val draftFile = remember(draftFileName) {
        val safeName = draftFileName.substringAfterLast("/").substringAfterLast("\\")
        File(File(context.cacheDir, "camera"), safeName)
    }

    val sourceBitmap = remember(draftFile) {
        if (draftFile.exists() && draftFile.length() > 0) {
            decodeBoundedAndRotatedBitmap(draftFile)
        } else {
            null
        }
    }

    LaunchedEffect(sourceBitmap) {
        if (sourceBitmap == null) {
            Toast.makeText(context, "Görsel yüklenemedi veya bozuk.", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    val imageBitmap = remember(sourceBitmap) {
        sourceBitmap?.asImageBitmap()
    }

    var isSaving by remember { mutableStateOf(false) }

    PhotoPreviewScreen(
        imageBitmap = imageBitmap,
        onBack = onBack,
        onReselect = {
            draftFile.delete()
            onReselect()
        },
        onSavePhoto = { zoom, panX, panY ->
            if (isSaving) return@PhotoPreviewScreen
            val bitmap = sourceBitmap
            if (bitmap == null || bitmap.isRecycled) return@PhotoPreviewScreen
            isSaving = true

            runCatching {
                val cropped = cropSquareAvatarBitmap(bitmap, zoom, panX, panY)
                val saved = avatarManager.saveAvatarBitmap(effectiveUserId, cropped)
                if (saved) {
                    draftFile.delete()
                    Toast.makeText(context, "Profil fotoğrafı kaydedildi.", Toast.LENGTH_SHORT).show()
                    onSaveSuccess()
                } else {
                    isSaving = false
                    Toast.makeText(context, "Fotoğraf kaydedilemedi. Lütfen tekrar deneyin.", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                isSaving = false
                Toast.makeText(context, "Fotoğraf işlenirken hata oluştu.", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier,
    )
}

/**
 * 12 Yardım ve Hakkında Rotası.
 */
@Composable
fun HelpAboutScreenRoute(
    onNavigateToFeedback: (isBugReport: Boolean) -> Unit,
    onNavigateToHelpCenter: () -> Unit,
    onNavigateToLegalInfo: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }

    HelpAboutScreen(
        appVersion = "Sürüm $versionName",
        onBack = onBack,
        onNavigateToFeedback = onNavigateToFeedback,
        onOpenHelpCenter = onNavigateToHelpCenter,
        onOpenPrivacyPolicy = onNavigateToLegalInfo,
        onOpenTermsOfService = onNavigateToLegalInfo,
        onOpenOpenSourceLicenses = onNavigateToLegalInfo,
        modifier = modifier,
    )
}

/**
 * A3 E-posta Durum ve Yönetim Rotası.
 */
@Composable
fun EmailSettingsScreenRoute(
    onNavigateToChangeEmail: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentEmail = uiState.displayEmail

    LaunchedEffect(Unit) {
        viewModel.refreshEmailVerificationStatus()
    }

    EmailSettingsScreen(
        currentEmail = currentEmail,
        verificationStatus = uiState.emailVerificationStatus,
        isSendingVerification = uiState.isSendingEmailVerification,
        verificationSentSuccess = uiState.emailVerificationSent,
        errorMessage = uiState.emailError,
        onBack = onBack,
        onResendVerification = { viewModel.resendEmailVerification(currentEmail) },
        onNavigateToChangeEmail = onNavigateToChangeEmail,
        modifier = modifier,
    )
}

/**
 * 13 E-posta Değiştirme Rotası.
 */
@Composable
fun ChangeEmailScreenRoute(
    onVerificationSent: (newEmail: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.emailVerificationSent) {
        if (uiState.emailVerificationSent) {
            viewModel.clearEmailStatus()
        }
    }

    ChangeEmailScreen(
        currentEmail = uiState.displayEmail,
        isLoading = uiState.isSendingEmailVerification,
        errorMessage = uiState.emailError,
        onSubmitNewEmail = { newEmail ->
            viewModel.requestEmailChange(newEmail) {
                onVerificationSent(newEmail)
            }
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * 14 E-posta Doğrulaması Gönderildi Rotası.
 */
@Composable
fun EmailVerificationScreenRoute(
    newEmail: String,
    onBackToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    EmailVerificationScreen(
        targetEmail = newEmail,
        onBackToSettings = onBackToSettings,
        onResendEmail = {
            viewModel.requestEmailChange(newEmail)
            Toast.makeText(context, "Doğrulama bağlantısı tekrar gönderildi.", Toast.LENGTH_SHORT).show()
        },
        onCorrectAddress = onBackToSettings,
        modifier = modifier,
    )
}

/**
 * 15 Parola Değiştirme Rotası.
 */
@Composable
fun ChangePasswordScreenRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.passwordSuccess) {
        if (uiState.passwordSuccess) {
            Toast.makeText(context, "Parolanız başarıyla güncellendi.", Toast.LENGTH_SHORT).show()
            viewModel.clearPasswordStatus()
            onBack()
        }
    }

    ChangePasswordScreen(
        isLoading = uiState.isChangingPassword,
        errorMessage = uiState.passwordError,
        onSubmit = { old, new, confirm -> viewModel.changePassword(old, new, confirm) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Pano A4: Hesap Silme Hazırlık Rotası.
 */
@Composable
fun DeleteAccountScreenRoute(
    onExportData: () -> Unit,
    onCheckWorkspaces: () -> Unit,
    onInspectSync: () -> Unit,
    onContactSupport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DeleteAccountScreen(
        onBack = onBack,
        onExportData = onExportData,
        onCheckSharedSpaces = onCheckWorkspaces,
        onInspectSync = onInspectSync,
        onContactSupport = onContactSupport,
        modifier = modifier,
    )
}

/**
 * Pano B1: Yardım Merkezi Rotası.
 */
@Composable
fun HelpCenterScreenRoute(
    onNavigateToArticle: (articleTitle: String) -> Unit,
    onNavigateToFeedback: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HelpCenterScreen(
        onBack = onBack,
        onArticleClick = onNavigateToArticle,
        onNavigateToFeedback = onNavigateToFeedback,
        modifier = modifier,
    )
}

/**
 * Pano B2: Hazırlanan Makale Durumu Rotası.
 */
@Composable
fun HelpArticleStatusScreenRoute(
    articleTitle: String,
    onNavigateToFeedback: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HelpArticleStatusScreen(
        articleTitle = articleTitle,
        onBack = onBack,
        onNavigateToFeedback = onNavigateToFeedback,
        modifier = modifier,
    )
}

/**
 * Pano B3: Gizlilik ve Hukuki Bilgiler Rotası.
 */
@Composable
fun LegalInfoScreenRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LegalInfoScreen(
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Pano B4: Geri Bildirim Rotası.
 */
@Composable
fun FeedbackScreenRoute(
    initialIsBug: Boolean = false,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var attachmentName by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        attachmentUri = uri
        attachmentName = uri?.lastPathSegment ?: if (uri != null) "ekran_goruntusu.png" else null
    }

    FeedbackScreen(
        initialIsBug = initialIsBug,
        onBack = onBack,
        onShareFeedback = { isBug, subject, message ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (attachmentUri != null) "image/*" else "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "[Feniqo Geri Bildirim] ${if (isBug) "Hata" else "Öneri"}: $subject")
                putExtra(Intent.EXTRA_TEXT, message)
                attachmentUri?.let { uri ->
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            runCatching {
                context.startActivity(Intent.createChooser(intent, "Geri bildirimi paylaş"))
            }.onFailure {
                Toast.makeText(context, "Paylaşım yapabilecek uygun bir uygulama bulunamadı.", Toast.LENGTH_LONG).show()
            }
        },
        onPickAttachment = { filePickerLauncher.launch("image/*") },
        hasAttachment = attachmentUri != null,
        attachmentName = attachmentName,
        onRemoveAttachment = {
            attachmentUri = null
            attachmentName = null
        },
        modifier = modifier,
    )
}

/**
 * Android Katmanı: Güvenli ve OOM Korumalı Görsel Decode ve EXIF Düzeltmesi.
 */
private fun decodeBoundedAndRotatedBitmap(file: File, maxDimension: Int = 1280): Bitmap? {
    return runCatching {
        // 1. Görsel boyutlarını oku
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        // 2. inSampleSize hesapla (OOM koruması)
        var sampleSize = 1
        while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        // 3. Gerçek görseli decode et
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

        // 4. EXIF Orientation oku ve uygula
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val rotationAngle = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (rotationAngle != 0f) {
            val matrix = Matrix().apply { postRotate(rotationAngle) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }
    }.getOrNull()
}

/**
 * Android Katmanı: Önizleme parametrelerine göre dairesel avatar için kare kırpma.
 */
private fun cropSquareAvatarBitmap(
    source: Bitmap,
    zoom: Float,
    panX: Float,
    panY: Float,
    targetSize: Int = 512,
): Bitmap {
    val srcWidth = source.width
    val srcHeight = source.height
    val minDim = minOf(srcWidth, srcHeight)

    val effectiveZoom = zoom.coerceIn(1f, 4f)
    val cropSize = (minDim / effectiveZoom).toInt().coerceIn(32, minDim)

    // Pan oranını piksel ofsetine eşle
    val maxOffsetX = (srcWidth - cropSize) / 2f
    val maxOffsetY = (srcHeight - cropSize) / 2f

    val panRatioX = (panX / 250f).coerceIn(-1f, 1f)
    val panRatioY = (panY / 250f).coerceIn(-1f, 1f)

    val centerX = (srcWidth / 2f) - (panRatioX * maxOffsetX)
    val centerY = (srcHeight / 2f) - (panRatioY * maxOffsetY)

    val left = (centerX - (cropSize / 2f)).toInt().coerceIn(0, maxOf(0, srcWidth - cropSize))
    val top = (centerY - (cropSize / 2f)).toInt().coerceIn(0, maxOf(0, srcHeight - cropSize))

    val cropped = Bitmap.createBitmap(source, left, top, cropSize, cropSize)
    return if (cropSize != targetSize) {
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (scaled != cropped) cropped.recycle()
        scaled
    } else {
        cropped
    }
}

private data class PendingBackupImport(
    val raw: String,
    val categoryCount: Int,
    val transactionCount: Int,
)

private fun Context.readBoundedUtf8(uri: Uri): String {
    val input = contentResolver.openInputStream(uri) ?: error("input_stream_unavailable")
    return input.buffered().use { source ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            total += read
            if (total > FENIQO_BACKUP_MAX_BYTES) error("backup_too_large")
            output.write(buffer, 0, read)
        }
        output.toString(Charsets.UTF_8.name())
    }
}

private fun backupFailureMessage(reason: String): String = when (reason) {
    "backup_personal_scope_required" -> "İçe aktarmak için önce kişisel alana geçin."
    "auth_session_required" -> "İçe aktarmak için oturum açmanız gerekiyor."
    "backup_too_large" -> "Yedek dosyası 10 MiB sınırını aşıyor."
    "backup_version_unsupported" -> "Bu yedek sürümü desteklenmiyor."
    "backup_import_failed" -> "Yedek içe aktarılamadı; hiçbir kayıt eklenmedi."
    else -> "Geçersiz veya desteklenmeyen Feniqo yedek dosyası."
}

package com.feniqo.mobile.presentation.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.feniqo.mobile.domain.repository.AutoLockTimeout
import com.feniqo.mobile.data.backup.BackupImportResult
import com.feniqo.mobile.data.backup.FENIQO_BACKUP_MAX_BYTES
import com.feniqo.mobile.presentation.screen.ThemeSettingsPlaceholderScreen
import com.feniqo.mobile.presentation.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreenRoute(
    themeMode: ThemeMode,
    onThemeModeChange: suspend (ThemeMode) -> Unit,
    biometricLockEnabled: Boolean,
    biometricLockAvailable: Boolean,
    autoLockTimeout: AutoLockTimeout,
    onBiometricLockChange: (Boolean) -> Unit,
    onAutoLockTimeoutChange: (AutoLockTimeout) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionExportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingBackup by remember { mutableStateOf<PendingBackupImport?>(null) }
    val createDocument = rememberLauncherForActivityResult(
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
    val openBackupDocument = rememberLauncherForActivityResult(
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

    pendingBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingBackup = null },
            title = { Text("JSON yedeği içe aktarılsın mı?") },
            text = {
                Text(
                    "${backup.categoryCount} kategori ve ${backup.transactionCount} işlem kişisel alanınıza yeni kayıtlar olarak eklenecek. Mevcut kayıtlar değiştirilmeyecek.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingBackup = null
                    scope.launch {
                        val message = when (val result = viewModel.importBackup(backup.raw)) {
                            is BackupImportResult.Success ->
                                "${result.categoryCount} kategori ve ${result.transactionCount} işlem içe aktarıldı."
                            is BackupImportResult.Failure -> backupFailureMessage(result.reason)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }) { Text("İçe Aktar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBackup = null }) { Text("İptal") }
            },
        )
    }

    ThemeSettingsPlaceholderScreen(
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        biometricLockEnabled = biometricLockEnabled,
        biometricLockAvailable = biometricLockAvailable,
        autoLockTimeout = autoLockTimeout,
        onBiometricLockChange = onBiometricLockChange,
        onAutoLockTimeoutChange = onAutoLockTimeoutChange,
        onExportTransactions = { createDocument.launch("feniqo-islemler.csv") },
        onImportBackup = { openBackupDocument.launch(arrayOf("application/json", "text/json", "text/plain")) },
        modifier = modifier,
    )
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

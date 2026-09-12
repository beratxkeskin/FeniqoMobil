package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.feniqo.mobile.domain.repository.AutoLockTimeout
import com.feniqo.mobile.presentation.component.EmptyState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SplashLoadingScreen(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LoadingContent(message = "Feniqo yükleniyor…")
    }
}

@Composable
fun CategoriesPlaceholderScreen(
    modifier: Modifier = Modifier,
) {
    EmptyState(
        modifier = modifier.fillMaxSize(),
        title = "Henüz kategori yok",
        description = "Kategori yönetimi sonraki ürün adımlarında eklenecek.",
    )
}

@Composable
fun ThemeSettingsPlaceholderScreen(
    themeMode: ThemeMode,
    onThemeModeChange: suspend (ThemeMode) -> Unit,
    biometricLockEnabled: Boolean = false,
    biometricLockAvailable: Boolean = false,
    autoLockTimeout: AutoLockTimeout = AutoLockTimeout.AFTER_1_MINUTE,
    onBiometricLockChange: (Boolean) -> Unit = {},
    onAutoLockTimeoutChange: (AutoLockTimeout) -> Unit = {},
    onExportTransactions: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FeniqoSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
    ) {
        Text("Görünüm", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Tema tercihin uygulama yeniden açıldığında korunur.",
            style = MaterialTheme.typography.bodyMedium,
        )

        ThemeMode.entries.forEach { mode ->
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { coroutineScope.launch { onThemeModeChange(mode) } },
                enabled = mode != themeMode,
            ) {
                Text(
                    when (mode) {
                        ThemeMode.SYSTEM -> "Sistem teması"
                        ThemeMode.LIGHT -> "Açık tema"
                        ThemeMode.DARK -> "Koyu tema"
                    },
                )
            }
        }

        Text("Veriler", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Aktif alandaki işlemleri, makbuz yolu ve kullanıcı kimliği olmadan CSV dosyasına aktarın.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(modifier = Modifier.fillMaxWidth(), onClick = onExportTransactions) {
            Text("İşlemleri CSV Olarak Dışa Aktar")
        }
        Text(
            "Feniqo JSON yedeğini kişisel alana yeni kopyalar olarak aktarın. Dosya doğrulandıktan sonra sizden onay istenir.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(modifier = Modifier.fillMaxWidth(), onClick = onImportBackup) {
            Text("JSON Yedeğini İçe Aktar")
        }

        Text("Güvenlik", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (biometricLockAvailable) {
                "Uygulamayı biyometri veya cihaz ekran kilidiyle koruyun."
            } else {
                "Güvenli sistem doğrulaması bu cihazda kullanılamıyor veya ayarlanmamış."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = biometricLockEnabled,
            onCheckedChange = onBiometricLockChange,
            enabled = biometricLockAvailable || biometricLockEnabled,
        )
        if (biometricLockEnabled) {
            Text("Otomatik kilit süresi", style = MaterialTheme.typography.titleMedium)
            AutoLockTimeout.entries.forEach { timeout ->
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onAutoLockTimeoutChange(timeout) },
                    enabled = timeout != autoLockTimeout,
                ) {
                    Text(timeout.label())
                }
            }
        }

    }
}

private fun AutoLockTimeout.label(): String = when (this) {
    AutoLockTimeout.IMMEDIATELY -> "Anında"
    AutoLockTimeout.AFTER_30_SECONDS -> "30 saniye sonra"
    AutoLockTimeout.AFTER_1_MINUTE -> "1 dakika sonra"
    AutoLockTimeout.AFTER_5_MINUTES -> "5 dakika sonra"
}

@Composable
fun AppLockScreen(
    errorMessage: String?,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(FeniqoSpacing.Screen),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Feniqo kilitli", style = MaterialTheme.typography.headlineMedium)
            Text(
                errorMessage ?: "Devam etmek için biyometri veya cihaz ekran kilidinizle doğrulayın.",
                modifier = Modifier.padding(top = FeniqoSpacing.Medium),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FeniqoSpacing.Large),
                onClick = onUnlock,
            ) {
                Text("Kilidi Aç")
            }
        }
    }
}

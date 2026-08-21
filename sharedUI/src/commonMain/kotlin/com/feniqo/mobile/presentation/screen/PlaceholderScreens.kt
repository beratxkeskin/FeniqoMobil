package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun DashboardPlaceholderScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(FeniqoSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
    ) {
        Text("Feniqo", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Finansal özetin burada yer alacak.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun TransactionsPlaceholderScreen(
    modifier: Modifier = Modifier,
) {
    EmptyState(
        modifier = modifier.fillMaxSize(),
        title = "Henüz işlem yok",
        description = "İşlem listesi ve ekleme akışı sonraki ürün adımlarında eklenecek.",
    )
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
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
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
    }
}

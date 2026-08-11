package com.feniqo.mobile.presentation.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.feniqo.mobile.presentation.component.EmptyState
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.ThemeMode
import kotlinx.coroutines.launch

private enum class AppSection(val label: String, val symbol: String) {
    DASHBOARD("Özet", "⌂"),
    TRANSACTIONS("İşlemler", "↕"),
    SETTINGS("Ayarlar", "⚙"),
}

/**
 * V1 için uygulamanın görsel kabuğu.
 * Gerçek type-safe Navigation Compose rotaları 7.1 adımında bu yerel seçimi devralacaktır.
 */
@Composable
fun FeniqoAppShell(
    themeMode: ThemeMode,
    onThemeModeChange: suspend (ThemeMode) -> Unit,
) {
    var selectedSection by remember { mutableStateOf(AppSection.DASHBOARD) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppSection.entries.forEach { section ->
                    NavigationBarItem(
                        selected = section == selectedSection,
                        onClick = { selectedSection = section },
                        icon = { Text(section.symbol) },
                        label = { Text(section.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        when (selectedSection) {
            AppSection.DASHBOARD -> DashboardPlaceholder(paddingValues)
            AppSection.TRANSACTIONS -> TransactionsPlaceholder(paddingValues)
            AppSection.SETTINGS -> ThemeSettings(
                paddingValues = paddingValues,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
    }
}

@Composable
private fun DashboardPlaceholder(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
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
private fun TransactionsPlaceholder(paddingValues: PaddingValues) {
    EmptyState(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        title = "Henüz işlem yok",
        description = "İşlem listesi ve ekleme akışı sonraki ürün adımlarında eklenecek.",
    )
}

@Composable
private fun ThemeSettings(
    paddingValues: PaddingValues,
    themeMode: ThemeMode,
    onThemeModeChange: suspend (ThemeMode) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(FeniqoSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
    ) {
        Text("Görünüm", style = MaterialTheme.typography.headlineMedium)
        Text("Tema tercihin uygulama yeniden açıldığında korunur.")

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

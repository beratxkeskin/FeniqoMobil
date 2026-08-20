package com.feniqo.mobile.presentation.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.feniqo.mobile.presentation.component.SyncStatusIndicator
import com.feniqo.mobile.presentation.sync.SyncStatusUiState

enum class AppSection(val label: String, val symbol: String) {
    DASHBOARD("Özet", "⌂"),
    TRANSACTIONS("İşlemler", "↕"),
    CATEGORIES("Kategoriler", "⊞"),
    SETTINGS("Ayarlar", "⚙"),
}

/**
 * Platformdan bağımsız, durumsuz (stateless) uygulama ana kabuğudur.
 * Navigation durumunu barındırmaz; seçili sekmeyi ve içerik Composable slot'unu dışarıdan alır.
 * Scaffold padding'i doğrudan ana Column üzerine uygulanır.
 */
@Composable
fun FeniqoAppShell(
    selectedSection: AppSection,
    onSectionSelect: (AppSection) -> Unit,
    syncStatus: SyncStatusUiState = SyncStatusUiState.Initial,
    onManualSync: () -> Unit = {},
    onRetryFailed: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                AppSection.entries.forEach { section ->
                    NavigationBarItem(
                        selected = section == selectedSection,
                        onClick = { onSectionSelect(section) },
                        icon = { Text(section.symbol) },
                        label = { Text(section.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            SyncStatusIndicator(
                uiState = syncStatus,
                onManualSync = onManualSync,
                onRetryFailed = onRetryFailed,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                content()
            }
        }
    }
}

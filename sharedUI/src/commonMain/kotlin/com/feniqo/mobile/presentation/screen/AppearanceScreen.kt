package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.presentation.component.AmountMaskingSection
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.component.ThemeSelectionCard
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

/**
 * 04 Görünüm Ekranı.
 * Sistem, Açık ve Koyu tema seçenekleri ile tutarları gizleme tercihi ve önizleme kutusu.
 */
@Composable
fun AppearanceScreen(
    currentTheme: ThemePreference,
    maskAmounts: Boolean,
    onBack: () -> Unit,
    onThemeSelect: (ThemePreference) -> Unit,
    onMaskAmountsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = FeniqoSpacing.Screen,
                vertical = FeniqoSpacing.Medium,
            ),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
        ) {
            item {
                SettingsTopBar(
                    title = "Görünüm",
                    onBack = onBack,
                )
            }

            item {
                Column {
                    Text(
                        text = "Uygulama temasını seç",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cihazının görünümüne en uygun temayı belirle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 3'lü Tema Kartları
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    ThemeSelectionCard(
                        themePreference = ThemePreference.SYSTEM,
                        currentSelection = currentTheme,
                        title = "Sistem",
                        subtitle = "Cihaz temasını kullanır.",
                        onSelect = onThemeSelect,
                        modifier = Modifier.weight(1f),
                    )
                    ThemeSelectionCard(
                        themePreference = ThemePreference.LIGHT,
                        currentSelection = currentTheme,
                        title = "Açık",
                        subtitle = "Her zaman açık tema.",
                        onSelect = onThemeSelect,
                        modifier = Modifier.weight(1f),
                    )
                    ThemeSelectionCard(
                        themePreference = ThemePreference.DARK,
                        currentSelection = currentTheme,
                        title = "Koyu",
                        subtitle = "Her zaman koyu tema.",
                        onSelect = onThemeSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Tutarları Gizle ve Önizleme
            item {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                AmountMaskingSection(
                    maskAmounts = maskAmounts,
                    onMaskChange = onMaskAmountsChange,
                )
            }
        }
    }
}

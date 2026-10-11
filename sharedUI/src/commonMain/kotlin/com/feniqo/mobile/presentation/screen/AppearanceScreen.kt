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

import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.appearance_theme_section_subtitle
import feniqomobil.sharedui.generated.resources.appearance_theme_section_title
import feniqomobil.sharedui.generated.resources.appearance_title
import com.feniqo.mobile.presentation.settings.toSubtitleResource
import com.feniqo.mobile.presentation.settings.toTitleResource
import org.jetbrains.compose.resources.stringResource

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
                    title = stringResource(Res.string.appearance_title),
                    onBack = onBack,
                )
            }

            item {
                Column {
                    Text(
                        text = stringResource(Res.string.appearance_theme_section_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.appearance_theme_section_subtitle),
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
                    ThemePreference.entries.forEach { pref ->
                        ThemeSelectionCard(
                            themePreference = pref,
                            currentSelection = currentTheme,
                            title = stringResource(pref.toTitleResource()),
                            subtitle = stringResource(pref.toSubtitleResource()),
                            onSelect = onThemeSelect,
                            modifier = Modifier.weight(1f),
                        )
                    }
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

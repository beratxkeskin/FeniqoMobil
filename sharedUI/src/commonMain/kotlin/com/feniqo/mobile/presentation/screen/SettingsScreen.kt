package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 01 Ayarlar Ana Ekranı.
 * Uygulama tercihleri, güvenlik, veri ve yardım araçlarının tek sahibi olan ayarlar ekranı.
 */
@Composable
fun SettingsScreen(
    themeLabel: String,
    languageRegionLabel: String,
    onBack: (() -> Unit)?,
    onNavigateToProfile: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLanguageRegion: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToDataManagement: () -> Unit,
    onNavigateToHelpAbout: () -> Unit,
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
                    title = stringResource(Res.string.settings_title),
                    onBack = onBack,
                )
            }

            // Tercihler Grubu
            item {
                Text(
                    text = stringResource(Res.string.settings_section_preferences),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = stringResource(Res.string.settings_appearance_title),
                        subtitle = themeLabel,
                        icon = Icons.Outlined.LightMode,
                        iconTint = FeniqoSageGreen,
                        onClick = onNavigateToAppearance,
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.settings_language_region_title),
                        subtitle = languageRegionLabel,
                        icon = Icons.Outlined.Language,
                        iconTint = Color(0xFF2E7D32),
                        onClick = onNavigateToLanguageRegion,
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.settings_notifications_title),
                        subtitle = stringResource(Res.string.settings_notifications_subtitle),
                        icon = Icons.Outlined.Notifications,
                        iconTint = Color(0xFF1565C0),
                        showDivider = false,
                        onClick = onNavigateToNotifications,
                    )
                }
            }

            // Kontrol Grubu
            item {
                Text(
                    text = stringResource(Res.string.settings_section_control),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = stringResource(Res.string.settings_security_title),
                        subtitle = stringResource(Res.string.settings_security_subtitle),
                        icon = Icons.Outlined.Lock,
                        iconTint = FeniqoSageGreen,
                        onClick = onNavigateToSecurity,
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.settings_data_title),
                        subtitle = stringResource(Res.string.settings_data_subtitle),
                        icon = Icons.Outlined.FolderCopy,
                        iconTint = Color(0xFF00838F),
                        onClick = onNavigateToDataManagement,
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.settings_help_title),
                        subtitle = stringResource(Res.string.settings_help_subtitle),
                        icon = Icons.Outlined.HelpOutline,
                        iconTint = Color(0xFF6A1B9A),
                        showDivider = false,
                        onClick = onNavigateToHelpAbout,
                    )
                }
            }

            // Hesap ve oturum işlemlerinin tek sahibi Profil Merkezi'dir.
            item {
                Text(
                    text = stringResource(Res.string.settings_section_account),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = stringResource(Res.string.settings_account_title),
                        subtitle = stringResource(Res.string.settings_account_subtitle),
                        icon = Icons.Outlined.AccountCircle,
                        iconTint = MaterialTheme.colorScheme.primary,
                        showDivider = false,
                        onClick = onNavigateToProfile,
                    )
                }
            }
        }
    }
}

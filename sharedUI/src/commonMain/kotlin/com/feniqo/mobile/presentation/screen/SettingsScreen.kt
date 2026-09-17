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
import com.feniqo.mobile.presentation.component.SettingsDangerItem
import com.feniqo.mobile.presentation.component.SettingsGraphiteProfileCard
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

/**
 * 01 Ayarlar Ana Ekranı.
 * Onaylı tasarım 01'e sadık, kompakt grafit profil kartı, Tercihler ve Kontrol grupları.
 */
@Composable
fun SettingsScreen(
    displayName: String,
    email: String,
    themeLabel: String,
    languageRegionLabel: String,
    onBack: (() -> Unit)?,
    onManageProfile: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLanguageRegion: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToDataManagement: () -> Unit,
    onNavigateToHelpAbout: () -> Unit,
    onSignOutClick: () -> Unit,
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
                    title = "Ayarlar",
                    onBack = onBack,
                )
            }

            // Üst kompakt grafit profil kartı
            item {
                SettingsGraphiteProfileCard(
                    displayName = displayName,
                    email = email,
                    onManageProfile = onManageProfile,
                )
            }

            // Tercihler Grubu
            item {
                Text(
                    text = "Tercihler",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Görünüm",
                        subtitle = themeLabel,
                        icon = Icons.Outlined.LightMode,
                        iconTint = FeniqoSageGreen,
                        onClick = onNavigateToAppearance,
                    )
                    SettingsRowItem(
                        title = "Dil ve bölge",
                        subtitle = languageRegionLabel,
                        icon = Icons.Outlined.Language,
                        iconTint = Color(0xFF2E7D32),
                        onClick = onNavigateToLanguageRegion,
                    )
                    SettingsRowItem(
                        title = "Bildirimler",
                        subtitle = "Hatırlatmalar ve uyarılar",
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
                    text = "Kontrol",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Güvenlik ve gizlilik",
                        subtitle = "Uygulama kilidi ve koruma",
                        icon = Icons.Outlined.Lock,
                        iconTint = FeniqoSageGreen,
                        onClick = onNavigateToSecurity,
                    )
                    SettingsRowItem(
                        title = "Veri yönetimi",
                        subtitle = "Dışa aktar, yedekle ve eşitle",
                        icon = Icons.Outlined.FolderCopy,
                        iconTint = Color(0xFF00838F),
                        onClick = onNavigateToDataManagement,
                    )
                    SettingsRowItem(
                        title = "Yardım ve hakkında",
                        subtitle = "Destek, lisanslar ve sürüm",
                        icon = Icons.Outlined.HelpOutline,
                        iconTint = Color(0xFF6A1B9A),
                        showDivider = false,
                        onClick = onNavigateToHelpAbout,
                    )
                }
            }

            // Ayrı Çıkış Butonu
            item {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                SettingsDangerItem(
                    title = "Çıkış yap",
                    icon = Icons.Outlined.Logout,
                    onClick = onSignOutClick,
                )
            }
        }
    }
}

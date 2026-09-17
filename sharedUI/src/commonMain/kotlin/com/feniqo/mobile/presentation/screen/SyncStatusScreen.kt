package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.component.ConflictAlertCard
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.component.SyncStatusHeaderCard
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget

/**
 * 11 Senkronizasyon Durum Ekranı.
 * Gerçek bağlantı durumu, bekleyen değişiklik sayısı, son başarılı eşitleme zamanı ve çakışma inceleme.
 */
@Composable
fun SyncStatusScreen(
    isOnline: Boolean,
    isSyncing: Boolean,
    pendingChangesCount: Int,
    lastSyncFormatted: String,
    conflictCount: Int,
    onBack: () -> Unit,
    onRetrySync: () -> Unit,
    onInspectConflict: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Screen, vertical = FeniqoSpacing.Medium),
        ) {
            SettingsTopBar(
                title = "Senkronizasyon",
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
            ) {
                // Grafit Durum Kartı
                item {
                    SyncStatusHeaderCard(
                        isOnline = isOnline,
                        isSyncing = isSyncing,
                    )
                }

                // Sayı ve Zaman Satırları
                item {
                    SettingsGroupCard {
                        SettingsRowItem(
                            title = "Bekleyen değişiklikler",
                            subtitle = if (pendingChangesCount > 0) "$pendingChangesCount yerel değişiklik aktarılmayı bekliyor" else "Bekleyen değişiklik yok",
                            icon = Icons.Outlined.FormatListNumbered,
                            badgeText = pendingChangesCount.toString(),
                            badgeColor = if (pendingChangesCount > 0) Color(0xFFD97706) else FeniqoSageGreen,
                            onClick = null,
                            showChevron = false,
                        )
                        SettingsRowItem(
                            title = "Son başarılı eşitleme",
                            subtitle = lastSyncFormatted.ifBlank { "Henüz eşitlenmedi" },
                            icon = Icons.Outlined.Schedule,
                            showDivider = false,
                            onClick = null,
                            showChevron = false,
                        )
                    }
                }

                // Çakışma Kartı
                item {
                    ConflictAlertCard(
                        conflictCount = conflictCount,
                        onInspectConflict = onInspectConflict,
                    )
                }

                // Bilgi Notu
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Çakışan kayıtlar kararın olmadan değiştirilmez.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Yeniden Dene Butonu
            Button(
                onClick = onRetrySync,
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FeniqoTouchTarget.PrimaryAction),
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Outlined.Sync, null)
                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                    Text("Yeniden dene", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

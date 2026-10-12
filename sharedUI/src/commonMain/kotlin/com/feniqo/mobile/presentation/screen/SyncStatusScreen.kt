package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
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
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.sync_conflict_info_note
import feniqomobil.sharedui.generated.resources.sync_last_sync_title
import feniqomobil.sharedui.generated.resources.sync_legacy_quarantine_body
import feniqomobil.sharedui.generated.resources.sync_legacy_quarantine_title
import feniqomobil.sharedui.generated.resources.sync_never_synced
import feniqomobil.sharedui.generated.resources.sync_no_pending_changes
import feniqomobil.sharedui.generated.resources.sync_pending_changes_plural
import feniqomobil.sharedui.generated.resources.sync_pending_changes_title
import feniqomobil.sharedui.generated.resources.sync_retry_button
import feniqomobil.sharedui.generated.resources.sync_status_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
    hasLegacyQuarantinedData: Boolean,
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
                title = stringResource(Res.string.sync_status_title),
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

                // Karantina Uyarısı (yalnızca hasLegacyQuarantinedData true iken görünür)
                if (hasLegacyQuarantinedData) {
                    item {
                        LegacyQuarantineWarningCard()
                    }
                }

                // Sayı ve Zaman Satırları
                item {
                    SettingsGroupCard {
                        SettingsRowItem(
                            title = stringResource(Res.string.sync_pending_changes_title),
                            subtitle = if (pendingChangesCount > 0) {
                                pluralStringResource(
                                    Res.plurals.sync_pending_changes_plural,
                                    pendingChangesCount,
                                    pendingChangesCount,
                                )
                            } else {
                                stringResource(Res.string.sync_no_pending_changes)
                            },
                            icon = Icons.Outlined.FormatListNumbered,
                            badgeText = pendingChangesCount.toString(),
                            badgeColor = if (pendingChangesCount > 0) Color(0xFFD97706) else FeniqoSageGreen,
                            onClick = null,
                            showChevron = false,
                        )
                        SettingsRowItem(
                            title = stringResource(Res.string.sync_last_sync_title),
                            subtitle = lastSyncFormatted.ifBlank { stringResource(Res.string.sync_never_synced) },
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
                            text = stringResource(Res.string.sync_conflict_info_note),
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
                    Text(stringResource(Res.string.sync_retry_button), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Fail-closed tutulan eski karantina kayıtları için genel bilgilendirme kartı.
 * Herhangi bir mutation aksiyonu içermez ve exact metadata göstermez.
 */
@Composable
fun LegacyQuarantineWarningCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.sync_legacy_quarantine_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.sync_legacy_quarantine_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

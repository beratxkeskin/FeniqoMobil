package com.feniqo.mobile.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.NotificationPreferences
import com.feniqo.mobile.domain.model.ReminderTimeOffset
import com.feniqo.mobile.presentation.component.NotificationPermissionCard
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget

/**
 * 07 Bildirimler Ekranı, 08 Hatırlatma Tercihleri ve 21 Bildirim İzni Kapalı Durumu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    isSystemPermissionGranted: Boolean,
    preferences: NotificationPreferences,
    onBack: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onPreferencesChange: (NotificationPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showReminderPreferencesSheet by remember { mutableStateOf(false) }

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
                    title = "Bildirimler",
                    onBack = onBack,
                )
            }

            // 07 & 21 Sistem İzni Durum Kartı
            item {
                NotificationPermissionCard(
                    isPermissionGranted = isSystemPermissionGranted,
                    onOpenSettings = onOpenSystemSettings,
                )
            }

            // Ana Bildirim Açma/Kapama Switch'i
            item {
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Bildirimlere izin ver",
                        subtitle = if (preferences.enabled) "Uygulama içi bildirimler açık" else "Tüm bildirimler susturuldu",
                        icon = Icons.Outlined.NotificationsActive,
                        trailingContent = {
                            Switch(
                                checked = preferences.enabled,
                                onCheckedChange = { onPreferencesChange(preferences.copy(enabled = it)) },
                                colors = SwitchDefaults.colors(checkedTrackColor = FeniqoSageGreen),
                            )
                        },
                        showDivider = false,
                    )
                }
            }

            // Kategori Switch'leri
            item {
                Text(
                    text = "Bildirim kategorileri",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Borç ve alacak vadeleri",
                        subtitle = "Yaklaşan ödeme ve tahsilat hatırlatmaları",
                        icon = Icons.Outlined.AccountBalance,
                        trailingContent = {
                            Switch(
                                checked = preferences.remindDebts && preferences.enabled,
                                onCheckedChange = { onPreferencesChange(preferences.copy(remindDebts = it)) },
                                enabled = preferences.enabled,
                                colors = SwitchDefaults.colors(checkedTrackColor = FeniqoSageGreen),
                            )
                        },
                    )
                    SettingsRowItem(
                        title = "Abonelik yenilemeleri",
                        subtitle = "Yenilenme tarihi yaklaşan servisler",
                        icon = Icons.Outlined.EventRepeat,
                        trailingContent = {
                            Switch(
                                checked = preferences.remindSubscriptions && preferences.enabled,
                                onCheckedChange = { onPreferencesChange(preferences.copy(remindSubscriptions = it)) },
                                enabled = preferences.enabled,
                                colors = SwitchDefaults.colors(checkedTrackColor = FeniqoSageGreen),
                            )
                        },
                    )
                    SettingsRowItem(
                        title = "Tekrarlayan işlemler",
                        subtitle = "Planlanmış periyodik gelir ve giderler",
                        icon = Icons.Outlined.Autorenew,
                        trailingContent = {
                            Switch(
                                checked = preferences.remindRecurring && preferences.enabled,
                                onCheckedChange = { onPreferencesChange(preferences.copy(remindRecurring = it)) },
                                enabled = preferences.enabled,
                                colors = SwitchDefaults.colors(checkedTrackColor = FeniqoSageGreen),
                            )
                        },
                    )
                    SettingsRowItem(
                        title = "Bütçe uyarıları",
                        subtitle = "%80 limit yaklaşımı ve aşım bildirimleri",
                        icon = Icons.Outlined.Assessment,
                        trailingContent = {
                            Switch(
                                checked = preferences.remindBudgets && preferences.enabled,
                                onCheckedChange = { onPreferencesChange(preferences.copy(remindBudgets = it)) },
                                enabled = preferences.enabled,
                                colors = SwitchDefaults.colors(checkedTrackColor = FeniqoSageGreen),
                            )
                        },
                        showDivider = false,
                    )
                }
            }

            // Zaman ve Gizlilik Tercihleri
            item {
                Text(
                    text = "Zamanlama ve Gizlilik",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    val timeString = "${preferences.reminderOffset.label} • ${preferences.reminderHour.toString().padStart(2, '0')}:${preferences.reminderMinute.toString().padStart(2, '0')}"
                    SettingsRowItem(
                        title = "Hatırlatma zamanı",
                        subtitle = timeString,
                        icon = Icons.Outlined.Schedule,
                        onClick = { showReminderPreferencesSheet = true },
                    )
                    val quietString = if (preferences.quietHoursEnabled) {
                        "${preferences.quietHoursStartHour.toString().padStart(2, '0')}:${preferences.quietHoursStartMinute.toString().padStart(2, '0')} – ${preferences.quietHoursEndHour.toString().padStart(2, '0')}:${preferences.quietHoursEndMinute.toString().padStart(2, '0')}"
                    } else {
                        "Kapalı"
                    }
                    SettingsRowItem(
                        title = "Sessiz saatler",
                        subtitle = quietString,
                        icon = Icons.Outlined.Bedtime,
                        onClick = { showReminderPreferencesSheet = true },
                    )
                    SettingsRowItem(
                        title = "Bildirimlerde tutarları gizle",
                        subtitle = "Kilit ekranı ve bildirimlerde bakiyeleri maskele",
                        icon = Icons.Outlined.VisibilityOff,
                        trailingContent = {
                            Switch(
                                checked = preferences.hideAmountsInNotifications,
                                onCheckedChange = { onPreferencesChange(preferences.copy(hideAmountsInNotifications = it)) },
                                colors = SwitchDefaults.colors(checkedTrackColor = FeniqoSageGreen),
                            )
                        },
                        showDivider = false,
                    )
                }
            }
        }
    }

    // 08 Hatırlatma Tercihleri Bottom Sheet
    if (showReminderPreferencesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReminderPreferencesSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            var offset by remember { mutableStateOf(preferences.reminderOffset) }
            var hour by remember { mutableStateOf(preferences.reminderHour) }
            var minute by remember { mutableStateOf(preferences.reminderMinute) }
            var quietEnabled by remember { mutableStateOf(preferences.quietHoursEnabled) }
            var quietStartHour by remember { mutableStateOf(preferences.quietHoursStartHour) }
            var quietEndHour by remember { mutableStateOf(preferences.quietHoursEndHour) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                Text(
                    text = "Hatırlatma tercihleri",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )

                // Hatırlatma Zamanı Çipleri
                Text("Hatırlatma zamanı", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    ReminderTimeOffset.entries.forEach { opt ->
                        val selected = offset == opt
                        FilterChip(
                            selected = selected,
                            onClick = { offset = opt },
                            label = { Text(opt.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FeniqoSageGreen,
                                selectedLabelColor = Color.White,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Saat Seçimi
                SettingsGroupCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FeniqoSpacing.Large),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AccessTime, null, tint = FeniqoSageGreen)
                        Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                        Text(
                            text = "Bildirim saati:",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { hour = (hour + 1) % 24 }) {
                                Text(
                                    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = FeniqoSageGreen,
                                )
                            }
                        }
                    }
                }

                // Sessiz Saatler
                Text("Sessiz saatler", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                SettingsGroupCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FeniqoSpacing.Large),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sessiz saatleri etkinleştir", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                            Text("Bu aralıkta bildirimler sessiz saatlerin sonuna ertelenir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = quietEnabled,
                            onCheckedChange = { quietEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = FeniqoSageGreen),
                        )
                    }
                    if (quietEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(FeniqoSpacing.Large),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("Başlangıç", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { quietStartHour = (quietStartHour + 1) % 24 }) {
                                    Text("${quietStartHour.toString().padStart(2, '0')}:00", fontWeight = FontWeight.Bold, color = FeniqoSageGreen)
                                }
                            }
                            Column {
                                Text("Bitiş", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { quietEndHour = (quietEndHour + 1) % 24 }) {
                                    Text("${quietEndHour.toString().padStart(2, '0')}:00", fontWeight = FontWeight.Bold, color = FeniqoSageGreen)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onPreferencesChange(
                            preferences.copy(
                                reminderOffset = offset,
                                reminderHour = hour,
                                reminderMinute = minute,
                                quietHoursEnabled = quietEnabled,
                                quietHoursStartHour = quietStartHour,
                                quietHoursEndHour = quietEndHour,
                            )
                        )
                        showReminderPreferencesSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FeniqoTouchTarget.PrimaryAction),
                ) {
                    Text("Kaydet", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
            }
        }
    }
}

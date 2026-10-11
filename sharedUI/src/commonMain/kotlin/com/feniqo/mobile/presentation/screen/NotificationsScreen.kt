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

import com.feniqo.mobile.presentation.settings.toLocalizedResource
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
                    title = stringResource(Res.string.notifications_title),
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
                        title = stringResource(Res.string.notifications_allow_title),
                        subtitle = if (preferences.enabled) {
                            stringResource(Res.string.notifications_allow_subtitle_enabled)
                        } else {
                            stringResource(Res.string.notifications_allow_subtitle_disabled)
                        },
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
                    text = stringResource(Res.string.notifications_categories_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = stringResource(Res.string.notifications_category_debts_title),
                        subtitle = stringResource(Res.string.notifications_category_debts_subtitle),
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
                        title = stringResource(Res.string.notifications_category_subscriptions_title),
                        subtitle = stringResource(Res.string.notifications_category_subscriptions_subtitle),
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
                        title = stringResource(Res.string.notifications_category_recurring_title),
                        subtitle = stringResource(Res.string.notifications_category_recurring_subtitle),
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
                        title = stringResource(Res.string.notifications_category_budgets_title),
                        subtitle = stringResource(Res.string.notifications_category_budgets_subtitle),
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
                    text = stringResource(Res.string.notifications_timing_privacy_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    val offsetLabel = stringResource(preferences.reminderOffset.toLocalizedResource())
                    val timeString = "$offsetLabel • ${preferences.reminderHour.toString().padStart(2, '0')}:${preferences.reminderMinute.toString().padStart(2, '0')}"
                    SettingsRowItem(
                        title = stringResource(Res.string.notifications_reminder_time_title),
                        subtitle = timeString,
                        icon = Icons.Outlined.Schedule,
                        onClick = { showReminderPreferencesSheet = true },
                    )
                    val quietString = if (preferences.quietHoursEnabled) {
                        "${preferences.quietHoursStartHour.toString().padStart(2, '0')}:${preferences.quietHoursStartMinute.toString().padStart(2, '0')} – ${preferences.quietHoursEndHour.toString().padStart(2, '0')}:${preferences.quietHoursEndMinute.toString().padStart(2, '0')}"
                    } else {
                        stringResource(Res.string.notifications_quiet_hours_disabled)
                    }
                    SettingsRowItem(
                        title = stringResource(Res.string.notifications_quiet_hours_title),
                        subtitle = quietString,
                        icon = Icons.Outlined.Bedtime,
                        onClick = { showReminderPreferencesSheet = true },
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.notifications_hide_amounts_title),
                        subtitle = stringResource(Res.string.notifications_hide_amounts_subtitle),
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
                    text = stringResource(Res.string.notifications_sheet_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )

                // Hatırlatma Zamanı Çipleri
                Text(
                    text = stringResource(Res.string.notifications_sheet_reminder_time_label),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    ReminderTimeOffset.entries.forEach { opt ->
                        val selected = offset == opt
                        FilterChip(
                            selected = selected,
                            onClick = { offset = opt },
                            label = { Text(stringResource(opt.toLocalizedResource())) },
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
                            text = stringResource(Res.string.notifications_sheet_notification_time_label),
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
                Text(
                    text = stringResource(Res.string.notifications_sheet_quiet_hours_label),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                SettingsGroupCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FeniqoSpacing.Large),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.notifications_sheet_quiet_hours_enable_title),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            )
                            Text(
                                text = stringResource(Res.string.notifications_sheet_quiet_hours_enable_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                                Text(
                                    text = stringResource(Res.string.notifications_sheet_quiet_hours_start),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { quietStartHour = (quietStartHour + 1) % 24 }) {
                                    Text(
                                        text = "${quietStartHour.toString().padStart(2, '0')}:00",
                                        fontWeight = FontWeight.Bold,
                                        color = FeniqoSageGreen,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = stringResource(Res.string.notifications_sheet_quiet_hours_end),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { quietEndHour = (quietEndHour + 1) % 24 }) {
                                    Text(
                                        text = "${quietEndHour.toString().padStart(2, '0')}:00",
                                        fontWeight = FontWeight.Bold,
                                        color = FeniqoSageGreen,
                                    )
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
                            ),
                        )
                        showReminderPreferencesSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FeniqoTouchTarget.PrimaryAction),
                ) {
                    Text(
                        text = stringResource(Res.string.notifications_sheet_save_button),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
            }
        }
    }
}

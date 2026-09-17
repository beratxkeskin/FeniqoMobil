package com.feniqo.mobile.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.presentation.theme.FeniqoGraphite
import com.feniqo.mobile.presentation.theme.FeniqoPureWhite
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget

@Composable
fun SettingsTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FeniqoSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(FeniqoTouchTarget.Minimum),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** 01 Ayarlar üstündeki kompakt grafit profil kartı. */
@Composable
fun SettingsGraphiteProfileCard(
    displayName: String,
    email: String,
    onManageProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(containerColor = FeniqoGraphite),
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onManageProfile),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(FeniqoSageGreenContainer),
                contentAlignment = Alignment.Center,
            ) {
                val initials = displayName.trim().split(" ")
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .take(2)
                    .joinToString("")
                    .ifBlank { "F" }
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = FeniqoSageGreen,
                )
            }
            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName.ifBlank { "Feniqo Kullanıcısı" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = FeniqoPureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (email.isNotBlank()) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = FeniqoPureWhite.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Profili yönet",
                    style = MaterialTheme.typography.labelSmall,
                    color = FeniqoPureWhite.copy(alpha = 0.85f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Profili yönet",
                tint = FeniqoPureWhite.copy(alpha = 0.7f),
            )
        }
    }
}

/** Menü ve ayar gruplarını çevreleyen yuvarlatılmış kart yüzeyi. */
@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

/** Standart ayar satır bileşeni. */
@Composable
fun SettingsRowItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = FeniqoSageGreen,
    iconBgColor: Color = iconTint.copy(alpha = 0.12f),
    badgeText: String? = null,
    badgeColor: Color = FeniqoSageGreen,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    showChevron: Boolean = onClick != null && trailingContent == null,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isClickable = onClick != null
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isClickable) Modifier.clickable(role = Role.Button, onClick = onClick!!)
                    else Modifier
                )
                .defaultMinSize(minHeight = FeniqoTouchTarget.Minimum)
                .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(FeniqoRadius.Small))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor.copy(alpha = 0.12f),
                    modifier = Modifier.padding(end = FeniqoSpacing.Small),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 2.dp),
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            } else if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "$title aç",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 68.dp, end = FeniqoSpacing.Large),
            )
        }
    }
}

/** Tehlikeli ve kırmızı eylemler (Çıkış, Hesabı Sil, Fotoğrafı Kaldır). */
@Composable
fun SettingsDangerItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val errorColor = MaterialTheme.colorScheme.error
    Card(
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = errorColor.copy(alpha = 0.06f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = FeniqoTouchTarget.Minimum)
                .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(FeniqoRadius.Small))
                    .background(errorColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = errorColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = errorColor,
                modifier = Modifier.weight(1f),
            )
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "$title aç",
                    tint = errorColor,
                )
            }
        }
    }
}

/** 04 Görünüm: Tema seçim kartı (Sistem, Açık, Koyu). */
@Composable
fun ThemeSelectionCard(
    themePreference: ThemePreference,
    currentSelection: ThemePreference,
    title: String,
    subtitle: String,
    onSelect: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = themePreference == currentSelection
    val borderColor = if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Card(
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        modifier = modifier
            .border(borderWidth, borderColor, RoundedCornerShape(FeniqoRadius.Medium))
            .clickable { onSelect(themePreference) }
            .padding(FeniqoSpacing.Medium),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Küçük telefon çerçevesi illüstrasyonu
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (themePreference) {
                            ThemePreference.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
                            ThemePreference.LIGHT -> Color(0xFFF1F3F2)
                            ThemePreference.DARK -> Color(0xFF1E2621)
                        }
                    )
                    .border(1.dp, Color(0xFF8A9A90), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 16.dp, height = 3.dp)
                            .background(FeniqoSageGreen, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 6.dp)
                            .background(
                                if (themePreference == ThemePreference.DARK) Color(0xFF334139) else Color(0xFFD3DFD8),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                lineHeight = 14.sp,
            )
            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
            RadioButton(
                selected = isSelected,
                onClick = { onSelect(themePreference) },
                colors = RadioButtonDefaults.colors(selectedColor = FeniqoSageGreen),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** 04 Görünüm / 09 Güvenlik: Tutarları gizle kartı ve önizleme kutusu. */
@Composable
fun AmountMaskingSection(
    maskAmounts: Boolean,
    onMaskChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
    ) {
        Card(
            shape = RoundedCornerShape(FeniqoRadius.Large),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FeniqoSpacing.Large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(FeniqoRadius.Small))
                        .background(FeniqoSageGreen.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (maskAmounts) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = FeniqoSageGreen,
                    )
                }
                Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tutarları gizle",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Tutarlar gizlendiğinde bakiyeler maskelenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = maskAmounts,
                    onCheckedChange = onMaskChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = FeniqoSageGreen),
                )
            }
        }

        // Maskelenmiş veya açık örnek önizleme kutusu
        Card(
            shape = RoundedCornerShape(FeniqoRadius.Large),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FeniqoSpacing.Large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Toplam bakiye (Örnek)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (maskAmounts) "••••" else "₺12.450,00",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Icon(
                    imageVector = if (maskAmounts) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 05 Dil ve Bölge: Biçim önizleme kartı. */
@Composable
fun FormatPreviewGraphiteCard(
    formattedPreview: String,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(containerColor = FeniqoGraphite),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(FeniqoRadius.Small))
                    .background(FeniqoPureWhite.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = FeniqoPureWhite,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
            Column {
                Text(
                    text = "Biçim önizlemesi",
                    style = MaterialTheme.typography.bodySmall,
                    color = FeniqoPureWhite.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedPreview,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = FeniqoPureWhite,
                )
            }
        }
    }
}

/** 07 & 21 Bildirim izni durumu veya kapalı uyarı kartı. */
@Composable
fun NotificationPermissionCard(
    isPermissionGranted: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = if (isPermissionGranted) FeniqoGraphite else Color(0xFF2C3230),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(FeniqoRadius.Small))
                    .background(FeniqoPureWhite.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPermissionGranted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                    contentDescription = null,
                    tint = FeniqoPureWhite,
                )
            }
            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPermissionGranted) "Bildirim izni açık" else "Sistem bildirimine izin vermiyor.",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = FeniqoPureWhite,
                )
                Text(
                    text = if (isPermissionGranted) "Uygulama bildirim gönderebilir." else "Tercihlerin korunur; bildirim almak için aç.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = FeniqoPureWhite.copy(alpha = 0.7f),
                )
            }
            if (!isPermissionGranted) {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Aç", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** 11 Senkronizasyon: Üst durum kartı. */
@Composable
fun SyncStatusHeaderCard(
    isOnline: Boolean,
    isSyncing: Boolean,
    modifier: Modifier = Modifier,
) {
    val title = when {
        isSyncing -> "Eşitleniyor..."
        !isOnline -> "Çevrimdışı"
        else -> "Eşitlendi"
    }
    val subtitle = when {
        isSyncing -> "Değişiklikler sunucuya aktarılıyor."
        !isOnline -> "Bağlantı bekleniyor."
        else -> "Verileriniz güncel."
    }
    val icon = when {
        isSyncing -> Icons.Default.Sync
        !isOnline -> Icons.Outlined.CloudOff
        else -> Icons.Outlined.CloudDone
    }

    Card(
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(containerColor = FeniqoGraphite),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(FeniqoRadius.Small))
                    .background(FeniqoPureWhite.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = FeniqoPureWhite,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = FeniqoPureWhite,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FeniqoPureWhite.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** Çakışma inceleme banner'ı (Panel 11). */
@Composable
fun ConflictAlertCard(
    conflictCount: Int,
    onInspectConflict: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conflictCount <= 0) return
    Card(
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD591)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFD46B08),
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$conflictCount kayıt inceleme bekliyor.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF873800),
                )
                Text(
                    text = "Olası bir çakışma tespit edildi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAD4E00),
                )
            }
            TextButton(onClick = onInspectConflict) {
                Text("İncele", color = FeniqoSageGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

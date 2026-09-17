package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
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
import com.feniqo.mobile.domain.repository.AutoLockTimeout
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoGraphite
import com.feniqo.mobile.presentation.theme.FeniqoPureWhite
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

/**
 * 09 Güvenlik ve Gizlilik Ekranı.
 */
@Composable
fun SecurityPrivacyScreen(
    biometricLockEnabled: Boolean,
    biometricLockAvailable: Boolean,
    autoLockTimeout: AutoLockTimeout,
    hideAmounts: Boolean,
    onBack: () -> Unit,
    onBiometricLockChange: (Boolean) -> Unit,
    onAutoLockTimeoutChange: (AutoLockTimeout) -> Unit,
    onHideAmountsChange: (Boolean) -> Unit,
    onNavigateToChangePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTimeoutDialog by remember { mutableStateOf(false) }

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
                    title = "Güvenlik ve gizlilik",
                    onBack = onBack,
                )
            }

            // Grafit Durum Kartı
            item {
                Card(
                    shape = RoundedCornerShape(FeniqoRadius.Large),
                    colors = CardDefaults.cardColors(containerColor = FeniqoGraphite),
                    modifier = Modifier.fillMaxWidth(),
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
                                .background(FeniqoPureWhite.copy(alpha = 0.14f), RoundedCornerShape(FeniqoRadius.Small)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (biometricLockEnabled) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                contentDescription = null,
                                tint = FeniqoPureWhite,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                        Column {
                            Text(
                                text = if (biometricLockEnabled) "Uygulama kilidi açık" else "Uygulama kilidi kapalı",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = FeniqoPureWhite,
                            )
                            Text(
                                text = if (biometricLockAvailable) "Biyometri veya cihaz ekran kilidiyle doğrulama."
                                else "Cihazınızda ekran kilidi tanımlı değil.",
                                style = MaterialTheme.typography.bodySmall,
                                color = FeniqoPureWhite.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            // Güvenlik Ayarları Grubu
            item {
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Uygulama kilidi",
                        subtitle = "Biyometri veya cihaz şifresi ile koru",
                        icon = Icons.Outlined.Fingerprint,
                        trailingContent = {
                            Switch(
                                checked = biometricLockEnabled,
                                onCheckedChange = onBiometricLockChange,
                                enabled = biometricLockAvailable || biometricLockEnabled,
                                colors = SwitchDefaults.colors(checkedTrackColor = FeniqoSageGreen),
                            )
                        },
                    )
                    if (biometricLockEnabled) {
                        SettingsRowItem(
                            title = "Otomatik kilit",
                            subtitle = autoLockTimeout.toLabel(),
                            icon = Icons.Outlined.Timer,
                            onClick = { showTimeoutDialog = true },
                        )
                    }
                    SettingsRowItem(
                        title = "Bildirimlerde tutarları gizle",
                        subtitle = "Kilit ekranında bakiyeleri maskele",
                        icon = Icons.Outlined.VisibilityOff,
                        trailingContent = {
                            Switch(
                                checked = hideAmounts,
                                onCheckedChange = onHideAmountsChange,
                                colors = SwitchDefaults.colors(checkedTrackColor = FeniqoSageGreen),
                            )
                        },
                    )
                    SettingsRowItem(
                        title = "Parolayı değiştir",
                        subtitle = "Hesap giriş şifrenizi güncelleyin",
                        icon = Icons.Outlined.Key,
                        showDivider = false,
                        onClick = onNavigateToChangePassword,
                    )
                }
            }
        }
    }

    // Otomatik Kilit Seçim Diyaloğu
    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("Otomatik kilit süresi") },
            text = {
                Column {
                    AutoLockTimeout.entries.forEach { timeout ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAutoLockTimeoutChange(timeout)
                                    showTimeoutDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = timeout.toLabel(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            RadioButton(
                                selected = timeout == autoLockTimeout,
                                onClick = {
                                    onAutoLockTimeoutChange(timeout)
                                    showTimeoutDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = FeniqoSageGreen),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) {
                    Text("Kapat")
                }
            },
        )
    }
}

private fun AutoLockTimeout.toLabel(): String = when (this) {
    AutoLockTimeout.IMMEDIATELY -> "Anında"
    AutoLockTimeout.AFTER_30_SECONDS -> "30 saniye sonra"
    AutoLockTimeout.AFTER_1_MINUTE -> "1 dakika sonra"
    AutoLockTimeout.AFTER_5_MINUTES -> "5 dakika sonra"
}

package com.feniqo.mobile.presentation.screen

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget

/**
 * 12 Yardım ve Hakkında Ekranı ve 24 Geri Bildirim Formu.
 */
@Composable
fun HelpAboutScreen(
    appVersion: String,
    onBack: () -> Unit,
    onNavigateToFeedback: (isBugReport: Boolean) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTermsOfService: () -> Unit,
    onOpenOpenSourceLicenses: () -> Unit,
    onOpenHelpCenter: () -> Unit,
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
                    title = "Yardım ve hakkında",
                    onBack = onBack,
                )
            }

            // Feniqo Marka Başlığı
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = FeniqoSpacing.Medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Eco,
                            contentDescription = null,
                            tint = FeniqoSageGreen,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                        Text(
                            text = "feniqo",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = FeniqoSageGreen,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Daha bilinçli bir finansal yaşam.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // DESTEK Grubu
            item {
                Text(
                    text = "DESTEK",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Yardım merkezi",
                        subtitle = "Sıkça sorulan sorular ve rehberler",
                        icon = Icons.Outlined.HelpCenter,
                        onClick = onOpenHelpCenter,
                    )
                    SettingsRowItem(
                        title = "Geri bildirim gönder",
                        subtitle = "Uygulama deneyimini iyileştirmemize yardım et",
                        icon = Icons.Outlined.RateReview,
                        onClick = { onNavigateToFeedback(false) },
                    )
                    SettingsRowItem(
                        title = "Hata bildir",
                        subtitle = "Karşılaştığın bir sorunu bizimle paylaş",
                        icon = Icons.Outlined.BugReport,
                        showDivider = false,
                        onClick = { onNavigateToFeedback(true) },
                    )
                }
            }

            // HUKUKİ Grubu
            item {
                Text(
                    text = "HUKUKİ",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Gizlilik politikası",
                        subtitle = "Verilerinizin nasıl korunduğunu öğrenin",
                        icon = Icons.Outlined.Policy,
                        onClick = onOpenPrivacyPolicy,
                    )
                    SettingsRowItem(
                        title = "Kullanım koşulları",
                        subtitle = "Hizmet şartları ve kurallar",
                        icon = Icons.Outlined.Description,
                        onClick = onOpenTermsOfService,
                    )
                    SettingsRowItem(
                        title = "Açık kaynak lisansları",
                        subtitle = "Kullanılan açık kaynak kütüphaneler",
                        icon = Icons.Outlined.Code,
                        showDivider = false,
                        onClick = onOpenOpenSourceLicenses,
                    )
                }
            }

            // Sürüm Bilgisi
            item {
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Sürüm bilgisi",
                        subtitle = appVersion,
                        icon = Icons.Outlined.Info,
                        showChevron = false,
                        showDivider = false,
                        onClick = null,
                    )
                }
            }
        }
    }
}

/**
 * 24 Geri Bildirim Formu Ekranı.
 */
@Composable
fun FeedbackScreen(
    initialIsBug: Boolean = false,
    onBack: () -> Unit,
    onSendFeedback: (isBug: Boolean, subject: String, message: String, hasAttachment: Boolean) -> Unit,
    onPickAttachment: () -> Unit,
    hasAttachment: Boolean,
    attachmentName: String?,
    onRemoveAttachment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isBug by remember { mutableStateOf(initialIsBug) }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

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
                title = "Geri bildirim",
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                // Öneri / Hata Seçici Sekmeleri
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                    ) {
                        OutlinedButton(
                            onClick = { isBug = false },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (!isBug) FeniqoSageGreen.copy(alpha = 0.12f) else Color.Transparent,
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (!isBug) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = if (!isBug) FeniqoSageGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                            Text(
                                text = "Öneri",
                                color = if (!isBug) FeniqoSageGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        OutlinedButton(
                            onClick = { isBug = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isBug) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else Color.Transparent,
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isBug) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BugReport,
                                contentDescription = null,
                                tint = if (isBug) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                            Text(
                                text = "Hata",
                                color = if (isBug) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Konu Alanı
                item {
                    Text(
                        text = "Konu",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        placeholder = { Text("Konu başlığını yaz") },
                        singleLine = true,
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = FeniqoSageGreen,
                        ),
                    )
                }

                // Mesaj Alanı
                item {
                    Text(
                        text = "Mesaj",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        placeholder = { Text("Mesajını buraya yaz...") },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = FeniqoSageGreen,
                        ),
                    )
                }

                // Ekran Görüntüsü Ekle
                item {
                    Card(
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(FeniqoSpacing.Large),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.AttachFile, null, tint = FeniqoSageGreen)
                            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasAttachment) (attachmentName ?: "Ekran görüntüsü eklendi") else "Ekran görüntüsü ekle",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                )
                                Text(
                                    text = if (hasAttachment) "Ekli dosyayı değiştirmek için dokunun" else "(isteğe bağlı)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (hasAttachment) {
                                IconButton(onClick = onRemoveAttachment) {
                                    Icon(Icons.Outlined.Close, "Eki kaldır", tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                TextButton(onClick = onPickAttachment) {
                                    Text("Seç", color = FeniqoSageGreen, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Gizlilik Uyarısı
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
                            text = "Kişisel ve finansal bilgilerini paylaşma.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Gönder Butonu
            Button(
                onClick = { onSendFeedback(isBug, subject, message, hasAttachment) },
                enabled = subject.isNotBlank() && message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FeniqoTouchTarget.PrimaryAction),
            ) {
                Text("Gönder", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

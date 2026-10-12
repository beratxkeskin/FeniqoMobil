package com.feniqo.mobile.presentation.screen

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
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

const val FEEDBACK_MESSAGE_MAX_LENGTH = 1000

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
                    title = stringResource(Res.string.help_about_title),
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
                        text = stringResource(Res.string.help_about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // DESTEK Grubu
            item {
                Text(
                    text = stringResource(Res.string.help_about_section_support),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = stringResource(Res.string.help_about_row_help_center_title),
                        subtitle = stringResource(Res.string.help_about_row_help_center_subtitle),
                        icon = Icons.Outlined.HelpCenter,
                        onClick = onOpenHelpCenter,
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.help_about_row_feedback_title),
                        subtitle = stringResource(Res.string.help_about_row_feedback_subtitle),
                        icon = Icons.Outlined.RateReview,
                        onClick = { onNavigateToFeedback(false) },
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.help_about_row_bug_title),
                        subtitle = stringResource(Res.string.help_about_row_bug_subtitle),
                        icon = Icons.Outlined.BugReport,
                        showDivider = false,
                        onClick = { onNavigateToFeedback(true) },
                    )
                }
            }

            // HUKUKİ Grubu
            item {
                Text(
                    text = stringResource(Res.string.help_about_section_legal),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroupCard {
                    SettingsRowItem(
                        title = stringResource(Res.string.help_about_row_privacy_title),
                        subtitle = stringResource(Res.string.help_about_row_privacy_subtitle),
                        icon = Icons.Outlined.Policy,
                        onClick = onOpenPrivacyPolicy,
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.help_about_row_terms_title),
                        subtitle = stringResource(Res.string.help_about_row_terms_subtitle),
                        icon = Icons.Outlined.Description,
                        onClick = onOpenTermsOfService,
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.help_about_row_licenses_title),
                        subtitle = stringResource(Res.string.help_about_row_licenses_subtitle),
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
                        title = stringResource(Res.string.help_about_version_label),
                        subtitle = stringResource(Res.string.help_about_version_format, appVersion),
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
 * Pano B4: Geri Bildirim Formu Ekranı.
 *
 * Kullanıcının öneri veya hata bildirimini e-posta paylaşım intent'ine hazırlar.
 * Sunucuya sahte gönderim yapmaz; parola ve finansal veri paylaşılmaması için uyarır.
 */
@Composable
fun FeedbackScreen(
    initialIsBug: Boolean = false,
    onBack: () -> Unit,
    onShareFeedback: (isBug: Boolean, subject: String, message: String) -> Unit,
    onPickAttachment: () -> Unit,
    hasAttachment: Boolean,
    attachmentName: String?,
    onRemoveAttachment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isBug by remember(initialIsBug) { mutableStateOf(initialIsBug) }
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
                title = stringResource(if (isBug) Res.string.feedback_title_bug else Res.string.feedback_title_suggestion),
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                contentPadding = PaddingValues(vertical = FeniqoSpacing.Medium),
            ) {
                item {
                    Text(
                        text = stringResource(if (isBug) Res.string.feedback_desc_bug else Res.string.feedback_desc_suggestion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Pano B4: Öneri / Hata Seçici Çipleri
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (!isBug) FeniqoSageGreen else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (!isBug) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { isBug = false },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lightbulb,
                                    contentDescription = null,
                                    tint = if (!isBug) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(Res.string.feedback_type_suggestion),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (!isBug) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        val bugActiveColor = Color(0xFFD32F2F)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isBug) bugActiveColor else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isBug) bugActiveColor else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { isBug = true },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BugReport,
                                    contentDescription = null,
                                    tint = if (isBug) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(Res.string.feedback_type_bug),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isBug) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Konu Alanı
                item {
                    Column {
                        Text(
                            text = stringResource(Res.string.feedback_subject_label),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            placeholder = { Text(stringResource(Res.string.feedback_subject_placeholder)) },
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
                }

                // Mesaj Alanı ve Sayaç
                item {
                    Column {
                        Text(
                            text = stringResource(Res.string.feedback_message_label),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = message,
                            onValueChange = { if (it.length <= FEEDBACK_MESSAGE_MAX_LENGTH) message = it },
                            placeholder = { Text(stringResource(Res.string.feedback_message_placeholder)) },
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                Res.string.feedback_character_count_format,
                                message.length,
                                FEEDBACK_MESSAGE_MAX_LENGTH,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }

                // Ekran Görüntüsü (İsteğe Bağlı)
                item {
                    Column {
                        Text(
                            text = stringResource(Res.string.feedback_attachment_label),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (hasAttachment) {
                                val fallbackAttachmentName = stringResource(Res.string.feedback_attachment_default_name)
                                Card(
                                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier.height(56.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Outlined.Image, null, tint = FeniqoSageGreen, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = (attachmentName ?: fallbackAttachmentName).take(16),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = onRemoveAttachment,
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = stringResource(Res.string.feedback_attachment_remove),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }

                            // Ekle Butonu
                            OutlinedButton(
                                onClick = onPickAttachment,
                                shape = RoundedCornerShape(FeniqoRadius.Medium),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(Res.string.feedback_attachment_add))
                            }
                        }
                    }
                }

                // Pano B4: Kırmızı Güvenlik Uyarısı
                item {
                    Card(
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE57373).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(FeniqoSpacing.Large),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(24.dp).padding(top = 2.dp),
                            )
                            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                            Column {
                                Text(
                                    text = stringResource(Res.string.feedback_warning_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(Res.string.feedback_warning_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                                )
                            }
                        }
                    }
                }
            }

            // Geri Bildirimi Paylaş Butonu
            Button(
                onClick = { onShareFeedback(isBug, subject, message) },
                enabled = subject.isNotBlank() && message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBug) Color(0xFFD32F2F) else FeniqoSageGreen,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FeniqoTouchTarget.PrimaryAction),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                Text(stringResource(Res.string.feedback_share_button), fontWeight = FontWeight.SemiBold)
            }

            // Alt Bilgilendirme Notu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FeniqoSpacing.Small),
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
                    text = stringResource(Res.string.feedback_footer_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

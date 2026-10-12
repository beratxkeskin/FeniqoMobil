package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EmailVerificationStatus
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoPureWhite
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Pano A3: E-posta Durum ve Yönetim Ekranı.
 *
 * Gerçek Supabase Auth doğrulama bilgisine (`EmailVerificationStatus`) dayanır.
 * Boş veya dolu e-postaya göre sahte doğrulama üretmez.
 */
@Composable
fun EmailSettingsScreen(
    currentEmail: String,
    verificationStatus: EmailVerificationStatus,
    isSendingVerification: Boolean,
    verificationSentSuccess: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onResendVerification: () -> Unit,
    onNavigateToChangeEmail: () -> Unit,
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
                title = stringResource(Res.string.email_settings_title),
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                contentPadding = PaddingValues(vertical = FeniqoSpacing.Medium),
            ) {
                // E-posta Adresi & Durum Rozeti Kartı
                item {
                    Card(
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(FeniqoSpacing.Large),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(Res.string.email_settings_address_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                val (statusText, statusBg, statusColor) = when (verificationStatus) {
                                    EmailVerificationStatus.VERIFIED ->
                                        Triple(
                                            stringResource(Res.string.email_settings_status_verified),
                                            Color(0xFF2E7D32).copy(alpha = 0.12f),
                                            Color(0xFF2E7D32),
                                        )
                                    EmailVerificationStatus.PENDING_VERIFICATION ->
                                        Triple(
                                            stringResource(Res.string.email_settings_status_pending),
                                            Color(0xFFE65100).copy(alpha = 0.12f),
                                            Color(0xFFE65100),
                                        )
                                    EmailVerificationStatus.UNKNOWN ->
                                        Triple(
                                            stringResource(Res.string.email_settings_status_unknown),
                                            Color(0xFF757575).copy(alpha = 0.12f),
                                            Color(0xFF757575),
                                        )
                                }

                                Surface(
                                    shape = RoundedCornerShape(FeniqoRadius.Small),
                                    color = statusBg,
                                ) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = currentEmail.ifBlank { stringResource(Res.string.email_settings_unspecified) },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // Bilgilendirme Notu: Yalnızca doğrulama bekleniyorsa veya bilinmiyorsa
                if (verificationStatus != EmailVerificationStatus.VERIFIED) {
                    item {
                        Card(
                            shape = RoundedCornerShape(FeniqoRadius.Medium),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(FeniqoSpacing.Medium),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = FeniqoSageGreen,
                                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                                )
                                Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                                Text(
                                    text = stringResource(Res.string.email_settings_pending_info),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Tekrar Gönder Butonu
                    item {
                        Button(
                            onClick = onResendVerification,
                            enabled = !isSendingVerification && currentEmail.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FeniqoSageGreen,
                                contentColor = FeniqoPureWhite,
                            ),
                            shape = RoundedCornerShape(FeniqoRadius.Medium),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(FeniqoTouchTarget.PrimaryAction),
                        ) {
                            if (isSendingVerification) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = FeniqoPureWhite,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                                Text(
                                    text = stringResource(Res.string.email_settings_resend_button),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                // Başarı ve Hata Mesajları
                if (verificationSentSuccess) {
                    item {
                        Text(
                            text = stringResource(Res.string.email_settings_resend_success),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                errorMessage?.let { error ->
                    item {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                }

                // E-posta Adresini Değiştir Kartı
                item {
                    SettingsGroupCard {
                        SettingsRowItem(
                            title = stringResource(Res.string.email_settings_change_email_title),
                            icon = Icons.Outlined.Mail,
                            showDivider = false,
                            onClick = onNavigateToChangeEmail,
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(Res.string.email_settings_change_email_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // Alt Bilgi Notu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FeniqoSpacing.Medium),
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
                    text = stringResource(Res.string.email_settings_footer_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

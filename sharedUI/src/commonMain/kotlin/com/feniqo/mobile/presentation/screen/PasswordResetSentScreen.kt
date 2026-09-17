package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.auth.AuthErrorBanner
import com.feniqo.mobile.presentation.auth.AuthPrimaryButton
import com.feniqo.mobile.presentation.auth.AuthSecondaryButton
import com.feniqo.mobile.presentation.auth.AuthStatusBadge
import com.feniqo.mobile.presentation.auth.AuthTopBar
import com.feniqo.mobile.presentation.auth.AuthUiMessage
import com.feniqo.mobile.presentation.auth.toDisplayText
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * Pano B - 06 E-postanı Kontrol Et Ekranı.
 * Hesap varlığını açığa çıkarmayan güvenli bilgilendirme sunar.
 */
@Composable
fun PasswordResetSentScreen(
    email: String,
    onNavigateToLogin: () -> Unit,
    onEditEmail: () -> Unit,
    onResend: () -> Unit,
    isResending: Boolean = false,
    resendSuccess: Boolean = false,
    errorMessage: AuthUiMessage? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            AuthTopBar(
                title = "Feniqo",
                onBack = onBack,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .padding(horizontal = FeniqoSpacing.Screen, vertical = FeniqoSpacing.Medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
                ) {
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    // Zarf rozeti
                    AuthStatusBadge(
                        imageVector = Icons.Outlined.MailOutline,
                        contentDescription = "E-postanı kontrol et",
                    )

                    // Başlık ve Açıklama
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "E-postanı kontrol et",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Bu adresle bir hesap varsa parola sıfırlama bağlantısı gönderilecek.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 22.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Salt okunur e-posta kartı
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = email.ifBlank { "eposta@ornek.com" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }

                    // Spam hatırlatması
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Spam klasörünü de kontrol edebilirsin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Başarı veya hata bildirimi
                    if (resendSuccess) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "Sıfırlama bağlantısı tekrar gönderildi.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }

                    if (errorMessage != null) {
                        AuthErrorBanner(
                            message = errorMessage.toDisplayText(),
                            isNetworkError = errorMessage == AuthUiMessage.NETWORK_UNAVAILABLE,
                        )
                    }

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    // Butonlar
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AuthPrimaryButton(
                            text = "Girişe dön",
                            onClick = onNavigateToLogin,
                        )

                        AuthSecondaryButton(
                            text = "Adresi düzelt",
                            onClick = onEditEmail,
                        )

                        Text(
                            text = if (isResending) "Gönderiliyor..." else "Tekrar gönder",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = FeniqoSageGreen,
                            modifier = Modifier
                                .clickable(
                                    role = Role.Button,
                                    enabled = !isResending,
                                    onClick = onResend,
                                )
                                .padding(vertical = 6.dp, horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PasswordResetSentScreenPreview() {
    FeniqoTheme {
        PasswordResetSentScreen(
            email = "ayse@example.com",
            onNavigateToLogin = {},
            onEditEmail = {},
            onResend = {},
        )
    }
}

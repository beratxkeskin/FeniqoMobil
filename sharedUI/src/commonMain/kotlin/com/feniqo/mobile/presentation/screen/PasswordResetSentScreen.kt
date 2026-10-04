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
import com.feniqo.mobile.presentation.auth.toLocalizedText
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.auth_email_placeholder
import feniqomobil.sharedui.generated.resources.auth_password_reset_sent
import feniqomobil.sharedui.generated.resources.password_back_to_login
import feniqomobil.sharedui.generated.resources.password_check_email
import feniqomobil.sharedui.generated.resources.password_edit_email
import feniqomobil.sharedui.generated.resources.password_resend
import feniqomobil.sharedui.generated.resources.password_resend_success
import feniqomobil.sharedui.generated.resources.password_sending
import feniqomobil.sharedui.generated.resources.password_spam_hint
import org.jetbrains.compose.resources.stringResource

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
                        contentDescription = stringResource(Res.string.password_check_email),
                    )

                    // Başlık ve Açıklama
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = stringResource(Res.string.password_check_email),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(Res.string.auth_password_reset_sent),
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
                            text =
                                if (email.isBlank()) {
                                    stringResource(Res.string.auth_email_placeholder)
                                } else {
                                    email
                                },
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
                            text = stringResource(Res.string.password_spam_hint),
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
                                text = stringResource(Res.string.password_resend_success),
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
                            message = errorMessage.toLocalizedText(),
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
                            text = stringResource(Res.string.password_back_to_login),
                            onClick = onNavigateToLogin,
                        )

                        AuthSecondaryButton(
                            text = stringResource(Res.string.password_edit_email),
                            onClick = onEditEmail,
                        )

                        Text(
                            text =
                                if (isResending) {
                                    stringResource(Res.string.password_sending)
                                } else {
                                    stringResource(Res.string.password_resend)
                                },
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

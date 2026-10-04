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
import com.feniqo.mobile.presentation.auth.EmailVerificationHeroIllustration
import com.feniqo.mobile.presentation.auth.toLocalizedText
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.auth_email_placeholder
import feniqomobil.sharedui.generated.resources.email_verification_body
import feniqomobil.sharedui.generated.resources.email_verification_required_badge
import feniqomobil.sharedui.generated.resources.email_verification_required_body
import feniqomobil.sharedui.generated.resources.email_verification_required_title
import feniqomobil.sharedui.generated.resources.email_verification_resend_success
import feniqomobil.sharedui.generated.resources.email_verification_title
import feniqomobil.sharedui.generated.resources.password_back_to_login
import feniqomobil.sharedui.generated.resources.password_resend
import feniqomobil.sharedui.generated.resources.password_sending
import org.jetbrains.compose.resources.stringResource

/**
 * Pano A - 04 E-posta Doğrulama ve Pano C - 14 Doğrulama Gerekli Ekranı.
 */
@Composable
fun AuthEmailVerificationScreen(
    email: String,
    isRequiredOnLogin: Boolean = false,
    isResending: Boolean = false,
    resendSuccess: Boolean = false,
    errorMessage: AuthUiMessage? = null,
    onResend: () -> Unit,
    onNavigateToLogin: () -> Unit,
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

                    // İllüstrasyon veya Rozet
                    if (isRequiredOnLogin) {
                        AuthStatusBadge(
                            imageVector = Icons.Outlined.MailOutline,
                            contentDescription = stringResource(Res.string.email_verification_required_badge),
                        )
                    } else {
                        EmailVerificationHeroIllustration()
                    }

                    // Başlık ve Açıklama
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        val titleText =
                            if (isRequiredOnLogin) {
                                stringResource(Res.string.email_verification_required_title)
                            } else {
                                stringResource(Res.string.email_verification_title)
                            }

                        val bodyText =
                            if (isRequiredOnLogin) {
                                stringResource(Res.string.email_verification_required_body)
                            } else {
                                val targetEmail =
                                    if (email.isBlank()) {
                                        stringResource(Res.string.auth_email_placeholder)
                                    } else {
                                        email
                                    }
                                stringResource(Res.string.email_verification_body, targetEmail)
                            }

                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                lineHeight = 34.sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = bodyText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 22.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Başarı veya Hata Bildirimi
                    if (resendSuccess) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = stringResource(Res.string.email_verification_resend_success),
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
                    if (isRequiredOnLogin) {
                        // 14 nolu durum düzeni: Birincil "Tekrar gönder", İkincil "Girişe dön"
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AuthPrimaryButton(
                                text = stringResource(Res.string.password_resend),
                                onClick = onResend,
                                isLoading = isResending,
                                loadingText = stringResource(Res.string.password_sending),
                            )

                            AuthSecondaryButton(
                                text = stringResource(Res.string.password_back_to_login),
                                onClick = onNavigateToLogin,
                            )
                        }
                    } else {
                        // 04 nolu durum düzeni: Birincil "Girişe dön", İkincil metin "Tekrar gönder"
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AuthPrimaryButton(
                                text = stringResource(Res.string.password_back_to_login),
                                onClick = onNavigateToLogin,
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
                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun AuthEmailVerificationScreenPreview() {
    FeniqoTheme {
        AuthEmailVerificationScreen(
            email = "ayse@example.com",
            onResend = {},
            onNavigateToLogin = {},
        )
    }
}

@Preview
@Composable
private fun AuthEmailVerificationRequiredPreview() {
    FeniqoTheme {
        AuthEmailVerificationScreen(
            email = "ayse@example.com",
            isRequiredOnLogin = true,
            onResend = {},
            onNavigateToLogin = {},
        )
    }
}

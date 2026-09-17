package com.feniqo.mobile.presentation.screen

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
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.feniqo.mobile.presentation.component.AuthTextField
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * Pano B - 05 Parolamı Unuttum Ekranı.
 */
@Composable
fun ForgotPasswordScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit,
    isSubmitting: Boolean = false,
    errorMessage: AuthUiMessage? = null,
    emailError: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

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

                    // Anahtar rozeti
                    AuthStatusBadge(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = "Parolamı unuttum",
                    )

                    // Başlık ve Açıklama
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "Parolanı yenileyelim",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Hesabında kullandığın e-posta adresini gir.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // E-posta alanı
                    AuthTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        placeholder = "ayse@example.com",
                        leadingIcon = Icons.Outlined.MailOutline,
                        enabled = !isSubmitting,
                        isError = emailError != null,
                        supportingText = emailError,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            focusManager.clearFocus()
                            if (!isSubmitting) onSubmit()
                        },
                    )

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
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AuthPrimaryButton(
                            text = "Sıfırlama bağlantısı gönder",
                            onClick = {
                                focusManager.clearFocus()
                                if (!isSubmitting) onSubmit()
                            },
                            isLoading = isSubmitting,
                            loadingText = "Gönderiliyor...",
                            enabled = !isSubmitting,
                        )

                        AuthSecondaryButton(
                            text = "Girişe dön",
                            onClick = onNavigateToLogin,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ForgotPasswordScreenPreview() {
    FeniqoTheme {
        ForgotPasswordScreen(
            email = "ayse@example.com",
            onEmailChange = {},
            onSubmit = {},
            onNavigateToLogin = {},
        )
    }
}

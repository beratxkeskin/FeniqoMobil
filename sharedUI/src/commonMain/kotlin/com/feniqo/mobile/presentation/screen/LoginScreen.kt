package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.auth.AuthErrorBanner
import com.feniqo.mobile.presentation.auth.AuthPrimaryButton
import com.feniqo.mobile.presentation.auth.AuthTopBar
import com.feniqo.mobile.presentation.auth.AuthUiMessage
import com.feniqo.mobile.presentation.auth.LoginUiState
import com.feniqo.mobile.presentation.auth.toDisplayText
import com.feniqo.mobile.presentation.component.AuthTextField
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * Pano A - 02 Giriş Yap Ekranı ve Pano C (09, 11, 13) hata/durum yönetimi.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit = {},
    onNavigateToEmailVerification: ((email: String) -> Unit)? = null,
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
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                ) {
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    // Başlıklar
                    Text(
                        text = "Tekrar hoş geldin.",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Hesabına giriş yaparak devam et.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Form Alanları
                    AuthTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = "E-posta",
                        placeholder = "eposta@ornek.com",
                        leadingIcon = Icons.Outlined.MailOutline,
                        enabled = !state.isSubmitting,
                        isError = state.emailError != null,
                        supportingText = state.emailError?.toDisplayText(),
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        onImeAction = {
                            focusManager.moveFocus(FocusDirection.Down)
                        },
                    )

                    AuthTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = "Parola",
                        leadingIcon = Icons.Outlined.Lock,
                        enabled = !state.isSubmitting,
                        isError = state.passwordError != null,
                        supportingText = state.passwordError?.toDisplayText(),
                        isPassword = true,
                        isPasswordVisible = state.isPasswordVisible,
                        onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            focusManager.clearFocus()
                            if (!state.isSubmitting) onSubmit()
                        },
                    )

                    // Sağa hizalı "Parolamı unuttum"
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = "Parolamı unuttum",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(
                                    role = Role.Button,
                                    enabled = !state.isSubmitting,
                                    onClick = onNavigateToForgotPassword,
                                )
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                        )
                    }

                    // Hata ve Durum Kartları (Pano C 09 ve 11)
                    if (state.generalMessage != null) {
                        val isNetwork = state.generalMessage == AuthUiMessage.NETWORK_UNAVAILABLE
                        AuthErrorBanner(
                            message = state.generalMessage.toDisplayText(),
                            isNetworkError = isNetwork,
                        )
                        if (state.generalMessage == AuthUiMessage.EMAIL_NOT_CONFIRMED && onNavigateToEmailVerification != null) {
                            com.feniqo.mobile.presentation.auth.AuthSecondaryButton(
                                text = "Doğrulama bağlantısını tekrar gönder",
                                onClick = { onNavigateToEmailVerification(state.email) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Birincil Buton (Pano C 11'de "Tekrar dene", Pano C 13'te "Giriş yapılıyor...")
                    val buttonText = if (state.generalMessage == AuthUiMessage.NETWORK_UNAVAILABLE) {
                        "Tekrar dene"
                    } else {
                        "Giriş yap"
                    }

                    AuthPrimaryButton(
                        text = buttonText,
                        onClick = {
                            focusManager.clearFocus()
                            if (!state.isSubmitting) onSubmit()
                        },
                        isLoading = state.isSubmitting,
                        loadingText = "Giriş yapılıyor...",
                        enabled = !state.isSubmitting,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Alt yönlendirme: "Hesabın yok mu? Hesap oluştur"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Hesabın yok mu? ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Hesap oluştur",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = FeniqoSageGreen,
                            modifier = Modifier.clickable(
                                role = Role.Button,
                                enabled = !state.isSubmitting,
                                onClick = onNavigateToRegister,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun LoginScreenNormalPreview() {
    FeniqoTheme {
        LoginScreen(
            state = LoginUiState(email = "kullanici@feniqo.com"),
            onEmailChange = {},
            onPasswordChange = {},
            onPasswordVisibilityToggle = {},
            onSubmit = {},
            onNavigateToRegister = {},
            onNavigateToForgotPassword = {},
        )
    }
}

@Preview
@Composable
private fun LoginScreenErrorPreview() {
    FeniqoTheme {
        LoginScreen(
            state = LoginUiState(
                email = "kullanici@feniqo.com",
                generalMessage = AuthUiMessage.INVALID_CREDENTIALS,
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onPasswordVisibilityToggle = {},
            onSubmit = {},
            onNavigateToRegister = {},
            onNavigateToForgotPassword = {},
        )
    }
}

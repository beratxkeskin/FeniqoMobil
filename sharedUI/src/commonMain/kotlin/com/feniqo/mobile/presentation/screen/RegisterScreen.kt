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
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.auth.AuthErrorBanner
import com.feniqo.mobile.presentation.auth.AuthPrimaryButton
import com.feniqo.mobile.presentation.auth.AuthTopBar
import com.feniqo.mobile.presentation.auth.AuthUiMessage
import com.feniqo.mobile.presentation.auth.RegisterUiState
import com.feniqo.mobile.presentation.auth.toDisplayText
import com.feniqo.mobile.presentation.component.AuthTextField
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * Pano A - 03 Hesap Oluştur Ekranı ve Pano C-10 form doğrulama durumları.
 */
@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onFullNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onConfirmPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Başlıklar
                    Text(
                        text = "Yeni bir başlangıç.",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Feniqo ile finansal hedeflerine bir adım daha yaklaş.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // 1. Görünen ad (isteğe bağlı)
                    AuthTextField(
                        value = state.fullName,
                        onValueChange = onFullNameChange,
                        label = "Görünen ad (isteğe bağlı)",
                        placeholder = "Ayşe Yılmaz",
                        leadingIcon = Icons.Outlined.Person,
                        enabled = !state.isSubmitting,
                        isError = state.fullNameError != null,
                        supportingText = state.fullNameError?.toDisplayText(),
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    )

                    // 2. E-posta
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
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    )

                    // 3. Parola
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
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    )

                    // 4. Parola tekrar
                    AuthTextField(
                        value = state.confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        label = "Parola tekrar",
                        leadingIcon = Icons.Outlined.Lock,
                        enabled = !state.isSubmitting,
                        isError = state.confirmPasswordError != null,
                        supportingText = state.confirmPasswordError?.toDisplayText(),
                        isPassword = true,
                        isPasswordVisible = state.isConfirmPasswordVisible,
                        onPasswordVisibilityToggle = onConfirmPasswordVisibilityToggle,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            focusManager.clearFocus()
                            if (!state.isSubmitting) onSubmit()
                        },
                    )

                    // Genel hata mesajı banner'ı
                    if (state.generalMessage != null && state.generalMessage != AuthUiMessage.EMAIL_CONFIRMATION_SENT) {
                        AuthErrorBanner(
                            message = state.generalMessage.toDisplayText(),
                            isNetworkError = state.generalMessage == AuthUiMessage.NETWORK_UNAVAILABLE,
                        )
                    }

                    // Hukuki bağlantılar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Kullanım koşulları",
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDecoration = TextDecoration.Underline,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(role = Role.Button, onClick = onTermsClick),
                        )
                        Text(
                            text = "  •  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Gizlilik politikası",
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDecoration = TextDecoration.Underline,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(role = Role.Button, onClick = onPrivacyClick),
                        )
                    }

                    // Birincil Buton
                    AuthPrimaryButton(
                        text = "Hesap oluştur",
                        onClick = {
                            focusManager.clearFocus()
                            if (!state.isSubmitting) onSubmit()
                        },
                        isLoading = state.isSubmitting,
                        loadingText = "Hesap oluşturuluyor...",
                        enabled = !state.isSubmitting,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Alt yönlendirme: "Zaten hesabın var mı? Giriş yap"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Zaten hesabın var mı? ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Giriş yap",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = FeniqoSageGreen,
                            modifier = Modifier.clickable(
                                role = Role.Button,
                                enabled = !state.isSubmitting,
                                onClick = onNavigateToLogin,
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
private fun RegisterScreenNormalPreview() {
    FeniqoTheme {
        RegisterScreen(
            state = RegisterUiState.Initial,
            onFullNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onPasswordVisibilityToggle = {},
            onConfirmPasswordVisibilityToggle = {},
            onSubmit = {},
            onNavigateToLogin = {},
        )
    }
}

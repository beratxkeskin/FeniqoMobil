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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.auth.AuthUiMessage
import com.feniqo.mobile.presentation.auth.RegisterUiState
import com.feniqo.mobile.presentation.auth.toDisplayText
import com.feniqo.mobile.presentation.component.AuthTextField
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * Kayıt ekranının saf ve durumsuz (stateless) Compose sunumudur.
 * Normal form veya e-posta doğrulama bekleme durumunu render eder.
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
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(FeniqoSpacing.Screen),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                if (state.isEmailConfirmationPending) {
                    Text(
                        text = "Kaydınız Oluşturuldu",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text(
                            text = state.generalMessage?.toDisplayText()
                                ?: AuthUiMessage.EMAIL_CONFIRMATION_SENT.toDisplayText(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(FeniqoSpacing.Large),
                        )
                    }

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToLogin,
                    ) {
                        Text("Giriş Ekranına Dön")
                    }
                } else {
                    Text(
                        text = "Hesap Oluştur",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Feniqo ile gelir ve giderlerinizi takip edin.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    if (state.generalMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Text(
                                text = state.generalMessage.toDisplayText(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(FeniqoSpacing.Medium),
                            )
                        }
                    }

                    AuthTextField(
                        value = state.fullName,
                        onValueChange = onFullNameChange,
                        label = "Ad Soyad (İsteğe bağlı)",
                        enabled = !state.isSubmitting,
                        isError = state.fullNameError != null,
                        supportingText = state.fullNameError?.toDisplayText(),
                        imeAction = ImeAction.Next,
                        onImeAction = {
                            focusManager.moveFocus(FocusDirection.Down)
                        },
                    )

                    AuthTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = "E-posta",
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
                        label = "Yeni Parola",
                        enabled = !state.isSubmitting,
                        isError = state.passwordError != null,
                        supportingText = state.passwordError?.toDisplayText(),
                        isPassword = true,
                        isPasswordVisible = state.isPasswordVisible,
                        onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                        onImeAction = {
                            focusManager.moveFocus(FocusDirection.Down)
                        },
                    )

                    AuthTextField(
                        value = state.confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        label = "Parola Tekrar",
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

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            focusManager.clearFocus()
                            if (!state.isSubmitting) onSubmit()
                        },
                        enabled = !state.isSubmitting,
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .semantics { contentDescription = "Hesap oluşturuluyor" },
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Hesap Oluştur")
                        }
                    }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToLogin,
                        enabled = !state.isSubmitting,
                    ) {
                        Text("Zaten hesabım var (Giriş Yap)")
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
            state = RegisterUiState(
                fullName = "Ahmet Yılmaz",
                email = "ahmet@feniqo.com",
            ),
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

@Preview
@Composable
private fun RegisterScreenConfirmationPendingPreview() {
    FeniqoTheme(darkTheme = true) {
        RegisterScreen(
            state = RegisterUiState(
                email = "ahmet@feniqo.com",
                isEmailConfirmationPending = true,
                generalMessage = AuthUiMessage.EMAIL_CONFIRMATION_SENT,
            ),
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

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
import com.feniqo.mobile.presentation.auth.LoginUiState
import com.feniqo.mobile.presentation.auth.toDisplayText
import com.feniqo.mobile.presentation.component.AuthTextField
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * Giriş ekranının saf ve durumsuz (stateless) Compose sunumudur.
 * Yalnızca UI state ve kullanıcı etkileşim callback'lerini alır.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateToRegister: () -> Unit,
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
                Text(
                    text = "Feniqo",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Giriş yapın ve hesabınızı yönetin.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                if (state.generalMessage != null) {
                    val isConfirmation = state.generalMessage == AuthUiMessage.EMAIL_CONFIRMATION_SENT
                    val containerColor = if (isConfirmation) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                    val contentColor = if (isConfirmation) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = containerColor,
                            contentColor = contentColor,
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
                    label = "Parola",
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
                                .semantics { contentDescription = "Giriş yapılıyor" },
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Giriş Yap")
                    }
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToRegister,
                    enabled = !state.isSubmitting,
                ) {
                    Text("Hesap Oluştur (Kayıt Ol)")
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
        )
    }
}

@Preview
@Composable
private fun LoginScreenErrorPreview() {
    FeniqoTheme(darkTheme = true) {
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
        )
    }
}

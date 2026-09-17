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
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
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
 * Pano B - 07 Yeni Parola ve Pano C - 12 Bağlantı Geçersiz/Süresi Dolmuş Ekranı.
 */
@Composable
fun ResetPasswordScreen(
    password: String,
    confirmPassword: String,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onConfirmPasswordVisibilityToggle: () -> Unit,
    isPasswordVisible: Boolean = false,
    isConfirmPasswordVisible: Boolean = false,
    isSubmitting: Boolean = false,
    isInvalidOrExpiredLink: Boolean = false,
    errorMessage: AuthUiMessage? = null,
    passwordError: String? = null,
    confirmPasswordError: String? = null,
    onSubmit: () -> Unit,
    onRequestNewLink: () -> Unit,
    onNavigateToLogin: () -> Unit,
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
                    if (isInvalidOrExpiredLink) {
                        // Pano C 12 - Bağlantı geçersiz veya süresi dolmuş
                        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                        AuthStatusBadge(
                            imageVector = Icons.Outlined.LinkOff,
                            isError = true,
                            contentDescription = "Bağlantı geçersiz",
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Bağlantı geçersiz\nveya süresi dolmuş",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    lineHeight = 30.sp,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Parolanı yenilemek için yeni bir bağlantı iste.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }

                        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AuthPrimaryButton(
                                text = "Yeni bağlantı iste",
                                onClick = onRequestNewLink,
                            )

                            AuthSecondaryButton(
                                text = "Girişe dön",
                                onClick = onNavigateToLogin,
                            )
                        }
                    } else {
                        // Pano B 07 - Yeni parolanı belirle
                        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                text = "Yeni parolanı belirle",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        // Form Alanları
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            AuthTextField(
                                value = password,
                                onValueChange = onPasswordChange,
                                label = "Yeni parola",
                                leadingIcon = Icons.Outlined.Lock,
                                enabled = !isSubmitting,
                                isError = passwordError != null,
                                supportingText = passwordError,
                                isPassword = true,
                                isPasswordVisible = isPasswordVisible,
                                onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next,
                                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                            )

                            AuthTextField(
                                value = confirmPassword,
                                onValueChange = onConfirmPasswordChange,
                                label = "Yeni parola tekrar",
                                leadingIcon = Icons.Outlined.Lock,
                                enabled = !isSubmitting,
                                isError = confirmPasswordError != null,
                                supportingText = confirmPasswordError,
                                isPassword = true,
                                isPasswordVisible = isConfirmPasswordVisible,
                                onPasswordVisibilityToggle = onConfirmPasswordVisibilityToggle,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                                onImeAction = {
                                    focusManager.clearFocus()
                                    if (!isSubmitting) onSubmit()
                                },
                            )
                        }

                        if (errorMessage != null) {
                            AuthErrorBanner(
                                message = errorMessage.toDisplayText(),
                                isNetworkError = errorMessage == AuthUiMessage.NETWORK_UNAVAILABLE,
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        AuthPrimaryButton(
                            text = "Parolayı güncelle",
                            onClick = {
                                focusManager.clearFocus()
                                if (!isSubmitting) onSubmit()
                            },
                            isLoading = isSubmitting,
                            loadingText = "Güncelleniyor...",
                            enabled = !isSubmitting,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ResetPasswordScreenNormalPreview() {
    FeniqoTheme {
        ResetPasswordScreen(
            password = "",
            confirmPassword = "",
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onPasswordVisibilityToggle = {},
            onConfirmPasswordVisibilityToggle = {},
            onSubmit = {},
            onRequestNewLink = {},
            onNavigateToLogin = {},
        )
    }
}

@Preview
@Composable
private fun ResetPasswordScreenExpiredPreview() {
    FeniqoTheme {
        ResetPasswordScreen(
            password = "",
            confirmPassword = "",
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onPasswordVisibilityToggle = {},
            onConfirmPasswordVisibilityToggle = {},
            isInvalidOrExpiredLink = true,
            onSubmit = {},
            onRequestNewLink = {},
            onNavigateToLogin = {},
        )
    }
}

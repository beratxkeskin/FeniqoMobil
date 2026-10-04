package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoPureWhite
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.account_change_email_title
import feniqomobil.sharedui.generated.resources.account_change_password_title
import feniqomobil.sharedui.generated.resources.account_contact_support
import feniqomobil.sharedui.generated.resources.account_current_email
import feniqomobil.sharedui.generated.resources.account_current_password
import feniqomobil.sharedui.generated.resources.account_delete_backup_subtitle
import feniqomobil.sharedui.generated.resources.account_delete_backup_title
import feniqomobil.sharedui.generated.resources.account_delete_before_body
import feniqomobil.sharedui.generated.resources.account_delete_before_title
import feniqomobil.sharedui.generated.resources.account_delete_shared_subtitle
import feniqomobil.sharedui.generated.resources.account_delete_shared_title
import feniqomobil.sharedui.generated.resources.account_delete_support_body
import feniqomobil.sharedui.generated.resources.account_delete_support_warning
import feniqomobil.sharedui.generated.resources.account_delete_sync_subtitle
import feniqomobil.sharedui.generated.resources.account_delete_sync_title
import feniqomobil.sharedui.generated.resources.account_delete_title
import feniqomobil.sharedui.generated.resources.account_email_verification_body
import feniqomobil.sharedui.generated.resources.account_email_verification_note
import feniqomobil.sharedui.generated.resources.account_email_verification_title
import feniqomobil.sharedui.generated.resources.account_export_data
import feniqomobil.sharedui.generated.resources.account_hide_password
import feniqomobil.sharedui.generated.resources.account_new_email
import feniqomobil.sharedui.generated.resources.account_new_email_placeholder
import feniqomobil.sharedui.generated.resources.account_return_to_sync
import feniqomobil.sharedui.generated.resources.account_send_verification
import feniqomobil.sharedui.generated.resources.account_show_password
import feniqomobil.sharedui.generated.resources.account_sign_out
import feniqomobil.sharedui.generated.resources.account_sign_out_anyway
import feniqomobil.sharedui.generated.resources.account_sign_out_pending
import feniqomobil.sharedui.generated.resources.account_sign_out_safe
import feniqomobil.sharedui.generated.resources.account_sign_out_title
import feniqomobil.sharedui.generated.resources.common_cancel
import feniqomobil.sharedui.generated.resources.password_check_email
import feniqomobil.sharedui.generated.resources.password_confirm_new_label
import feniqomobil.sharedui.generated.resources.password_edit_email
import feniqomobil.sharedui.generated.resources.password_new_label
import feniqomobil.sharedui.generated.resources.password_resend
import feniqomobil.sharedui.generated.resources.password_update
import org.jetbrains.compose.resources.stringResource

/**
 * 13 E-postayı Değiştir Ekranı.
 */
@Composable
fun ChangeEmailScreen(
    currentEmail: String,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSubmitNewEmail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newEmail by remember { mutableStateOf("") }
    val isSubmitEnabled = newEmail.isNotBlank() && newEmail.contains("@") && !isLoading

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
                title = stringResource(Res.string.account_change_email_title),
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                // Mevcut E-posta
                item {
                    Text(
                        text = stringResource(Res.string.account_current_email),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = currentEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = FeniqoSpacing.Large, vertical = 14.dp),
                        )
                    }
                }

                // Yeni E-posta
                item {
                    Text(
                        text = stringResource(Res.string.account_new_email),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        placeholder = { Text(stringResource(Res.string.account_new_email_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = FeniqoSageGreen,
                        ),
                    )
                }

                // Bilgi Notu
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
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
                            text = stringResource(Res.string.account_email_verification_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                errorMessage?.let { error ->
                    item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Button(
                onClick = { onSubmitNewEmail(newEmail.trim()) },
                enabled = isSubmitEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FeniqoTouchTarget.PrimaryAction),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = FeniqoPureWhite,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(Res.string.account_send_verification), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 14 E-posta Doğrulaması Bilgilendirme Ekranı.
 */
@Composable
fun EmailVerificationScreen(
    targetEmail: String,
    onBackToSettings: () -> Unit,
    onResendEmail: () -> Unit,
    onCorrectAddress: () -> Unit,
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
                title = stringResource(Res.string.account_email_verification_title),
                onBack = onBackToSettings,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(FeniqoSageGreenContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mail,
                        contentDescription = null,
                        tint = FeniqoSageGreen,
                        modifier = Modifier.size(40.dp),
                    )
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))

                Text(
                    text = stringResource(Res.string.password_check_email),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                Text(
                    text = stringResource(Res.string.account_email_verification_body, targetEmail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Large),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                OutlinedButton(
                    onClick = onResendEmail,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FeniqoTouchTarget.PrimaryAction),
                ) {
                    Text(stringResource(Res.string.password_resend), fontWeight = FontWeight.SemiBold)
                }

                TextButton(
                    onClick = onCorrectAddress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.password_edit_email), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * 15 Parolayı Değiştir Ekranı.
 */
@Composable
fun ChangePasswordScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSubmit: (currentPass: String, newPass: String, confirmPass: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val canSubmit = currentPassword.isNotBlank() &&
        newPassword.length >= 6 &&
        newPassword == confirmPassword &&
        !isLoading

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
                title = stringResource(Res.string.account_change_password_title),
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                // Mevcut Parola
                item {
                    Text(
                        text = stringResource(Res.string.account_current_password),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        singleLine = true,
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showCurrent = !showCurrent }) {
                                Icon(
                                    if (showCurrent) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (showCurrent) {
                                        stringResource(Res.string.account_hide_password)
                                    } else {
                                        stringResource(Res.string.account_show_password)
                                    },
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = FeniqoSageGreen,
                        ),
                    )
                }

                // Yeni Parola
                item {
                    Text(
                        text = stringResource(Res.string.password_new_label),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        singleLine = true,
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showNew = !showNew }) {
                                Icon(
                                    if (showNew) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (showNew) {
                                        stringResource(Res.string.account_hide_password)
                                    } else {
                                        stringResource(Res.string.account_show_password)
                                    },
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = FeniqoSageGreen,
                        ),
                    )
                }

                // Yeni Parola Tekrar
                item {
                    Text(
                        text = stringResource(Res.string.password_confirm_new_label),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        singleLine = true,
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showConfirm = !showConfirm }) {
                                Icon(
                                    if (showConfirm) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (showConfirm) {
                                        stringResource(Res.string.account_hide_password)
                                    } else {
                                        stringResource(Res.string.account_show_password)
                                    },
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = FeniqoSageGreen,
                        ),
                    )
                }

                errorMessage?.let { error ->
                    item {
                        Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Button(
                onClick = { onSubmit(currentPassword, newPassword, confirmPassword) },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FeniqoTouchTarget.PrimaryAction),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = FeniqoPureWhite, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.password_update), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 17 Çıkış Onayı Diyaloğu (Bekleyen sync uyarısıyla).
 */
@Composable
fun SignOutConfirmDialog(
    pendingChangesCount: Int,
    onDismiss: () -> Unit,
    onNavigateToSync: () -> Unit,
    onConfirmSignOut: () -> Unit,
) {
    val hasPending = pendingChangesCount > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.account_sign_out_title)) },
        text = {
            Text(
                if (hasPending) {
                    stringResource(Res.string.account_sign_out_pending, pendingChangesCount)
                } else {
                    stringResource(Res.string.account_sign_out_safe)
                }
            )
        },
        confirmButton = {
            if (hasPending) {
                Button(
                    onClick = onNavigateToSync,
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                ) {
                    Text(stringResource(Res.string.account_return_to_sync))
                }
            } else {
                Button(
                    onClick = onConfirmSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(Res.string.account_sign_out))
                }
            }
        },
        dismissButton = {
            Row {
                if (hasPending) {
                    TextButton(onClick = onConfirmSignOut) {
                        Text(stringResource(Res.string.account_sign_out_anyway), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.common_cancel))
                }
            }
        },
    )
}

/**
 * Pano A4: Hesap Silme Ön Hazırlık Ekranı.
 *
 * Gerçekte hesap ve yerel veri silmez; kullanıcıyı veri yedekleme, ortak alan ve
 * senkronizasyon kontrollerine yönlendirir. Kalıcı silmenin destek üzerinden yürütüldüğünü
 * şeffaf bir şekilde açıklar.
 */
@Composable
fun DeleteAccountScreen(
    onBack: () -> Unit,
    onExportData: () -> Unit,
    onCheckSharedSpaces: () -> Unit,
    onInspectSync: () -> Unit,
    onContactSupport: () -> Unit,
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
                title = stringResource(Res.string.account_delete_title),
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                contentPadding = PaddingValues(vertical = FeniqoSpacing.Medium),
            ) {
                item {
                    Column {
                        Text(
                            text = stringResource(Res.string.account_delete_before_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.account_delete_before_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Hazırlık Adımları Kart Grubu
                item {
                    SettingsGroupCard {
                        SettingsRowItem(
                            title = stringResource(Res.string.account_delete_backup_title),
                            subtitle = stringResource(Res.string.account_delete_backup_subtitle),
                            icon = Icons.Outlined.Description,
                            onClick = onExportData,
                        )
                        SettingsRowItem(
                            title = stringResource(Res.string.account_delete_shared_title),
                            subtitle = stringResource(Res.string.account_delete_shared_subtitle),
                            icon = Icons.Outlined.Group,
                            onClick = onCheckSharedSpaces,
                        )
                        SettingsRowItem(
                            title = stringResource(Res.string.account_delete_sync_title),
                            subtitle = stringResource(Res.string.account_delete_sync_subtitle),
                            icon = Icons.Outlined.Sync,
                            showDivider = false,
                            onClick = onInspectSync,
                        )
                    }
                }

                // Pano A4: Kırmızı Şeffaf Bilgilendirme Kutusu
                item {
                    Card(
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(FeniqoSpacing.Large),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(top = 2.dp),
                            )
                            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                            Column {
                                Text(
                                    text = stringResource(Res.string.account_delete_support_warning),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(Res.string.account_delete_support_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                                )
                            }
                        }
                    }
                }
            }

            // Alt Eylem Butonları: "Verilerimi dışa aktar" ve "Destekle iletişime geç"
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                Button(
                    onClick = onExportData,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FeniqoSageGreen,
                        contentColor = FeniqoPureWhite,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FeniqoTouchTarget.PrimaryAction),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                    Text(stringResource(Res.string.account_export_data), fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onContactSupport,
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FeniqoTouchTarget.PrimaryAction),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                    Text(stringResource(Res.string.account_contact_support), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

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
                title = "E-postayı değiştir",
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                // Mevcut E-posta
                item {
                    Text(
                        text = "Mevcut e-posta adresi",
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
                        text = "Yeni e-posta",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        placeholder = { Text("yeni@example.com") },
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
                            text = "Yeni adres doğrulanmadan değişiklik tamamlanmaz.",
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
                    Text("Doğrulama gönder", fontWeight = FontWeight.SemiBold)
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
                title = "E-posta doğrulaması",
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
                    text = "E-postanı kontrol et",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                Text(
                    text = "$targetEmail adresine doğrulama bağlantısı gönderdik. Lütfen e-postanı kontrol et ve bağlantıya tıkla.",
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
                    Text("Tekrar gönder", fontWeight = FontWeight.SemiBold)
                }

                TextButton(
                    onClick = onCorrectAddress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Adresi düzelt", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                title = "Parolayı değiştir",
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                // Mevcut Parola
                item {
                    Text("Mevcut parola", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
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
                                Icon(if (showCurrent) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
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
                    Text("Yeni parola", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
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
                                Icon(if (showNew) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
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
                    Text("Yeni parola tekrar", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
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
                                Icon(if (showConfirm) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
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
                    Text("Parolayı güncelle", fontWeight = FontWeight.SemiBold)
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
        title = { Text("Çıkış yapmak istiyor musun?") },
        text = {
            Text(
                if (hasPending) {
                    "$pendingChangesCount değişiklik henüz eşitlenmedi. Çıkış yaparsanız bu değişiklikler henüz sunucuya aktarılmamış olabilir. Çıkıştan önce eşitlemenizi öneririz."
                } else {
                    "Bu cihazdaki oturumunuz güvenle kapatılacak."
                }
            )
        },
        confirmButton = {
            if (hasPending) {
                Button(
                    onClick = onNavigateToSync,
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                ) {
                    Text("Eşitlemeye dön")
                }
            } else {
                Button(
                    onClick = onConfirmSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Çıkış Yap")
                }
            }
        },
        dismissButton = {
            Row {
                if (hasPending) {
                    TextButton(onClick = onConfirmSignOut) {
                        Text("Yine de çık", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Vazgeç")
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
                title = "Hesap silme",
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
                            text = "Hesabını silmeden önce",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hesabını silmek kalıcı bir işlemdir. Devam etmeden önce aşağıdaki adımları tamamlamanı öneririz.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Hazırlık Adımları Kart Grubu
                item {
                    SettingsGroupCard {
                        SettingsRowItem(
                            title = "Verilerini yedekle",
                            subtitle = "Kişisel kategorilerini ve işlemlerini dışa aktararak cihazında saklayabilirsin. Makbuzlar ve ortak alanlar bu yedeğe dahil değildir.",
                            icon = Icons.Outlined.Description,
                            onClick = onExportData,
                        )
                        SettingsRowItem(
                            title = "Ortak alanlarını kontrol et",
                            subtitle = "Paylaştığın bütçe, hedef veya ortak alanlarda başka kişilerin erişimi olabilir.",
                            icon = Icons.Outlined.Group,
                            onClick = onCheckSharedSpaces,
                        )
                        SettingsRowItem(
                            title = "Eşitlenmemiş değişiklikleri incele",
                            subtitle = "Henüz eşitlenmemiş işlemlerinin olup olmadığını kontrol et.",
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
                            containerColor = Color(0xFFFDECEA),
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0xFFE57373).copy(alpha = 0.4f),
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
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(top = 2.dp),
                            )
                            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                            Column {
                                Text(
                                    text = "Kalıcı silme şu anda destek üzerinden yürütülür. Bu ekrandan veriler silinmez.",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFD32F2F),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Hesabını silmek için bizimle iletişime geçmen gerekmektedir.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD32F2F).copy(alpha = 0.9f),
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
                    Text("Verilerimi dışa aktar", fontWeight = FontWeight.SemiBold)
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
                    Text("Destekle iletişime geç", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

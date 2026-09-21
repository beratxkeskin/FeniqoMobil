package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.component.SettingsDangerItem
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
 * 02 Hesabım Ekranı ve 16 Fotoğraf Seçim Sheet'i.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    displayName: String,
    email: String,
    emailVerificationStatus: com.feniqo.mobile.domain.model.EmailVerificationStatus,
    loginMethod: String,
    onBack: () -> Unit,
    onNavigateToPersonalInfo: () -> Unit,
    onNavigateToChangeEmail: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToDeleteAccount: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
    avatarBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
) {
    var showPhotoSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = FeniqoSpacing.Screen,
                vertical = FeniqoSpacing.Medium,
            ),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
        ) {
            item {
                SettingsTopBar(
                    title = "Profil fotoğrafı",
                    onBack = onBack,
                )
            }

            // Pano A1: Avatar ve Kullanıcı Başlığı
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = FeniqoSpacing.Medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .background(FeniqoSageGreenContainer)
                            .clickable { showPhotoSheet = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = avatarBitmap,
                                contentDescription = "Profil fotoğrafı",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            val initials = displayName.trim().split(" ")
                                .mapNotNull { it.firstOrNull()?.uppercase() }
                                .take(2)
                                .joinToString("")
                                .ifBlank { "F" }
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                color = FeniqoSageGreen,
                            )
                        }

                        // Kamera rozeti
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 4.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(FeniqoSageGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Fotoğrafı değiştir",
                                tint = FeniqoPureWhite,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Hesap ayarlarınla ilgili bilgiler burada yer alır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Hesap Satırları
            item {
                val (badgeText, badgeColor) = when (emailVerificationStatus) {
                    com.feniqo.mobile.domain.model.EmailVerificationStatus.VERIFIED ->
                        "Doğrulandı" to Color(0xFF2E7D32)
                    com.feniqo.mobile.domain.model.EmailVerificationStatus.PENDING_VERIFICATION ->
                        "Doğrulama bekleniyor" to Color(0xFFE65100)
                    com.feniqo.mobile.domain.model.EmailVerificationStatus.UNKNOWN ->
                        "Durum alınamadı" to Color(0xFF757575)
                }

                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Kişisel bilgiler",
                        subtitle = "Ad ve profil detayları",
                        icon = Icons.Outlined.Person,
                        onClick = onNavigateToPersonalInfo,
                    )
                    SettingsRowItem(
                        title = "E-posta",
                        subtitle = email.ifBlank { "Belirtilmemiş" },
                        icon = Icons.Outlined.Mail,
                        badgeText = badgeText,
                        badgeColor = badgeColor,
                        onClick = onNavigateToChangeEmail,
                    )
                    SettingsRowItem(
                        title = "Giriş yöntemi",
                        subtitle = loginMethod,
                        icon = Icons.Outlined.Key,
                        showChevron = false,
                        onClick = null,
                    )
                    SettingsRowItem(
                        title = "Parolayı değiştir",
                        subtitle = "Hesap giriş şifrenizi güncelleyin",
                        icon = Icons.Outlined.LockReset,
                        showDivider = false,
                        onClick = onNavigateToChangePassword,
                    )
                }
            }

            // Hesabı Sil Eylemi (Pano A4'e yönlendirir)
            item {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                SettingsDangerItem(
                    title = "Hesabı sil",
                    icon = Icons.Outlined.DeleteOutline,
                    onClick = onNavigateToDeleteAccount,
                )
            }
        }
    }

    // Pano A1: Profil Fotoğrafı Seçimi Modal Bottom Sheet
    if (showPhotoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = FeniqoRadius.Large, topEnd = FeniqoRadius.Large),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Galeriden seç",
                        icon = Icons.Outlined.Image,
                        onClick = {
                            showPhotoSheet = false
                            onPickFromGallery()
                        },
                    )
                    SettingsRowItem(
                        title = "Fotoğraf çek",
                        icon = Icons.Outlined.CameraAlt,
                        showDivider = false,
                        onClick = {
                            showPhotoSheet = false
                            onTakePhoto()
                        },
                    )
                }

                SettingsDangerItem(
                    title = "Fotoğrafı kaldır",
                    icon = Icons.Outlined.Delete,
                    showChevron = true,
                    onClick = {
                        showPhotoSheet = false
                        onRemovePhoto()
                    },
                )

                // Güvenlik Açıklaması
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = FeniqoSpacing.Small),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Yalnızca bu cihazda saklanır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))
            }
        }
    }
}

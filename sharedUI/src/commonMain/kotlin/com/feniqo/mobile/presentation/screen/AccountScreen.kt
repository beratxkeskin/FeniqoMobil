package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EmailVerificationStatus
import com.feniqo.mobile.presentation.component.SettingsDangerItem
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.settings.AccountLoginMethod
import com.feniqo.mobile.presentation.settings.toLocalizedText
import com.feniqo.mobile.presentation.theme.FeniqoPureWhite
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSageGreenContainer
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.account_login_method_account
import feniqomobil.sharedui.generated.resources.account_login_method_email
import feniqomobil.sharedui.generated.resources.account_row_change_password
import feniqomobil.sharedui.generated.resources.account_row_change_password_subtitle
import feniqomobil.sharedui.generated.resources.account_row_delete_account
import feniqomobil.sharedui.generated.resources.account_row_email
import feniqomobil.sharedui.generated.resources.account_row_login_method
import feniqomobil.sharedui.generated.resources.account_row_personal_info
import feniqomobil.sharedui.generated.resources.account_row_personal_info_subtitle
import feniqomobil.sharedui.generated.resources.account_screen_subtitle
import feniqomobil.sharedui.generated.resources.account_screen_title
import feniqomobil.sharedui.generated.resources.email_settings_status_pending
import feniqomobil.sharedui.generated.resources.email_settings_status_unknown
import feniqomobil.sharedui.generated.resources.email_settings_status_verified
import feniqomobil.sharedui.generated.resources.email_settings_unspecified
import feniqomobil.sharedui.generated.resources.profile_photo_action_camera
import feniqomobil.sharedui.generated.resources.profile_photo_action_gallery
import feniqomobil.sharedui.generated.resources.profile_photo_action_remove
import feniqomobil.sharedui.generated.resources.profile_photo_change_action
import feniqomobil.sharedui.generated.resources.profile_photo_content_description
import feniqomobil.sharedui.generated.resources.profile_photo_stored_locally
import feniqomobil.sharedui.generated.resources.profile_route_default_user_name
import org.jetbrains.compose.resources.stringResource

/**
 * 02 Hesabım Ekranı ve 16 Fotoğraf Seçim Sheet'i.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    displayName: String? = null,
    email: String,
    emailVerificationStatus: EmailVerificationStatus,
    loginMethod: AccountLoginMethod = AccountLoginMethod.EMAIL,
    onBack: () -> Unit,
    onNavigateToPersonalInfo: () -> Unit,
    onNavigateToChangeEmail: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToDeleteAccount: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
    avatarBitmap: ImageBitmap? = null,
) {
    var showPhotoSheet by remember { mutableStateOf(false) }
    val effectiveDisplayName = displayName?.trim()?.ifBlank { null }
        ?: stringResource(Res.string.profile_route_default_user_name)

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
                    title = stringResource(Res.string.account_screen_title),
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
                            Image(
                                bitmap = avatarBitmap,
                                contentDescription = stringResource(Res.string.profile_photo_content_description),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            val initials = effectiveDisplayName.split(" ")
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
                                contentDescription = stringResource(Res.string.profile_photo_change_action),
                                tint = FeniqoPureWhite,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

                    Text(
                        text = effectiveDisplayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(Res.string.account_screen_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Hesap Satırları
            item {
                val (badgeText, badgeColor) = when (emailVerificationStatus) {
                    EmailVerificationStatus.VERIFIED ->
                        stringResource(Res.string.email_settings_status_verified) to Color(0xFF2E7D32)
                    EmailVerificationStatus.PENDING_VERIFICATION ->
                        stringResource(Res.string.email_settings_status_pending) to Color(0xFFE65100)
                    EmailVerificationStatus.UNKNOWN ->
                        stringResource(Res.string.email_settings_status_unknown) to Color(0xFF757575)
                }

                SettingsGroupCard {
                    SettingsRowItem(
                        title = stringResource(Res.string.account_row_personal_info),
                        subtitle = stringResource(Res.string.account_row_personal_info_subtitle),
                        icon = Icons.Outlined.Person,
                        onClick = onNavigateToPersonalInfo,
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.account_row_email),
                        subtitle = email.ifBlank { stringResource(Res.string.email_settings_unspecified) },
                        icon = Icons.Outlined.Mail,
                        badgeText = badgeText,
                        badgeColor = badgeColor,
                        onClick = onNavigateToChangeEmail,
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.account_row_login_method),
                        subtitle = loginMethod.toLocalizedText(),
                        icon = Icons.Outlined.Key,
                        showChevron = false,
                        onClick = null,
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.account_row_change_password),
                        subtitle = stringResource(Res.string.account_row_change_password_subtitle),
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
                    title = stringResource(Res.string.account_row_delete_account),
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
                        title = stringResource(Res.string.profile_photo_action_gallery),
                        icon = Icons.Outlined.Image,
                        onClick = {
                            showPhotoSheet = false
                            onPickFromGallery()
                        },
                    )
                    SettingsRowItem(
                        title = stringResource(Res.string.profile_photo_action_camera),
                        icon = Icons.Outlined.CameraAlt,
                        showDivider = false,
                        onClick = {
                            showPhotoSheet = false
                            onTakePhoto()
                        },
                    )
                }

                SettingsDangerItem(
                    title = stringResource(Res.string.profile_photo_action_remove),
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
                        text = stringResource(Res.string.profile_photo_stored_locally),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))
            }
        }
    }
}

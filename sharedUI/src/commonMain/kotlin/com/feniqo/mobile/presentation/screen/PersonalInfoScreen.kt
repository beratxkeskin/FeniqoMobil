package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import feniqomobil.sharedui.generated.resources.email_settings_unspecified
import feniqomobil.sharedui.generated.resources.personal_info_change_email_subtitle
import feniqomobil.sharedui.generated.resources.personal_info_change_email_title
import feniqomobil.sharedui.generated.resources.personal_info_change_photo
import feniqomobil.sharedui.generated.resources.personal_info_email_label
import feniqomobil.sharedui.generated.resources.personal_info_name_label
import feniqomobil.sharedui.generated.resources.personal_info_name_placeholder
import feniqomobil.sharedui.generated.resources.personal_info_save_button
import feniqomobil.sharedui.generated.resources.personal_info_title
import org.jetbrains.compose.resources.stringResource

/**
 * 03 Kişisel Bilgiler Ekranı.
 * Ad, Soyad ve salt okunur e-posta alanları ile e-posta değiştirme yönlendirmesi.
 */
@Composable
fun PersonalInfoScreen(
    currentFullName: String,
    email: String,
    isLoading: Boolean,
    onBack: () -> Unit,
    onChangePhoto: () -> Unit,
    onNavigateToChangeEmail: () -> Unit,
    onSave: (fullName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayNameInput by remember(currentFullName) { mutableStateOf(currentFullName) }
    val isDirty = displayNameInput.trim() != currentFullName.trim()

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
                title = stringResource(Res.string.personal_info_title),
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                // Avatar & Fotoğrafı Değiştir Butonu
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FeniqoSpacing.Small),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(FeniqoSageGreenContainer)
                                .clickable(onClick = onChangePhoto),
                            contentAlignment = Alignment.Center,
                        ) {
                            val initials = displayNameInput.trim().split(" ")
                                .mapNotNull { it.firstOrNull()?.uppercase() }
                                .take(2)
                                .joinToString("")
                                .ifBlank { "F" }
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = FeniqoSageGreen,
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(FeniqoSageGreen),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = stringResource(Res.string.personal_info_change_photo),
                                    tint = FeniqoPureWhite,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                        TextButton(onClick = onChangePhoto) {
                            Text(
                                text = stringResource(Res.string.personal_info_change_photo),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Görünen Ad Alanı (Domain Sözleşmesi: fullName)
                item {
                    Text(
                        text = stringResource(Res.string.personal_info_name_label),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = { displayNameInput = it },
                        placeholder = { Text(stringResource(Res.string.personal_info_name_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = FeniqoSageGreen,
                        ),
                    )
                }

                // E-posta Salt Okunur Alanı
                item {
                    Text(
                        text = stringResource(Res.string.personal_info_email_label),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = email.ifBlank { stringResource(Res.string.email_settings_unspecified) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = FeniqoSpacing.Large, vertical = 14.dp),
                        )
                    }
                }

                // E-postayı Değiştir Butonu
                item {
                    SettingsGroupCard {
                        SettingsRowItem(
                            title = stringResource(Res.string.personal_info_change_email_title),
                            subtitle = stringResource(Res.string.personal_info_change_email_subtitle),
                            icon = Icons.Outlined.Mail,
                            showDivider = false,
                            onClick = onNavigateToChangeEmail,
                        )
                    }
                }
            }

            // Kaydet Butonu
            Button(
                onClick = { onSave(displayNameInput.trim()) },
                enabled = isDirty && !isLoading,
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FeniqoTouchTarget.PrimaryAction)
                    .padding(vertical = 4.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = FeniqoPureWhite,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.personal_info_save_button),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

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
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
                title = "Kişisel bilgiler",
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
                                    contentDescription = "Fotoğrafı değiştir",
                                    tint = FeniqoPureWhite,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                        TextButton(onClick = onChangePhoto) {
                            Text(
                                text = "Fotoğrafı değiştir",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Görünen Ad Alanı (Domain Sözleşmesi: fullName)
                item {
                    Text(
                        text = "Görünen ad",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = { displayNameInput = it },
                        placeholder = { Text("Adınızı veya tam adınızı girin") },
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
                        text = "E-posta",
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
                            text = email.ifBlank { "Belirtilmemiş" },
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
                            title = "E-postayı değiştir",
                            subtitle = "Doğrulanmış yeni adres belirle",
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
                        text = "Değişiklikleri kaydet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

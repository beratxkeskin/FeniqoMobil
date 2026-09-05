package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.presentation.budget.CategoryIconResolver
import com.feniqo.mobile.presentation.goal.GoalDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.util.ColorParser

/**
 * Birikim hedefi kartı bileşenidir.
 */
@Composable
fun GoalCard(
    item: GoalDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = if (item.isAchieved) "Tamamlandı" else "Devam Ediyor"
    val avatarBgColor = ColorParser.parseHexColorOrNull(item.colorHex)
        ?: MaterialTheme.colorScheme.primaryContainer

    val iconEmoji = CategoryIconResolver.resolveIconEmojiOrNull(item.iconKey)
    val fallbackInitial = item.name.trim().firstOrNull()?.uppercase() ?: "🎯"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Medium))
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "${item.name}, Hedef: ${item.formattedTargetAmount}, Biriken: ${item.formattedCurrentAmount}, Kalan: ${item.formattedRemainingAmount}, Hedef Tarihi: ${item.formattedTargetDate}, Durum: $statusText"
            },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // İkon Avatarı
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(avatarBgColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (iconEmoji != null) {
                        Text(
                            text = iconEmoji,
                            fontSize = 18.sp,
                        )
                    } else {
                        Text(
                            text = fallbackInitial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = avatarBgColor,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

                // Hedef Adı ve Durum Rozeti
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Hedef: ${item.formattedTargetDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

                // Durum Rozeti
                GoalStatusBadge(status = item.status)
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // İlerleme Çubuğu
            LinearProgressIndicator(
                progress = { item.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (item.isAchieved) FeniqoStatusColor.Success else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

            // Tutarlar Satırı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Biriken / Hedef",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${item.formattedCurrentAmount} / ${item.formattedTargetAmount}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Kalan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.formattedRemainingAmount,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isAchieved) FeniqoStatusColor.Success else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalStatusBadge(
    status: GoalStatus,
    modifier: Modifier = Modifier,
) {
    val (text, containerColor, contentColor) = when (status) {
        GoalStatus.ACHIEVED -> Triple(
            "Tamamlandı",
            FeniqoStatusColor.Success.copy(alpha = 0.15f),
            FeniqoStatusColor.Success,
        )
        GoalStatus.IN_PROGRESS -> Triple(
            "Devam Ediyor",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary,
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(FeniqoRadius.Small),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 3.dp),
        )
    }
}

@Composable
fun GoalDeleteDialog(
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        modifier = modifier.semantics {
            contentDescription = "Hedef silme onay diyaloğu"
        },
        title = {
            Text(
                text = "Hedefi Sil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "Bu birikim hedefini silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onConfirm,
                enabled = !isSubmitting,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (isSubmitting) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Sil")
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
            ) {
                Text("Vazgeç")
            }
        },
    )
}


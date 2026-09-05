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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.DebtStatus
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.presentation.debt.DebtDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor

/**
 * Borç / Alacak kartı bileşenidir.
 */
@Composable
fun DebtCard(
    item: DebtDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = if (item.isOpen) "Açık" else "Kapandı"
    val avatarBgColor = if (item.type == DebtType.RECEIVABLE) {
        FeniqoStatusColor.Success
    } else {
        MaterialTheme.colorScheme.primary
    }
    val iconEmoji = if (item.type == DebtType.RECEIVABLE) "🤝" else "💳"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Medium))
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "${item.title}, ${item.typeLabel}, Tutar: ${item.formattedPrincipalAmount}, Vade: ${item.formattedDueDate}, Durum: $statusText${if (!item.description.isNullOrBlank()) ", Açıklama: ${item.description}" else ""}"
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
                        .background(avatarBgColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = iconEmoji,
                        fontSize = 18.sp,
                    )
                }

                Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

                // Başlık ve Vade Tarihi
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Vade: ${item.formattedDueDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

                // Tür ve Durum Rozetleri
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DebtTypeBadge(type = item.type, label = item.typeLabel)
                    DebtStatusBadge(status = item.status)
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Tutarlar (Toplam, Ödenen/Tahsil Edilen, Kalan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Toplam Tutar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.formattedPrincipalAmount,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val remainingLabel = if (item.type == DebtType.RECEIVABLE) "Kalan Alacak" else "Kalan Borç"
                    Text(
                        text = remainingLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.formattedRemainingAmount,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isSettled) FeniqoStatusColor.Success else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Ödenen / Tahsil Edilen satırı (Eğer ödeme varsa)
            if (item.totalPaid.amountMinor > 0L) {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val paidLabel = if (item.type == DebtType.RECEIVABLE) "Tahsil Edilen" else "Ödenen"
                    Text(
                        text = paidLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.formattedTotalPaid,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Açıklama (varsa)
            if (!item.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DebtTypeBadge(
    type: DebtType,
    label: String,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) = when (type) {
        DebtType.RECEIVABLE -> Pair(
            FeniqoStatusColor.Success.copy(alpha = 0.15f),
            FeniqoStatusColor.Success,
        )
        DebtType.DEBT -> Pair(
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
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 3.dp),
        )
    }
}

@Composable
private fun DebtStatusBadge(
    status: DebtStatus,
    modifier: Modifier = Modifier,
) {
    val (text, containerColor, contentColor) = when (status) {
        DebtStatus.OPEN -> Triple(
            "Açık",
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.secondary,
        )
        DebtStatus.SETTLED -> Triple(
            "Kapandı",
            FeniqoStatusColor.Success.copy(alpha = 0.15f),
            FeniqoStatusColor.Success,
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
fun DebtDeleteDialog(
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
            contentDescription = "Borç alacak silme onay diyaloğu"
        },
        title = {
            Text(
                text = "Kaydı Sil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "Bu borç / alacak kaydını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.",
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


package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.validation.SubscriptionRenewalStatus
import com.feniqo.mobile.presentation.budget.CategoryIconResolver
import com.feniqo.mobile.presentation.subscription.SubscriptionDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Abonelik kaydını listeleyen kart bileşenidir.
 */
@Composable
fun SubscriptionCard(
    item: SubscriptionDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusDescription = if (item.isPaused) "Duraklatıldı" else "Aktif"
    val (renewalStatusText, renewalStatusColor) = resolveRenewalStatusVisuals(item)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "${item.name} aboneliği",
                onClick = onClick,
            )
            .semantics {
                contentDescription = "${item.name}, ${item.categoryName}, ${item.formattedAmount}, ${item.formattedFrequency}, $renewalStatusText, $statusDescription"
            },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Kategori İkon ve Renk Göstergesi
            SubscriptionCategoryAvatar(
                iconKey = item.categoryIconKey,
                colorHex = item.categoryColorHex,
                isCategoryMissing = item.isCategoryMissing,
                isCategoryUnassigned = item.isCategoryUnassigned,
            )

            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

            // Sol Metin Bloğu: Abonelik Adı, Kategori/Frekans ve Yenileme Durumu
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

                    SubscriptionStatusBadge(isPaused = item.isPaused)
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))

                Text(
                    text = "${item.categoryName} • ${item.formattedFrequency}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = renewalStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = renewalStatusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

            // Sağ Metin Bloğu: Tutar
            Text(
                text = item.formattedAmount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Kategoriye ait renkli daire avatar ve emoji simgesidir.
 */
@Composable
fun SubscriptionCategoryAvatar(
    iconKey: String?,
    colorHex: String?,
    isCategoryMissing: Boolean,
    isCategoryUnassigned: Boolean,
    modifier: Modifier = Modifier,
) {
    val categoryColor = when {
        isCategoryMissing -> MaterialTheme.colorScheme.outline
        isCategoryUnassigned -> MaterialTheme.colorScheme.surfaceVariant
        else -> ColorParser.parseHexColorOrNull(colorHex) ?: MaterialTheme.colorScheme.primaryContainer
    }

    val emoji = when {
        isCategoryMissing -> "❓"
        isCategoryUnassigned -> "📦"
        else -> CategoryIconResolver.resolveIconEmojiOrNull(iconKey) ?: "💳"
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .background(categoryColor.copy(alpha = 0.2f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * Aktif / Duraklatıldı rozeti.
 */
@Composable
fun SubscriptionStatusBadge(
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor, text) = if (isPaused) {
        Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Duraklatıldı",
        )
    } else {
        Triple(
            FeniqoStatusColor.Success.copy(alpha = 0.15f),
            FeniqoStatusColor.Success,
            "Aktif",
        )
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = "Durum: $text"
        },
        shape = RoundedCornerShape(FeniqoRadius.Small),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 2.dp),
        )
    }
}

@Composable
private fun resolveRenewalStatusVisuals(item: SubscriptionDisplayModel): Pair<String, Color> =
    when (val status = item.renewalStatus) {
        SubscriptionRenewalStatus.Inactive -> {
            "Duraklatıldı" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        is SubscriptionRenewalStatus.Overdue -> {
            "${status.daysOverdue} gün gecikti (${item.formattedNextRenewalDate})" to FeniqoStatusColor.Error
        }
        SubscriptionRenewalStatus.DueToday -> {
            "Bugün yenilenecek" to FeniqoStatusColor.Warning
        }
        is SubscriptionRenewalStatus.Upcoming -> {
            "${status.daysUntilRenewal} gün kaldı (${item.formattedNextRenewalDate})" to MaterialTheme.colorScheme.primary
        }
        is SubscriptionRenewalStatus.Scheduled -> {
            "Sonraki: ${item.formattedNextRenewalDate}" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

/**
 * Abonelik silme onay diyaloğudur.
 */
@Composable
fun SubscriptionDeleteDialog(
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
            contentDescription = "Abonelik silme onay diyaloğu"
        },
        title = {
            Text(
                text = "Aboneliği Sil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "Bu abonelik kaydını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz ve gelecekteki yenileme takibi durdurulur.",
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
                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                }
                Text("Sil")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
            ) {
                Text("İptal")
            }
        },
    )
}

/**
 * Abonelik vadesini tek periyot ilerletme (ödendi işaretleme) onay diyaloğudur.
 */
@Composable
fun SubscriptionAdvanceRenewalDialog(
    nextRenewalDate: LocalDate,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedDate = DateFormatter.formatReadableDate(nextRenewalDate)
    androidx.compose.material3.AlertDialog(

        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        modifier = modifier.semantics {
            contentDescription = "Abonelik yenileme onay diyaloğu"
        },
        title = {
            Text(
                text = "Yenilemeyi Onayla",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "$formattedDate tarihli yenilemenin ödendiğini onaylıyor musunuz? Bu işlem aboneliğin yenileme vadesini bir sonraki döneme ilerletecektir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onConfirm,
                enabled = !isSubmitting,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (isSubmitting) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                }
                Text("Ödendi Olarak İşaretle")
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



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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.budget.CategoryIconResolver
import com.feniqo.mobile.presentation.recurring.RecurringTransactionDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.util.ColorParser

/**
 * Tekrarlayan işlem kuralını listeleyen kart bileşenidir.
 */
@Composable
fun RecurringTransactionCard(
    item: RecurringTransactionDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusDescription = if (item.isPaused) "Duraklatıldı" else "Aktif"
    val nextDateText = if (item.formattedNextOccurrenceDate != null) {
        "Sonraki: ${item.formattedNextOccurrenceDate}"
    } else {
        "Tekrar sona erdi"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "${item.categoryName} tekrarlayan işlemi",
                onClick = onClick,
            )
            .semantics {
                contentDescription = "${item.categoryName}, ${item.formattedAmount}, ${item.formattedFrequency}, $nextDateText, $statusDescription"
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
            RecurringCategoryAvatar(
                iconKey = item.categoryIconKey,
                colorHex = item.categoryColorHex,
                isCategoryMissing = item.isCategoryMissing,
            )

            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

            // Sol Metin Bloğu: Kategori Adı, Frekans ve Sonraki Vade
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

                    RecurringStatusBadge(isPaused = item.isPaused)
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))

                Text(
                    text = item.formattedFrequency,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = nextDateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.formattedNextOccurrenceDate != null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

            // Sağ Metin Bloğu: Tutar
            val amountColor = when (item.type) {
                TransactionType.INCOME -> FeniqoStatusColor.Success
                TransactionType.EXPENSE -> MaterialTheme.colorScheme.onSurface
            }

            Text(
                text = item.formattedAmount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
        }
    }
}

/**
 * Kategoriye ait renkli daire avatar ve emoji simgesidir.
 */
@Composable
private fun RecurringCategoryAvatar(
    iconKey: String?,
    colorHex: String?,
    isCategoryMissing: Boolean,
    modifier: Modifier = Modifier,
) {
    val categoryColor = ColorParser.parseHexColorOrNull(colorHex)
        ?: if (isCategoryMissing) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primaryContainer
    val emoji = CategoryIconResolver.resolveIconEmojiOrNull(iconKey) ?: "🔄"

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
fun RecurringStatusBadge(
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

/**
 * Tekrarlayan işlem kuralı silme onay diyaloğudur.
 */
@Composable
fun RecurringTransactionDeleteDialog(
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
            contentDescription = "Tekrarlayan işlem silme onay diyaloğu"
        },
        title = {
            Text(
                text = "Tekrarlayan İşlemi Sil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "Bu tekrarlayan işlem kuralını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz ve gelecekteki otomatik tekrarlar durdurulur.",
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


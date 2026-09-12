package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel
import com.feniqo.mobile.presentation.util.ColorParser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * İşlem listesi için tarih grubu başlığı bileşenidir.
 * Sağ tarafta para güvenliği doğrulanmış günlük net toplamı görüntüler.
 */
@Composable
fun TransactionDateGroupHeader(
    formattedDate: String,
    secondaryDateText: String? = null,
    dailyNetFormatted: String? = null,
    isDailyNetNegative: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val fullDateLabel = if (!secondaryDateText.isNullOrBlank()) {
        "$formattedDate, $secondaryDateText"
    } else {
        formattedDate
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!secondaryDateText.isNullOrBlank()) {
                Text(
                    text = secondaryDateText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!dailyNetFormatted.isNullOrBlank()) {
            val netDescription = if (isDailyNetNegative) {
                "Günlük net gider: $dailyNetFormatted"
            } else {
                "Günlük net gelir: $dailyNetFormatted"
            }
            Text(
                text = dailyNetFormatted,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                ),
                color = if (isDailyNetNegative) MaterialTheme.colorScheme.onSurfaceVariant else FeniqoStatusColor.Success,
                modifier = Modifier.semantics {
                    contentDescription = "$fullDateLabel için $netDescription"
                },
            )
        }
    }
}

/**
 * Taksitli işlemler için rozet bileşenidir.
 */
@Composable
fun InstallmentBadge(
    badgeText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics {
            contentDescription = "Taksit $badgeText"
        },
        shape = RoundedCornerShape(FeniqoRadius.Small),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 2.dp),
        )
    }
}

/**
 * Makbuz içeren işlemler için gösterge rozetidir.
 */
@Composable
fun ReceiptBadge(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics {
            contentDescription = "Makbuz mevcut"
        },
        shape = RoundedCornerShape(FeniqoRadius.Small),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text = "Makbuz",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 2.dp),
        )
    }
}

/**
 * Tekil işlem satırı bileşenidir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionListItem(
    item: TransactionDisplayModel,
    canEditTransaction: Boolean,
    isDeleteInProgress: Boolean,
    onTransactionClick: (TransactionDisplayModel) -> Unit,
    onDeleteClicked: (TransactionDisplayModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isClickable = canEditTransaction && item.canEdit
    val cardModifier = if (isClickable) {
        modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "${item.categoryName} işlemini düzenle",
            ) {
                onTransactionClick(item)
            }
    } else {
        modifier.fillMaxWidth()
    }

    // Giderler lüks koyu kömür / onSurface rengi, gelirler zümrüt yeşili
    val amountColor = if (item.type == TransactionType.INCOME) {
        FeniqoStatusColor.Success
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val title = if (!item.description.isNullOrBlank()) item.description else item.categoryName
    val subtitle = if (!item.description.isNullOrBlank()) {
        "${item.categoryName} • ${item.paymentMethod.toDisplayText()}"
    } else {
        item.paymentMethod.toDisplayText()
    }

    Card(
        modifier = cardModifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sol: 42dp Tonal Kategori İkonu
            val categoryColor = ColorParser.parseHexColorOrNull(item.categoryColorHex)
                ?: Color(0xFF4E735F)
            CategoryTonalIcon(
                iconKey = item.categoryIconKey,
                color = categoryColor,
                containerSize = 42.dp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Orta: Başlık, kategori, rozetler
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.installment != null || item.hasReceipt) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        if (item.installment != null) {
                            InstallmentBadge(badgeText = item.installment.badgeText)
                        }
                        if (item.hasReceipt) {
                            ReceiptBadge()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Sağ: Tutar ve Zarif İnce Chevron
            val typePrefix = if (item.type == TransactionType.INCOME) "Gelir" else "Gider"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.formattedAmount,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                    ),
                    color = amountColor,
                    maxLines = 1,
                    modifier = Modifier.semantics {
                        contentDescription = "$typePrefix: ${item.formattedAmount}"
                    },
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * PaymentMethod enum değerlerinin kullanıcı dostu Türkçe karşılıkları.
 */
fun PaymentMethod.toDisplayText(): String = when (this) {
    PaymentMethod.CASH -> "Nakit"
    PaymentMethod.CREDIT_CARD -> "Kredi Kartı"
    PaymentMethod.DEBIT_CARD -> "Banka Kartı"
    PaymentMethod.BANK_TRANSFER -> "Havale / EFT"
    PaymentMethod.OTHER -> "Diğer"
}

/**
 * TransactionType enum değerlerinin kullanıcı dostu Türkçe karşılıkları.
 */
fun TransactionType.toDisplayText(): String = when (this) {
    TransactionType.EXPENSE -> "Gider"
    TransactionType.INCOME -> "Gelir"
}

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

/**
 * İşlem listesi için tarih grubu başlığı bileşenidir.
 */
@Composable
fun TransactionDateGroupHeader(
    formattedDate: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = FeniqoSpacing.Large,
                vertical = FeniqoSpacing.Small,
            ),
        )
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

    val categoryColor = ColorParser.parseHexColorOrNull(item.categoryColorHex)
        ?: MaterialTheme.colorScheme.surfaceVariant

    val amountColor = if (item.type == TransactionType.INCOME) {
        FeniqoStatusColor.Success
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Sol taraf: Kategori rengi, adı, açıklama, ödeme yöntemi, rozetler
            Column(
                modifier = Modifier
                    .weight(0.68f)
                    .padding(end = FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(categoryColor, CircleShape),
                    )
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!item.description.isNullOrBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                    verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                ) {
                    Text(
                        text = item.paymentMethod.toDisplayText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (item.installment != null) {
                        InstallmentBadge(badgeText = item.installment.badgeText)
                    }

                    if (item.hasReceipt) {
                        ReceiptBadge()
                    }
                }
            }

            // Sağ taraf: Tutar ve Sil Butonu
            Column(
                modifier = Modifier.weight(0.32f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
            ) {
                Text(
                    text = item.formattedAmount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics {
                        contentDescription = item.formattedAmount
                    },
                )

                TextButton(
                    onClick = { onDeleteClicked(item) },
                    enabled = item.canDelete && !isDeleteInProgress,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "${item.categoryName} ${item.formattedAmount} işlemini sil"
                        },
                ) {
                    Text(
                        text = "Sil",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.canDelete && !isDeleteInProgress) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
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

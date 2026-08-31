package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.ColorParser

/**
 * Gelir ve Gider türü arasında geçiş yapılmasını sağlayan seçici bileşendir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTypeSelector(
    selectedType: TransactionType,
    onTypeSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        FilterChip(
            selected = selectedType == TransactionType.EXPENSE,
            onClick = { onTypeSelected(TransactionType.EXPENSE) },
            label = {
                Text(
                    text = "Gider",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedType == TransactionType.EXPENSE) FontWeight.Bold else FontWeight.Normal,
                )
            },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    contentDescription = "Gider kategorilerini filtrele"
                },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )

        FilterChip(
            selected = selectedType == TransactionType.INCOME,
            onClick = { onTypeSelected(TransactionType.INCOME) },
            label = {
                Text(
                    text = "Gelir",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedType == TransactionType.INCOME) FontWeight.Bold else FontWeight.Normal,
                )
            },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    contentDescription = "Gelir kategorilerini filtrele"
                },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
    }
}

/**
 * Kategori liste bölümleri için başlık bileşenidir.
 */
@Composable
fun CategorySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FeniqoSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Text(
                text = "($count)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Kategori rengini ve adının ilk harfini gösteren daire rozetidir.
 */
@Composable
fun CategoryColorBadge(
    colorHex: String,
    categoryName: String,
    modifier: Modifier = Modifier,
) {
    val parsedColor = ColorParser.parseHexColorOrNull(colorHex) ?: MaterialTheme.colorScheme.primary
    val firstChar = categoryName.trim().firstOrNull()?.uppercase() ?: "?"
    val textColor = if (parsedColor.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = modifier
            .size(40.dp)
            .background(color = parsedColor, shape = CircleShape)
            .semantics {
                contentDescription = "$categoryName kategori rengi"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = firstChar,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

/**
 * Sistem kategorileri için salt-okunur rozet bileşenidir.
 */
@Composable
fun SystemCategoryBadge(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics {
            contentDescription = "Sistem kategorisi"
        },
        shape = RoundedCornerShape(FeniqoRadius.Small),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "Sistem",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Small, vertical = 2.dp),
        )
    }
}

/**
 * Kategori liste satırı bileşenidir.
 */
@Composable
fun CategoryListItem(
    category: CategoryDisplayModel,
    onEditClick: (EntityId) -> Unit,
    onDeleteClick: (CategoryDisplayModel) -> Unit,
    modifier: Modifier = Modifier,
    isActionsEnabled: Boolean = true,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryColorBadge(
                colorHex = category.colorHex,
                categoryName = category.name,
            )

            Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

            if (category.isDefault) {
                SystemCategoryBadge()
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onEditClick(category.id) },
                        enabled = isActionsEnabled && category.canEdit,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = "${category.name} kategorisini düzenle"
                            },
                    ) {
                        Text(
                            text = "Düzenle",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    TextButton(
                        onClick = { onDeleteClick(category) },
                        enabled = isActionsEnabled && category.canDelete,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = "${category.name} kategorisini sil"
                            },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(
                            text = "Sil",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Genel bilgi/hata mesajlarını gösteren satır içi banner bileşenidir.
 */
@Composable
fun CategoryMessageBanner(
    message: FinanceUiMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isDismissEnabled: Boolean = true,
) {
    val isError = message.isError
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Small),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message.toDisplayText(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )

            TextButton(
                onClick = onDismiss,
                enabled = isDismissEnabled,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = "Mesajı kapat"
                    },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = contentColor,
                ),
            ) {
                Text(
                    text = "Kapat",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Kategori silme onay diyaloğudur.
 */
@Composable
fun CategoryDeleteDialog(
    targetCategory: CategoryDisplayModel?,
    isDeleteInProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (targetCategory == null) return

    AlertDialog(
        onDismissRequest = {
            if (!isDeleteInProgress) {
                onDismiss()
            }
        },
        modifier = modifier,
        title = {
            Text(
                text = "Kategoriyi Sil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "“${targetCategory.name}” kategorisini silmek istediğinize emin misiniz? Geçmiş işlemlerde kategori adı korunur.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleteInProgress && !targetCategory.isDefault,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (isDeleteInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                    Text(text = "Siliniyor...")
                } else {
                    Text(text = "Sil")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isDeleteInProgress,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(text = "İptal")
            }
        },
    )
}

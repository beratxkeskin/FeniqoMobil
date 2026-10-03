package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.usecase.InstallmentDeleteScope
import com.feniqo.mobile.presentation.theme.FeniqoStatusColor
import com.feniqo.mobile.presentation.transaction.TransactionDeleteDialogState
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Onaylı minimal tasarım panolarına sadık tekil işlem silme onay panelidir (ModalBottomSheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleTransactionDeleteDialog(
    dialog: TransactionDeleteDialogState.Single,
    isDeleteInProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            if (isDeleteInProgress) {
                targetValue != SheetValue.Hidden
            } else {
                true
            }
        },
    )

    ModalBottomSheet(
        onDismissRequest = {
            if (!isDeleteInProgress) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {}
        },
        modifier = modifier.semantics {
            paneTitle = "İşlemi sil"
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DeleteSheetHeader(title = "İşlemi sil?")

            TransactionDeleteSummaryRow(target = dialog.target)

            Text(
                text = "Bu işlem geri alınamaz.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DeleteSheetActions(
                confirmText = "İşlemi sil",
                isDeleteInProgress = isDeleteInProgress,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                confirmWeight = 1f,
                dismissWeight = 1f,
            )
        }
    }
}

/**
 * Onaylı minimal tasarım panolarına sadık taksitli işlem silme onay panelidir (ModalBottomSheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentTransactionDeleteDialog(
    dialog: TransactionDeleteDialogState.Installment,
    isDeleteInProgress: Boolean,
    onConfirm: (InstallmentDeleteScope) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedScope by remember(dialog.target.id) {
        mutableStateOf(InstallmentDeleteScope.ONLY_THIS)
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            if (isDeleteInProgress) {
                targetValue != SheetValue.Hidden
            } else {
                true
            }
        },
    )

    ModalBottomSheet(
        onDismissRequest = {
            if (!isDeleteInProgress) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {}
        },
        modifier = modifier.semantics {
            paneTitle = "Taksitli işlemi sil"
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DeleteSheetHeader(title = "Taksitli işlemi sil")

            TransactionDeleteSummaryRow(target = dialog.target)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InstallmentScopeRow(
                    title = "Yalnızca bu taksiti sil",
                    subtitle = "Sadece seçili taksit kaydı kaldırılır.",
                    selected = selectedScope == InstallmentDeleteScope.ONLY_THIS,
                    enabled = !isDeleteInProgress,
                    onSelect = { selectedScope = InstallmentDeleteScope.ONLY_THIS },
                )

                if (selectedScope != InstallmentDeleteScope.ONLY_THIS && selectedScope != InstallmentDeleteScope.THIS_AND_FOLLOWING) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        thickness = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }

                InstallmentScopeRow(
                    title = "Bu ve sonraki taksitleri sil",
                    subtitle = "Seçili taksit ve bu gruptaki sonraki taksitler kaldırılır.",
                    selected = selectedScope == InstallmentDeleteScope.THIS_AND_FOLLOWING,
                    enabled = !isDeleteInProgress,
                    onSelect = { selectedScope = InstallmentDeleteScope.THIS_AND_FOLLOWING },
                )

                if (selectedScope != InstallmentDeleteScope.THIS_AND_FOLLOWING && selectedScope != InstallmentDeleteScope.ALL_GROUP) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        thickness = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }

                InstallmentScopeRow(
                    title = "Tüm taksit grubunu sil",
                    subtitle = "Geçmiş ve gelecek tüm taksitler kaldırılır.",
                    selected = selectedScope == InstallmentDeleteScope.ALL_GROUP,
                    enabled = !isDeleteInProgress,
                    onSelect = { selectedScope = InstallmentDeleteScope.ALL_GROUP },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Bu işlem geri alınamaz.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            DeleteSheetActions(
                confirmText = "Seçilen kapsamı sil",
                isDeleteInProgress = isDeleteInProgress,
                onConfirm = { onConfirm(selectedScope) },
                onDismiss = onDismiss,
                confirmWeight = 1.35f,
                dismissWeight = 1f,
            )
        }
    }
}

/**
 * Silme paneli başlığı: Sol tarafta kırmızı silme ikonu ve başlık metni.
 */
@Composable
private fun DeleteSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * İşlem özet satırı: Kategori ikonu, kategori adı, tarih, tutar ve taksit rozeti.
 */
@Composable
private fun TransactionDeleteSummaryRow(
    target: TransactionDisplayModel,
    modifier: Modifier = Modifier,
) {
    val categoryColor = ColorParser.parseHexColorOrNull(target.categoryColorHex)
        ?: MaterialTheme.colorScheme.primary
    val formattedDate = DateFormatter.formatReadableDate(target.transactionDate)
    val amountColor = if (target.type == TransactionType.INCOME) {
        FeniqoStatusColor.Success
    } else {
        MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = categoryColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CategorySemanticIconResolver.resolve(target.categoryIconKey),
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = target.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = target.formattedAmount,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = amountColor,
                    maxLines = 1,
                )

                if (target.installment != null) {
                    val badgeText = when {
                        target.installment.badgeText.contains("/") && !target.installment.badgeText.contains(" / ") ->
                            target.installment.badgeText.replace("/", " / ")
                        target.installment.badgeText.isNotBlank() ->
                            target.installment.badgeText
                        else ->
                            "${target.installment.number} / ${target.installment.total}"
                    }
                    InstallmentBadge(badgeText = badgeText)
                }
            }
        }
    }
}

/**
 * Taksit silme kapsamı seçim satırı: RadioButton, başlık ve açıklama.
 */
@Composable
private fun InstallmentScopeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                ),
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Alt panel eylemleri: Sol tarafta çerçeveli 'Vazgeç', sağ tarafta kırmızı dolu onay butonu.
 */
@Composable
private fun DeleteSheetActions(
    confirmText: String,
    isDeleteInProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmWeight: Float = 1f,
    dismissWeight: Float = 1f,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onDismiss,
            enabled = !isDeleteInProgress,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = Modifier
                .weight(dismissWeight)
                .defaultMinSize(minHeight = 48.dp),
        ) {
            Text(
                text = "Vazgeç",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }

        Button(
            onClick = {
                if (!isDeleteInProgress) {
                    onConfirm()
                }
            },
            enabled = !isDeleteInProgress,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.8f),
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = Modifier
                .weight(confirmWeight)
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    contentDescription = confirmText
                    if (isDeleteInProgress) {
                        stateDescription = "İşlem siliniyor"
                    }
                },
        ) {
            if (isDeleteInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onError,
                )
            } else {
                Text(
                    text = confirmText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

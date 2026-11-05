package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.transaction.MerchantCorrectionUiError
import com.feniqo.mobile.presentation.transaction.MerchantSelectorItem
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun MerchantCorrectionUiError.toLocalizedText(): String = when (this) {
    MerchantCorrectionUiError.AUTH_REQUIRED -> stringResource(Res.string.transaction_merchant_error_auth_required)
    MerchantCorrectionUiError.TRANSACTION_NOT_FOUND -> stringResource(Res.string.transaction_merchant_error_transaction_not_found)
    MerchantCorrectionUiError.OWNER_MISMATCH -> stringResource(Res.string.transaction_merchant_error_owner_mismatch)
    MerchantCorrectionUiError.WORKSPACE_RESTRICTED -> stringResource(Res.string.transaction_merchant_error_workspace_restricted)
    MerchantCorrectionUiError.NOT_ELIGIBLE -> stringResource(Res.string.transaction_merchant_error_not_eligible)
    MerchantCorrectionUiError.UNKNOWN_MERCHANT -> stringResource(Res.string.transaction_merchant_error_unknown_merchant)
    MerchantCorrectionUiError.GENERIC -> stringResource(Res.string.transaction_merchant_error_generic)
}

/**
 * Kullanıcı "Doğru değil" butonuna bastığında onay isteyen diyalog.
 */
@Composable
fun MerchantBlockConfirmDialog(
    isInProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    error: MerchantCorrectionUiError? = null,
) {
    AlertDialog(
        onDismissRequest = { if (!isInProgress) onDismiss() },
        title = {
            Text(
                text = stringResource(Res.string.transaction_merchant_block_confirm_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.transaction_merchant_block_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (error != null) {
                    Text(
                        text = error.toLocalizedText(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("merchant_block_error_text"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isInProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_block_button"),
            ) {
                if (isInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).testTag("block_in_progress_indicator"),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(Res.string.transaction_merchant_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isInProgress,
                modifier = Modifier.testTag("dismiss_block_button"),
            ) {
                Text(stringResource(Res.string.transaction_merchant_cancel))
            }
        },
        modifier = modifier.testTag("merchant_block_confirm_dialog"),
    )
}

/**
 * Aranabilir ve seçilebilir merchant bottom sheet bileşeni.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantSelectorBottomSheet(
    isSelectorOpen: Boolean,
    searchQuery: String,
    searchResults: List<MerchantSelectorItem>,
    selectedMerchantId: EntityId?,
    isInProgress: Boolean,
    error: MerchantCorrectionUiError?,
    onSearchQueryChanged: (String) -> Unit,
    onSelectMerchant: (EntityId) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isSelectorOpen) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isInProgress) onDismiss() },
        sheetState = sheetState,
        modifier = modifier.testTag("merchant_selector_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.transaction_merchant_selector_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onDismiss,
                    enabled = !isInProgress,
                    modifier = Modifier.testTag("close_selector_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.transaction_merchant_close_selector),
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text(stringResource(Res.string.transaction_merchant_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChanged("") },
                            modifier = Modifier.testTag("clear_search_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(Res.string.transaction_merchant_search_clear),
                            )
                        }
                    }
                },
                singleLine = true,
                enabled = !isInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("merchant_search_field"),
                shape = RoundedCornerShape(12.dp),
            )

            if (error != null) {
                Text(
                    text = error.toLocalizedText(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("merchant_error_text"),
                )
            }

            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("merchant_no_results_box"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.transaction_merchant_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .testTag("merchant_results_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(searchResults, key = { it.id.value }) { item ->
                        MerchantSelectorRow(
                            item = item,
                            isSelected = item.id == selectedMerchantId,
                            isEnabled = !isInProgress,
                            onClick = { onSelectMerchant(item.id) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isInProgress,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("cancel_assign_button"),
                ) {
                    Text(stringResource(Res.string.transaction_merchant_cancel))
                }

                Button(
                    onClick = onConfirm,
                    enabled = selectedMerchantId != null && !isInProgress,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("confirm_assign_button"),
                ) {
                    if (isInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp).testTag("assign_in_progress_indicator"),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(Res.string.transaction_merchant_confirm))
                }
            }
        }
    }
}

@Composable
fun MerchantSelectorRow(
    item: MerchantSelectorItem,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag("merchant_row_${item.id.value}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MerchantLogo(
                verifiedLogoKey = item.verifiedLogoKey,
                canShowMerchantLogo = true,
                categoryIconKey = item.categoryIconKey,
                categoryColor = MaterialTheme.colorScheme.primary,
                fallbackLabel = item.displayName,
                size = 36.dp,
                contentDescription = null, // Dekoratif! Çift anons engeli
            )
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

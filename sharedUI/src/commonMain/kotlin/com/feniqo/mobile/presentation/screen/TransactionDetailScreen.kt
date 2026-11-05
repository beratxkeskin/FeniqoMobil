@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.component.MerchantLogo
import com.feniqo.mobile.presentation.component.ResponsiveLabelValueRow
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel
import com.feniqo.mobile.presentation.transaction.toLocalizedText
import com.feniqo.mobile.presentation.transaction.toLocalizedTransactionDate
import com.feniqo.mobile.presentation.transaction.toLocalizedTransactionStatusText
import com.feniqo.mobile.presentation.util.ColorParser
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun TransactionDetailScreen(
    item: TransactionDisplayModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onResolveConflict: (() -> Unit)? = null,
    onOpenMerchantSelector: (() -> Unit)? = null,
    onOpenBlockConfirmDialog: (() -> Unit)? = null,
    isCorrectionInProgress: Boolean = false,
) {
    val categoryName = if (item.isCategoryUnavailable) {
        stringResource(Res.string.transaction_unknown_category)
    } else {
        item.categoryName
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.transaction_detail_back))
                }
                Text(
                    stringResource(Res.string.transaction_detail_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            val categoryColor = ColorParser.parseHexColorOrNull(item.categoryColorHex)
                ?: MaterialTheme.colorScheme.primary
            val fallbackLabel = item.merchant?.displayName
                ?: item.description?.takeIf { it.isNotBlank() }
                ?: categoryName
            MerchantLogo(
                verifiedLogoKey = item.merchant?.verifiedLogoKey,
                canShowMerchantLogo = item.merchant?.canShowMerchantLogo == true,
                categoryIconKey = item.categoryIconKey,
                categoryColor = categoryColor,
                fallbackLabel = fallbackLabel,
                size = 88.dp,
                contentDescription = null,
            )
            Text(item.description ?: categoryName, style = MaterialTheme.typography.titleLarge)
            Text(item.formattedAmount, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(categoryName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailField(stringResource(Res.string.transaction_detail_name), item.description ?: "—")
                    HorizontalDivider()
                    DetailField(
                        stringResource(Res.string.transaction_detail_payment_method),
                        item.paymentMethod.toLocalizedText(),
                    )
                    HorizontalDivider()
                    DetailField(
                        stringResource(Res.string.transaction_detail_date),
                        item.transactionDate.toLocalizedTransactionDate(),
                    )
                    HorizontalDivider()
                    DetailField(
                        stringResource(Res.string.transaction_detail_note),
                        item.note ?: stringResource(Res.string.transaction_detail_note_missing),
                    )
                    item.installment?.let {
                        DetailField(stringResource(Res.string.transaction_detail_installment), it.badgeText)
                    }
                }
            }

            if (item.canCorrectMerchant) {
                if (item.merchant != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.transaction_merchant_recognized_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                MerchantLogo(
                                    verifiedLogoKey = item.merchant.verifiedLogoKey,
                                    canShowMerchantLogo = item.merchant.canShowMerchantLogo,
                                    categoryIconKey = item.categoryIconKey,
                                    categoryColor = categoryColor,
                                    fallbackLabel = item.merchant.displayName,
                                    size = 40.dp,
                                    contentDescription = null,
                                )
                                Text(
                                    text = item.merchant.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onOpenMerchantSelector?.invoke() },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isCorrectionInProgress,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(stringResource(Res.string.transaction_merchant_change_action))
                                }
                                OutlinedButton(
                                    onClick = { onOpenBlockConfirmDialog?.invoke() },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isCorrectionInProgress,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                ) {
                                    Text(stringResource(Res.string.transaction_merchant_incorrect_action))
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { onOpenMerchantSelector?.invoke() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        enabled = !isCorrectionInProgress,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(Res.string.transaction_merchant_select_action))
                    }
                }
            }

            Text(item.syncStatus.toLocalizedTransactionStatusText(), style = MaterialTheme.typography.bodyMedium)
            if (onResolveConflict != null) {
                Button(onClick = onResolveConflict, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.transaction_detail_resolve_conflict))
                }
            }
            if (item.canEdit) {
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(Res.string.transaction_detail_edit))
                }
            }
            if (item.canDelete) {
                FilledTonalButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(Res.string.transaction_detail_delete))
                }
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    ResponsiveLabelValueRow(label = label, value = value)
}

package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionMerchantCreateDecision
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.transaction.MerchantSearchFilter
import com.feniqo.mobile.presentation.transaction.TransactionFormUiState
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_clear_to_auto
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_close_selector
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_no_results
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_not_listed
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_optional
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_search_all
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_search_clear
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_search_placeholder
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_section_title
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_selected_title
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_suggested_title
import feniqomobil.sharedui.generated.resources.transaction_form_merchant_use_transaction_name
import org.jetbrains.compose.resources.stringResource

/**
 * İşlem formu için kategoriye duyarlı, güvenli merchant seçici bölümüdür.
 * Yalnız [uiState.isMerchantSelectionEligible] true olduğunda render edilir.
 */
@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionMerchantSection(
    uiState: TransactionFormUiState,
    enabled: Boolean,
    onKnownMerchantSelected: (EntityId) -> Unit,
    onUseTransactionNameSelected: () -> Unit,
    onClearMerchantDecision: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isMerchantSelectionEligible) return

    var isSearchSheetOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Form disabled (ör. submit sırasında) olduğunda açık sheet güvenle kapanır ve query temizlenir
    LaunchedEffect(enabled) {
        if (!enabled) {
            isSearchSheetOpen = false
            searchQuery = ""
        }
    }

    val selectedCandidate = uiState.selectedMerchantCandidate

    Column(
        modifier = modifier.fillMaxWidth().testTag("transaction_merchant_section"),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        // 1. Bölüm Başlığı (Tekil temizleme aksiyonu önizleme kartının içindedir)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        ) {
            Text(
                text = stringResource(Res.string.transaction_form_merchant_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.transaction_form_merchant_optional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 2. Karar Durumuna Göre İçerik
        when {
            selectedCandidate != null -> {
                // KnownMerchant seçili önizlemesi
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("selected_merchant_card"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MerchantLogo(
                            verifiedLogoKey = selectedCandidate.verifiedLogoKey,
                            canShowMerchantLogo = true,
                            categoryIconKey = selectedCandidate.categoryIconKey,
                            categoryColor = MaterialTheme.colorScheme.primary,
                            fallbackLabel = selectedCandidate.displayName,
                            size = 36.dp,
                            contentDescription = null,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.transaction_form_merchant_selected_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = selectedCandidate.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            onClick = onClearMerchantDecision,
                            enabled = enabled,
                            modifier =
                                Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("merchant_selected_clear_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.transaction_form_merchant_clear_to_auto),
                            )
                        }
                    }
                }
            }

            uiState.merchantDecision is TransactionMerchantCreateDecision.UseTransactionName -> {
                // UseTransactionName seçili önizlemesi
                val effectiveTitle = uiState.title.ifEmpty { uiState.description }
                val selectedCategory = uiState.availableCategories.find { it.id == uiState.selectedCategoryId }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("use_transaction_name_card"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MerchantLogo(
                            verifiedLogoKey = null,
                            canShowMerchantLogo = false,
                            categoryIconKey = selectedCategory?.iconKey,
                            categoryColor = MaterialTheme.colorScheme.primary,
                            fallbackLabel = effectiveTitle,
                            size = 36.dp,
                            contentDescription = null,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.transaction_form_merchant_use_transaction_name),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = effectiveTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            onClick = onClearMerchantDecision,
                            enabled = enabled,
                            modifier =
                                Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("merchant_use_name_clear_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.transaction_form_merchant_clear_to_auto),
                            )
                        }
                    }
                }
            }

            else -> {
                // Auto durumu: Önerilen merchant adayları ve aksiyon butonları
                val inlineCandidates = uiState.merchantSuggestedCandidates
                if (inlineCandidates.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.transaction_form_merchant_suggested_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("merchant_suggestions_flow_row"),
                    ) {
                        inlineCandidates.forEach { candidate ->
                            SuggestionChip(
                                onClick = { onKnownMerchantSelected(candidate.id) },
                                label = {
                                    Text(
                                        text = candidate.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                icon = {
                                    MerchantLogo(
                                        verifiedLogoKey = candidate.verifiedLogoKey,
                                        canShowMerchantLogo = true,
                                        categoryIconKey = candidate.categoryIconKey,
                                        categoryColor = MaterialTheme.colorScheme.primary,
                                        fallbackLabel = candidate.displayName,
                                        size = 20.dp,
                                        contentDescription = null,
                                    )
                                },
                                enabled = enabled,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("merchant_chip_${candidate.id.value}"),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.merchantSearchableCandidates.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { isSearchSheetOpen = true },
                            enabled = enabled,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("merchant_search_all_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(Res.string.transaction_form_merchant_search_all),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onUseTransactionNameSelected,
                        enabled = enabled,
                        modifier =
                            (if (uiState.merchantSearchableCandidates.isNotEmpty()) Modifier.weight(1f) else Modifier.fillMaxWidth())
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("merchant_use_transaction_name_button"),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.transaction_form_merchant_not_listed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(Res.string.transaction_form_merchant_use_transaction_name),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    // 3. Yerel "Tümünü ara" Bottom Sheet
    if (isSearchSheetOpen && uiState.merchantSearchableCandidates.isNotEmpty()) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = {
                isSearchSheetOpen = false
                searchQuery = ""
            },
            sheetState = sheetState,
            modifier = Modifier.testTag("merchant_search_bottom_sheet"),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.transaction_form_merchant_search_all),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = {
                            isSearchSheetOpen = false
                            searchQuery = ""
                        },
                        enabled = enabled,
                        modifier = Modifier.testTag("close_merchant_search_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.transaction_form_merchant_close_selector),
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    enabled = enabled,
                    placeholder = {
                        Text(stringResource(Res.string.transaction_form_merchant_search_placeholder))
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                enabled = enabled,
                                modifier = Modifier.testTag("clear_merchant_search_query_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(Res.string.transaction_form_merchant_search_clear),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("merchant_search_text_field"),
                )

                val filteredCandidates =
                    remember(searchQuery, uiState.merchantSearchableCandidates) {
                        MerchantSearchFilter.filterCandidates(searchQuery, uiState.merchantSearchableCandidates)
                    }

                if (filteredCandidates.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.transaction_form_merchant_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("merchant_search_empty_text"),
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).testTag("merchant_search_results_list"),
                    ) {
                        items(filteredCandidates, key = { it.id.value }) { item ->
                            Surface(
                                onClick = {
                                    if (enabled) {
                                        onKnownMerchantSelected(item.id)
                                        isSearchSheetOpen = false
                                        searchQuery = ""
                                    }
                                },
                                enabled = enabled,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth().testTag("merchant_search_item_${item.id.value}"),
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
                                        contentDescription = null,
                                    )
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

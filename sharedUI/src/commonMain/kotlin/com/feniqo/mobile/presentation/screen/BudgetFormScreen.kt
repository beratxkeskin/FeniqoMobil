package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.usecase.BudgetHealth
import com.feniqo.mobile.presentation.budget.BudgetFormUiState
import com.feniqo.mobile.presentation.budget.localizedCurrencyDisplayName
import com.feniqo.mobile.presentation.budget.symbolText
import com.feniqo.mobile.presentation.budget.toLocalizedBudgetPeriod
import com.feniqo.mobile.presentation.budget.toLocalizedText
import com.feniqo.mobile.presentation.component.BudgetCategoryPickerSheet
import com.feniqo.mobile.presentation.component.BudgetPeriodPickerSheet
import com.feniqo.mobile.presentation.component.CategoryTonalIcon
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.ColorParser
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val FeniqoSageGreen = Color(0xFF2D5A43)
private val FeniqoBackgroundSand = Color(0xFFF7F5F0)

/**
 * 02 Bütçe Oluştur ve B04 Bütçeyi Düzenle ekranlarının durumsuz (stateless) Compose sunumudur.
 */
@Composable
fun BudgetFormScreen(
    state: BudgetFormUiState,
    onBack: () -> Unit,
    onCategorySelected: (EntityId) -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onLimitChanged: (String) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showPeriodPicker by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val limitFocusRequester = remember { FocusRequester() }
    val amountErrorText = state.mutationState.amountError?.toLocalizedText()
    val limitContentDescription = stringResource(Res.string.budget_form_limit_content_desc)
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(state.mutationState.amountError) {
        if (state.mutationState.amountError != null) limitFocusRequester.requestFocus()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Large)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

            // 1. Üst Bar: Geri Butonu ve Başlık
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = !state.isSubmitting) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.budget_back),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (state.isEditMode) {
                        stringResource(Res.string.budget_form_edit_title)
                    } else {
                        stringResource(Res.string.budget_form_create_title)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 2. Form İçeriği
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                if (state.isEditMode) {
                    // -------------------------------------------------------------
                    // B04: Bütçeyi Düzenle Modu
                    // -------------------------------------------------------------

                    // Kilitli Kategori Kartı
                    item {
                        val catColor = ColorParser.parseHexColorOrNull(state.selectedCategoryColorHex)
                            ?: MaterialTheme.colorScheme.primary
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(FeniqoRadius.Large),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(FeniqoSpacing.Medium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                            ) {
                                CategoryTonalIcon(
                                    iconKey = state.selectedCategoryIconKey,
                                    color = catColor,
                                    containerSize = 48.dp,
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                     Text(
                                         text = state.selectedCategoryName ?: stringResource(Res.string.budget_fallback_category_name),
                                         style = MaterialTheme.typography.titleMedium,
                                         fontWeight = FontWeight.Bold,
                                     )
                                     Text(
                                         text = stringResource(Res.string.budget_form_expense_category_type),
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                                     )
                                 }

                                 Icon(
                                     imageVector = Icons.Default.Lock,
                                     contentDescription = stringResource(Res.string.budget_form_category_locked_desc),
                                     tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                     modifier = Modifier.size(20.dp),
                                 )
                             }
                         }
                     }

                     // Kilitli Dönem Kartı
                     item {
                         val formattedMonth = state.selectedMonth?.toLocalizedBudgetPeriod() ?: ""
                         Card(
                             modifier = Modifier.fillMaxWidth(),
                             shape = RoundedCornerShape(FeniqoRadius.Large),
                             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                         ) {
                             Column(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .padding(FeniqoSpacing.Medium),
                                 verticalArrangement = Arrangement.spacedBy(4.dp),
                             ) {
                                 Row(
                                     verticalAlignment = Alignment.CenterVertically,
                                     horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                 ) {
                                     Icon(
                                         imageVector = Icons.Outlined.CalendarMonth,
                                         contentDescription = null,
                                         modifier = Modifier.size(18.dp),
                                         tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                     )
                                     Text(
                                         text = stringResource(Res.string.budget_form_period_label),
                                         style = MaterialTheme.typography.labelMedium,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                                     )
                                 }
                                 Surface(
                                     shape = RoundedCornerShape(FeniqoRadius.Medium),
                                     color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                     modifier = Modifier.fillMaxWidth(),
                                 ) {
                                     Text(
                                         text = formattedMonth,
                                         style = MaterialTheme.typography.bodyLarge,
                                         fontWeight = FontWeight.SemiBold,
                                         modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = 12.dp),
                                     )
                                 }
                             }
                         }
                     }

                     // Aylık Limit Girişi (B04)
                     item {
                         Card(
                             modifier = Modifier.fillMaxWidth(),
                             shape = RoundedCornerShape(FeniqoRadius.Large),
                             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                         ) {
                             Column(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .padding(FeniqoSpacing.Medium),
                                 verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                             ) {
                                 Text(
                                     text = stringResource(Res.string.budget_form_monthly_limit_label),
                                     style = MaterialTheme.typography.labelMedium,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                                 )

                                 OutlinedTextField(
                                     value = state.limitInput,
                                     onValueChange = onLimitChanged,
                                     enabled = state.isFormEnabled,
                                     placeholder = { Text("0,00") },
                                     prefix = {
                                         Text(
                                             text = state.currency.symbolText,
                                             style = MaterialTheme.typography.bodyLarge,
                                             fontWeight = FontWeight.Bold,
                                         )
                                     },
                                     trailingIcon = {
                                         Surface(
                                             shape = RoundedCornerShape(FeniqoRadius.Small),
                                             color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                             modifier = Modifier.padding(end = 8.dp),
                                         ) {
                                             Text(
                                                 text = "${state.currency.name} ∨",
                                                 style = MaterialTheme.typography.labelMedium,
                                                 fontWeight = FontWeight.Bold,
                                                 modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                             )
                                         }
                                     },
                                     singleLine = true,
                                     keyboardOptions = KeyboardOptions(
                                         keyboardType = KeyboardType.Decimal,
                                         imeAction = ImeAction.Done,
                                     ),
                                     keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                                     isError = state.mutationState.amountError != null,
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .focusRequester(limitFocusRequester)
                                         .semantics {
                                             contentDescription = limitContentDescription
                                             amountErrorText?.let { error(it) }
                                         },
                                     shape = RoundedCornerShape(FeniqoRadius.Medium),
                                 )

                                 state.mutationState.amountError?.let { error ->
                                     Text(
                                         text = error.toLocalizedText(),
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.error,
                                     )
                                 }
                             }
                         }
                     }

                     // Canlı Önizleme Kartı (B04)
                     state.editPreview?.let { preview ->
                         item {
                             Card(
                                 modifier = Modifier.fillMaxWidth(),
                                 shape = RoundedCornerShape(FeniqoRadius.Large),
                                 colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                             ) {
                                 Column(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .padding(FeniqoSpacing.Medium),
                                     verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                 ) {
                                     Row(
                                         verticalAlignment = Alignment.CenterVertically,
                                         horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                     ) {
                                         Icon(
                                             imageVector = Icons.Outlined.BarChart,
                                             contentDescription = null,
                                             tint = MaterialTheme.colorScheme.primary,
                                             modifier = Modifier.size(18.dp),
                                         )
                                         Text(
                                             text = stringResource(Res.string.budget_form_preview_title),
                                             style = MaterialTheme.typography.titleSmall,
                                             fontWeight = FontWeight.Bold,
                                         )
                                     }

                                     Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.SpaceBetween,
                                     ) {
                                         Column {
                                             Text(stringResource(Res.string.budget_overview_spent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                             Text(preview.formattedSpent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                         }
                                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                             Text(stringResource(Res.string.budget_form_preview_new_remaining), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                             Text(
                                                 preview.formattedNewRemaining,
                                                 style = MaterialTheme.typography.bodyMedium,
                                                 fontWeight = FontWeight.Bold,
                                                 color = if (preview.isRemainingNegative) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
                                             )
                                         }
                                         Column(horizontalAlignment = Alignment.End) {
                                             Text(stringResource(Res.string.budget_detail_usage_rate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                             Text(
                                                 preview.formattedUsageRate,
                                                 style = MaterialTheme.typography.bodyMedium,
                                                 fontWeight = FontWeight.Bold,
                                                 color = if (preview.isRemainingNegative) Color(0xFFEF4444) else FeniqoSageGreen,
                                             )
                                         }
                                     }

                                     LinearProgressIndicator(
                                         progress = { preview.usageProgressFraction },
                                         modifier = Modifier
                                             .fillMaxWidth()
                                             .height(6.dp)
                                             .clip(RoundedCornerShape(3.dp)),
                                         color = if (preview.isRemainingNegative) Color(0xFFEF4444) else FeniqoSageGreen,
                                         trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                     )
                                 }
                             }
                         }
                     }

                     // Bilgi Notu (B04): Harcama kayıtların değişmez
                     item {
                         Surface(
                             shape = RoundedCornerShape(FeniqoRadius.Medium),
                             color = MaterialTheme.colorScheme.primaryContainer,
                             modifier = Modifier.fillMaxWidth(),
                         ) {
                             Row(
                                 modifier = Modifier.padding(FeniqoSpacing.Medium),
                                 verticalAlignment = Alignment.Top,
                                 horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                             ) {
                                 Icon(
                                     imageVector = Icons.Default.Info,
                                     contentDescription = null,
                                     tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                     modifier = Modifier.size(20.dp),
                                 )
                                 Column {
                                     Text(
                                         text = stringResource(Res.string.budget_form_edit_info_title),
                                         style = MaterialTheme.typography.bodySmall,
                                         fontWeight = FontWeight.Bold,
                                         color = MaterialTheme.colorScheme.onPrimaryContainer,
                                     )
                                     Text(
                                         text = stringResource(Res.string.budget_form_edit_info_desc),
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                                     )
                                 }
                             }
                         }
                     }
                } else {
                    // -------------------------------------------------------------
                    // 02: Bütçe Oluştur Modu
                    // -------------------------------------------------------------

                    // Kategori Seçim Kartı (02)
                    item {
                        val catColor = ColorParser.parseHexColorOrNull(state.selectedCategoryColorHex)
                            ?: MaterialTheme.colorScheme.primary

                        Card(
                            onClick = { if (state.isCategoryEditable) showCategoryPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(FeniqoRadius.Large),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(FeniqoSpacing.Medium),
                                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                            ) {
                                Text(
                                    text = stringResource(Res.string.budget_form_category_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                                    ) {
                                        if (state.selectedCategoryId != null) {
                                            CategoryTonalIcon(
                                                iconKey = state.selectedCategoryIconKey,
                                                color = catColor,
                                                containerSize = 40.dp,
                                            )
                                            Text(
                                                text = state.selectedCategoryName ?: stringResource(Res.string.budget_fallback_category_name),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text("?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(
                                                text = stringResource(Res.string.budget_form_category_placeholder),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = stringResource(Res.string.budget_form_category_placeholder),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        state.mutationState.categoryError?.let { error ->
                            Text(
                                text = error.toLocalizedText(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                            )
                        }
                    }

                    // Dönem Seçim Kartı (02)
                    item {
                        val formattedMonth = state.selectedMonth?.toLocalizedBudgetPeriod() ?: stringResource(Res.string.budget_form_select_period)
                        Card(
                            onClick = { if (state.isMonthEditable) showPeriodPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(FeniqoRadius.Large),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(FeniqoSpacing.Medium),
                                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                            ) {
                                Text(
                                    text = stringResource(Res.string.budget_form_period_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CalendarMonth,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = formattedMonth,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = stringResource(Res.string.budget_form_select_period),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Para Birimi Kartı (02)
                    item {
                        Card(
                            onClick = { showPeriodPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(FeniqoRadius.Large),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(FeniqoSpacing.Medium),
                                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                            ) {
                                Text(
                                    text = stringResource(Res.string.budget_form_currency_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                                    ) {
                                        Text(
                                            text = state.currency.symbolText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = FeniqoSageGreen,
                                        )
                                        Text(
                                            text = localizedCurrencyDisplayName(state.currency),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = stringResource(Res.string.budget_form_select_currency),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Aylık Limit Girişi (02)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(FeniqoRadius.Large),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(FeniqoSpacing.Medium),
                                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                            ) {
                                Text(
                                    text = stringResource(Res.string.budget_form_monthly_limit_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                OutlinedTextField(
                                    value = state.limitInput,
                                    onValueChange = onLimitChanged,
                                    enabled = state.isFormEnabled,
                                    placeholder = { Text("0,00") },
                                    prefix = {
                                        Text(
                                            text = state.currency.symbolText,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                                    isError = state.mutationState.amountError != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(limitFocusRequester)
                                        .semantics {
                                            contentDescription = limitContentDescription
                                            amountErrorText?.let { error(it) }
                                        },
                                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    ),
                                )

                                state.mutationState.amountError?.let { error ->
                                    Text(
                                        text = error.toLocalizedText(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }

                    // Bilgi Notu (02): Bu kategorinin giderleri bütçeye dahil edilir
                    item {
                        Surface(
                            shape = RoundedCornerShape(FeniqoRadius.Medium),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = stringResource(Res.string.budget_form_create_info_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Seçilen Ayın Harcaması Bilgi Kartı (02)
                    state.formattedCurrentSpent?.let { spentText ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(FeniqoRadius.Large),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(FeniqoSpacing.Medium),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.budget_form_current_spent_title),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }

                                        Text(
                                            text = spentText,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }

                                    Text(
                                        text = stringResource(Res.string.budget_form_current_spent_disclaimer),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

            // 3. Alt Butonlar (Kaydet / Vazgeç)
            if (state.isEditMode) {
                Button(
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.budget_form_save_changes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                OutlinedButton(
                    onClick = onBack,
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                ) {
                    Text(stringResource(Res.string.budget_cancel), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.budget_form_create_button), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
        }

        // 4. Kategori Seçim Modalı (B07)
        if (showCategoryPicker) {
            BudgetCategoryPickerSheet(
                categories = state.availableCategories,
                selectedCategoryId = state.selectedCategoryId,
                onCategorySelected = { categoryId ->
                    onCategorySelected(categoryId)
                    showCategoryPicker = false
                },
                onDismiss = { showCategoryPicker = false },
            )
        }

        // 5. Dönem ve Para Birimi Seçim Modalı (B08)
        if (showPeriodPicker && state.selectedMonth != null) {
            BudgetPeriodPickerSheet(
                initialMonth = state.selectedMonth,
                initialCurrency = state.currency,
                onApply = { newMonth, newCurrency ->
                    onMonthSelected(newMonth)
                    onCurrencySelected(newCurrency)
                    showPeriodPicker = false
                },
                onDismiss = { showPeriodPicker = false },
            )
        }
    }
}

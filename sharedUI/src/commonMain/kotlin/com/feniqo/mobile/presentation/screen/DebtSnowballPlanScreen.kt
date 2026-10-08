@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
)

package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.presentation.common.symbol
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.common.toLocalizedText
import com.feniqo.mobile.presentation.component.EmptyState
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.debt.DebtSnowballPlanUiState
import com.feniqo.mobile.presentation.debt.toLocalizedText
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Borç Snowball Ödeme Planı Simülasyon Ekranı (Panel 07 & 08).
 */
@Composable
fun DebtSnowballPlanScreen(
    state: DebtSnowballPlanUiState,
    onBack: () -> Unit,
    onCurrencySelect: (Currency) -> Unit,
    onBudgetChange: (String) -> Unit,
    onCalculate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FeniqoSpacing.Large),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Üst Başlık & Geri Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.debt_snowball_nav_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = stringResource(Res.string.debt_snowball_title),
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.3).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.debt_snowball_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Simülasyon Girdi Kartı
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.debt_snowball_currency_label),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )

                        // Para Birimi Seçim Sekmeleri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.availableCurrencies.forEach { currency ->
                                val isSelected = currency == state.selectedCurrency
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) FeniqoSageGreen else Color.Transparent)
                                        .clickable(
                                            role = Role.Tab,
                                            onClick = { onCurrencySelect(currency) },
                                        )
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = currency.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Aylık Bütçe Girdisi
                        val budgetErrorText = state.budgetError?.toLocalizedText()
                        OutlinedTextField(
                            value = state.budgetInput,
                            onValueChange = onBudgetChange,
                            label = { Text(stringResource(Res.string.debt_snowball_budget_label)) },
                            placeholder = { Text(stringResource(Res.string.debt_snowball_budget_placeholder)) },
                            trailingIcon = {
                                Text(
                                    text = state.selectedCurrency.symbol(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            },
                            isError = state.budgetError != null,
                            supportingText = {
                                budgetErrorText?.let {
                                    Text(text = it, color = Color(0xFFDC2626))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = FeniqoSageGreen,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )

                        // Uygun Borç Bilgisi
                        Text(
                            text = pluralStringResource(
                                Res.plurals.debt_snowball_eligible_debts_plural,
                                state.eligibleDebtsCount,
                                state.eligibleDebtsCount,
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Planı Hesapla Butonu
                        Button(
                            onClick = onCalculate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.debt_snowball_calculate_button),
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                ),
                            )
                        }
                    }
                }

                if (state.isLoadingDebts) {
                    LoadingContent(modifier = Modifier.fillMaxWidth().height(150.dp))
                } else if (state.observationError != null) {
                    ErrorState(
                        title = stringResource(Res.string.debt_snowball_error_title),
                        description = state.observationError.toLocalizedText(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth().padding(vertical = FeniqoSpacing.Large),
                    )
                } else if (state.eligibleDebtsCount == 0) {
                    EmptyState(
                        title = stringResource(Res.string.debt_snowball_empty_title),
                        description = stringResource(
                            Res.string.debt_snowball_empty_desc,
                            state.selectedCurrency.name,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = FeniqoSpacing.Large),
                    )
                } else if (state.plan != null) {
                    val plan = state.plan

                    // 2. Grafit Simülasyon Sonuç Kartı
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF303536)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.debt_snowball_result_title),
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(Res.string.debt_snowball_total_debt),
                                        fontSize = 11.sp,
                                        color = Color(0xFF9CA3AF),
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = plan.totalDebtAmount.toLocalizedFormatted(),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }

                                Column {
                                    Text(
                                        text = stringResource(Res.string.debt_snowball_monthly_budget),
                                        fontSize = 11.sp,
                                        color = Color(0xFF9CA3AF),
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = plan.monthlyPaymentBudget.toLocalizedFormatted(),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(Res.string.debt_snowball_approx_prefix),
                                        fontSize = 11.sp,
                                        color = Color(0xFF9CA3AF),
                                    )
                                    Text(
                                        text = pluralStringResource(
                                            Res.plurals.debt_snowball_month_count_plural,
                                            plan.totalMonths,
                                            plan.totalMonths,
                                        ),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF22C55E),
                                    )
                                    Text(
                                        text = stringResource(Res.string.debt_snowball_approx_suffix),
                                        fontSize = 10.sp,
                                        color = Color(0xFF9CA3AF),
                                    )
                                }
                            }

                            // Simülasyon Bilgi Kutusu
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF262A2B))
                                    .padding(10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF9CA3AF),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(Res.string.debt_snowball_disclaimer),
                                        fontSize = 11.sp,
                                        color = Color(0xFFD1D5DB),
                                        lineHeight = 15.sp,
                                    )
                                }
                            }
                        }
                    }

                    // 3. Borç Kapanış Sırası Bölümü
                    Text(
                        text = stringResource(Res.string.debt_snowball_order_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        plan.debtItems.forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(FeniqoSageGreen),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = item.orderIndex.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.debtTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = item.initialRemainingAmount.toLocalizedFormatted(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                        )
                                    }

                                    Text(
                                        text = stringResource(Res.string.debt_snowball_settled_in_month, item.settledInMonth),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FeniqoSageGreen,
                                    )
                                }
                            }
                        }
                    }

                    // 4. Aylık Dağılım Bölümü
                    Text(
                        text = stringResource(Res.string.debt_snowball_monthly_dist_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        plan.monthlyAllocations.forEach { alloc ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = alloc.month.toString(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            val allocationDebtTitle = if (alloc.isMissingDebt || alloc.debtTitle == null) {
                                                stringResource(Res.string.debt_snowball_missing_debt_fallback, alloc.debtId.value)
                                            } else {
                                                alloc.debtTitle
                                            }
                                            Text(
                                                text = stringResource(
                                                    Res.string.debt_snowball_allocation_header,
                                                    alloc.month,
                                                    allocationDebtTitle,
                                                ),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.sp,
                                            )
                                            Text(
                                                text = stringResource(
                                                    Res.string.debt_snowball_allocation_remaining,
                                                    alloc.remainingBalanceAfterPayment.toLocalizedFormatted(),
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = stringResource(Res.string.debt_snowball_allocation_payment),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = alloc.allocatedAmount.toLocalizedFormatted(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF16A34A),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

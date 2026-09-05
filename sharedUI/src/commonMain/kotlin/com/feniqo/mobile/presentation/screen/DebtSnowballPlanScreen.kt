package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.presentation.component.EmptyState
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.debt.DebtSnowballPlanUiState
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

/**
 * Borç snowball ödeme planı simülasyonunun durumsuz (stateless) Compose ekranıdır.
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

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Large),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Üst Başlık & Geri Dön
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "Geri dön" },
                ) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                Text(
                    text = "Borç Snowball Planı",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Giriş Kartı (Para birimi + Aylık bütçe + Hesapla CTA)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(FeniqoSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                    ) {
                        Text(
                            text = "Simülasyon Para Birimi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                        ) {
                            state.availableCurrencies.forEach { currency ->
                                val isSelected = currency == state.selectedCurrency
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onCurrencySelect(currency) },
                                    label = { Text(currency.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.budgetInput,
                            onValueChange = onBudgetChange,
                            label = { Text("Aylık Ödeme Bütçesi (${state.selectedCurrency.name})") },
                            placeholder = { Text("Örn: 2500") },
                            isError = state.budgetError != null,
                            supportingText = {
                                state.budgetError?.let {
                                    Text(text = it, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Button(
                            onClick = onCalculate,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(FeniqoRadius.Medium),
                        ) {
                            Text(
                                text = "Planı Hesapla",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))

                if (state.isLoadingDebts) {
                    LoadingContent(modifier = Modifier.fillMaxWidth().height(150.dp))
                } else if (state.observationError != null) {
                    ErrorState(
                        title = "Bir Hata Oluştu",
                        description = state.observationError.toDisplayText(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth().padding(vertical = FeniqoSpacing.Large),
                    )
                } else if (state.eligibleDebtsCount == 0) {
                    EmptyState(
                        title = "Uygun Borç Bulunamadı",
                        description = "Seçilen para biriminde (${state.selectedCurrency.name}) açık ve ödenecek borç kaydı bulunmuyor.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = FeniqoSpacing.Large),
                    )
                } else if (state.plan != null) {


                    val plan = state.plan

                    // Sonuç Özeti Kartı
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(FeniqoRadius.Medium),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(FeniqoSpacing.Medium),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                        ) {
                            Text(
                                text = "Simülasyon Özeti",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Toplam Borç:", style = MaterialTheme.typography.bodyMedium)
                                Text(plan.totalDebtFormatted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Aylık Bütçe:", style = MaterialTheme.typography.bodyMedium)
                                Text(plan.monthlyBudgetFormatted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Tahmini Borçsuzluk:", style = MaterialTheme.typography.bodyMedium)
                                Text("${plan.totalMonths} Ay", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Large))

                    // Kapanış Sırası
                    Text(
                        text = "Borç Kapanış Sırası (Snowball)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        plan.debtItems.forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(FeniqoRadius.Medium),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(FeniqoSpacing.Medium),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = item.orderIndex.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(FeniqoSpacing.Medium))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.debtTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        Text(
                                            text = "Kalan: ${item.initialRemainingFormatted}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = "${item.settledInMonth}. Ayda Kapanır",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Large))

                    // Aylık Ödeme Dağılımı Detayı
                    Text(
                        text = "Aylık Ödeme Dağılımı",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                    ) {
                        plan.monthlyAllocations.forEach { alloc ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(FeniqoRadius.Small),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(
                                            text = "${alloc.month}. Ay • ${alloc.debtTitle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "Kalan: ${alloc.remainingBalanceFormatted}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = alloc.allocatedFormatted,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraLarge))
            }
        }
    }
}

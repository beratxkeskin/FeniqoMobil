package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.budget.BudgetFormUiState
import com.feniqo.mobile.presentation.budget.CategoryIconResolver
import com.feniqo.mobile.presentation.budget.nextMonth
import com.feniqo.mobile.presentation.budget.previousMonth
import com.feniqo.mobile.presentation.budget.symbolText
import com.feniqo.mobile.presentation.category.CategoryDisplayModel
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Bütçe oluşturma ve düzenleme ekranının durumsuz (stateless) Compose sunumudur.
 * Yalnızca UI state ve etkileşim callback'lerini tüketir; ViewModel, DAO veya Supabase bağımlılığı içermez.
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
    val title = if (state.isEditMode) "Bütçeyi Düzenle" else "Bütçe Ekle"
    val description = if (state.isEditMode) {
        "Bütçe limitinizi güncelleyin."
    } else {
        "Seçtiğiniz harcama kategorisi için aylık limit belirleyin."
    }

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

            // 1. Üst Başlık ve Geri Dönüş Alanı
            BudgetFormHeader(
                title = title,
                description = description,
                onBack = onBack,
                isBackEnabled = !state.isSubmitting,
            )

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 2. Form İçeriği
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
            ) {
                // Kategori Seçim Bölümü
                item {
                    BudgetCategorySection(
                        state = state,
                        onCategorySelected = onCategorySelected,
                    )
                }

                // Dönem (Ay) Seçim Bölümü
                item {
                    BudgetMonthSection(
                        state = state,
                        onMonthSelected = onMonthSelected,
                    )
                }

                // Limit Tutarı ve Para Birimi Giriş Bölümü
                item {
                    BudgetLimitSection(
                        state = state,
                        onLimitChanged = onLimitChanged,
                        onCurrencySelected = onCurrencySelected,
                    )
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 3. Gönder Butonu (Çift tıklama/gönderim engelli)
            BudgetFormSubmitButton(
                state = state,
                onSubmit = onSubmit,
            )

            Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
        }
    }
}

@Composable
private fun BudgetFormHeader(
    title: String,
    description: String,
    onBack: () -> Unit,
    isBackEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            enabled = isBackEnabled,
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics { contentDescription = "Geri" },
        ) {
            Text(
                text = "← Geri",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(FeniqoSpacing.Small))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetCategorySection(
    state: BudgetFormUiState,
    onCategorySelected: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Text(
            text = "Harcama Kategorisi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (state.isEditMode) {
            // Düzenleme modunda kategori sabit ve salt-okunurdur
            val catColor = ColorParser.parseHexColorOrNull(state.selectedCategoryColorHex)
                ?: MaterialTheme.colorScheme.primary
            val catIconEmoji = CategoryIconResolver.resolveIconEmojiOrNull(state.selectedCategoryIconKey)
            val displayName = state.selectedCategoryName ?: "Kategori"

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FeniqoSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(catColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = catIconEmoji ?: (displayName.firstOrNull()?.uppercase() ?: "?"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = catColor,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Mevcut kategori (Değiştirilemez)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            // Oluşturma modunda harcama kategorisi seçimi
            if (state.availableCategories.isEmpty()) {
                Text(
                    text = "Henüz tanımlı harcama kategorisi bulunmuyor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = FeniqoSpacing.Small),
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    state.availableCategories.forEach { category ->
                        val isSelected = category.id == state.selectedCategoryId
                        val catColor = ColorParser.parseHexColorOrNull(category.colorHex)
                            ?: MaterialTheme.colorScheme.primary
                        val iconEmoji = CategoryIconResolver.resolveIconEmojiOrNull(category.iconKey)

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (state.isCategoryEditable) {
                                    onCategorySelected(category.id)
                                }
                            },
                            enabled = state.isCategoryEditable,
                            label = {
                                Text(
                                    text = category.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(catColor),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (iconEmoji != null) {
                                        Text(
                                            text = iconEmoji,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            // Kategori Alan Hatası
            state.mutationState.categoryError?.let { error ->
                Text(
                    text = error.toDisplayText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun BudgetMonthSection(
    state: BudgetFormUiState,
    onMonthSelected: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Text(
            text = "Bütçe Dönemi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        val formattedMonth = state.selectedMonth?.let { DateFormatter.formatYearMonth(it) } ?: "Ay Seçilmedi"

        if (state.isEditMode) {
            // Düzenleme modunda ay bilgisi sabittir
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FeniqoSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formattedMonth,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "(Değiştirilemez)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // Oluşturma modunda ay seçimi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { state.selectedMonth?.let { onMonthSelected(it.previousMonth()) } },
                    enabled = state.isMonthEditable && state.selectedMonth != null,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text("◀ Önceki Ay")
                }

                Surface(
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = formattedMonth,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(
                            horizontal = FeniqoSpacing.Medium,
                            vertical = FeniqoSpacing.Small,
                        ),
                    )
                }

                TextButton(
                    onClick = { state.selectedMonth?.let { onMonthSelected(it.nextMonth()) } },
                    enabled = state.isMonthEditable && state.selectedMonth != null,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text("Sonraki Ay ▶")
                }
            }

            // Ay Alan Hatası
            state.mutationState.monthError?.let { error ->
                Text(
                    text = error.toDisplayText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetLimitSection(
    state: BudgetFormUiState,
    onLimitChanged: (String) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Text(
            text = "Aylık Limit Tutarı",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = state.limitInput,
            onValueChange = onLimitChanged,
            enabled = state.isFormEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Bütçe limiti" },
            placeholder = { Text("Örn: 5000 veya 5000,00") },
            trailingIcon = {
                Text(
                    text = state.currency.symbolText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = FeniqoSpacing.Medium),
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() },
            ),
            isError = state.mutationState.amountError != null,
            singleLine = true,
            shape = RoundedCornerShape(FeniqoRadius.Medium),
        )

        // Limit Alan Hatası
        state.mutationState.amountError?.let { error ->
            Text(
                text = error.toDisplayText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))

        // Para Birimi Seçimi
        Text(
            text = "Para Birimi",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        ) {
            val supportedCurrencies = listOf(Currency.TRY, Currency.USD, Currency.EUR)
            supportedCurrencies.forEach { curr ->
                val isSelected = curr == state.currency
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (state.isFormEnabled) {
                            onCurrencySelected(curr)
                        }
                    },
                    enabled = state.isFormEnabled,
                    label = {
                        Text(text = "${curr.symbolText} ${curr.code}")
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BudgetFormSubmitButton(
    state: BudgetFormUiState,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val submitText = if (state.isEditMode) "Bütçeyi Güncelle" else "Bütçeyi Kaydet"

    Button(
        onClick = {
            if (!state.isSubmitting) {
                onSubmit()
            }
        },
        enabled = state.canSubmit,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .semantics {
                contentDescription = submitText
            },
        shape = RoundedCornerShape(FeniqoRadius.Medium),
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = submitText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

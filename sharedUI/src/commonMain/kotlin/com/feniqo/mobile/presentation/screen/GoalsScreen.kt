package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RateBasisPoints
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.CreateGoalActionCard
import com.feniqo.mobile.presentation.component.EmptyState
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.GoalCard
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.goal.*
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTypographyTokens
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/** Hedef yönetiminin stateless, Room-verisiyle beslenen warm-luxury sunumu. */
@Composable
fun GoalsScreen(
    state: GoalsUiState,
    onRetry: () -> Unit,
    onAddGoal: () -> Unit,
    onGoalClick: (EntityId) -> Unit,
    onFilterSelected: (GoalStatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            item("header") { GoalsHeader(state.activeWorkspaceName) }
            when {
                state.isLoading -> item("loading") { LoadingContent(message = "Hedefler yükleniyor…", modifier = Modifier.fillParentMaxWidth().heightIn(min = 280.dp)) }
                state.observationError != null -> item("error") { ErrorState("Hedefler Yüklenemedi", state.observationError.toDisplayText(), onRetry) }
                state.isEmpty -> item("empty") { GoalsEmptyState(onAddGoal) }
                else -> {
                    item("summary") { GoalsSummaryCard(state.summary, state.isSummaryCalculationError, state.selectedFilter) }
                    item("filter") { GoalFilterRow(state.selectedFilter, onFilterSelected) }
                    item("section") { GoalsSectionHeading("Hedeflerin", "${state.visibleGoals.size} hedef gösteriliyor") }
                    if (state.isFilterEmpty) {
                        item("filter-empty") { FilterEmptyState(state.selectedFilter, onShowAll = { onFilterSelected(GoalStatusFilter.ALL) }) }
                    } else {
                        items(state.visibleGoals, key = { it.id.value }) { goal -> GoalCard(goal, onClick = { onGoalClick(goal.id) }) }
                    }
                    item("create") { CreateGoalActionCard(onAddGoal) }
                    if (state.insights.isNotEmpty()) {
                        item("insights-heading") { GoalsSectionHeading("İçgörüler") }
                        items(state.insights, key = { it.id }) { insight -> GoalInsightCard(insight) }
                    }
                }
            }
            item("bottom") { Spacer(Modifier.height(FeniqoSpacing.Screen)) }
        }
    }
}

@Composable
private fun GoalsHeader(workspaceName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall)) {
        Text("Hedefler", style = FeniqoTypographyTokens.DisplayTitle, color = MaterialTheme.colorScheme.onBackground)
        Text("Hayallerini planlara dönüştür.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ActiveWorkspaceIndicator(workspaceName = workspaceName, isCompact = true)
    }
}

@Composable
private fun GoalsSummaryCard(summary: GoalsSummaryUiModel?, isError: Boolean, filter: GoalStatusFilter) {
    Card(shape = RoundedCornerShape(FeniqoRadius.Large), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(FeniqoSpacing.Large), verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium)) {
            Text("Hedef özeti · ${filter.label}", style = MaterialTheme.typography.titleLarge)
            when {
                isError -> Text("Özet güvenle hesaplanamadı.", style = MaterialTheme.typography.bodyMedium)
                summary == null -> Text("Bu kapsamda gösterilecek hedef özeti yok.", style = MaterialTheme.typography.bodyMedium)
                else -> {
                    summary.currencySummaries.take(3).forEach { currency ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(currency.currency.name, style = MaterialTheme.typography.labelLarge)
                            Text("${currency.formattedSavedAmount} / ${currency.formattedTargetAmount}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (summary.currencySummaries.size > 3) Text("${summary.currencySummaries.size} para biriminde", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .18f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryMetric("Aktif", summary.activeGoalCount.toString())
                        SummaryMetric("Tamamlanan", summary.achievedGoalCount.toString())
                        SummaryMetric("Ortalama ilerleme", summary.averageProgressBasisPoints?.let { "%${it.value / 100}" } ?: "—")
                    }
                }
            }
        }
    }
}

@Composable private fun SummaryMetric(label: String, value: String) = Column(Modifier.widthIn(max = 120.dp)) { Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }

@Composable
private fun GoalFilterRow(selected: GoalStatusFilter, onSelected: (GoalStatusFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small), modifier = Modifier.fillMaxWidth()) {
        GoalStatusFilter.entries.forEach { filter ->
            FilterChip(selected = selected == filter, onClick = { onSelected(filter) }, label = { Text(filter.label) }, modifier = Modifier.semantics { contentDescription = "${filter.label} hedefleri filtrele" })
        }
    }
}

@Composable private fun GoalsSectionHeading(title: String, subtitle: String? = null) = Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun GoalsEmptyState(onAddGoal: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = FeniqoSpacing.Screen), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium)) {
        EmptyState("Henüz hedefin yok", "Gelecek planların için ilk hedefini oluştur.")
        CreateGoalActionCard(onAddGoal)
    }
}

@Composable
private fun FilterEmptyState(filter: GoalStatusFilter, onShowAll: () -> Unit) {
    Card(shape = RoundedCornerShape(FeniqoRadius.Medium), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FeniqoSpacing.Large), verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small)) {
            Text("${filter.label} hedef bulunmuyor", style = MaterialTheme.typography.titleMedium)
            Text("Farklı bir durum filtresi seçebilirsin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (filter != GoalStatusFilter.ALL) TextButton(onClick = onShowAll) { Text("Tüm hedefleri göster") }
        }
    }
}

@Composable
private fun GoalInsightCard(insight: GoalInsightUiModel) {
    Card(shape = RoundedCornerShape(FeniqoRadius.Medium), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FeniqoSpacing.Medium), verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall)) {
            Text(insight.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(insight.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val previewGoal = GoalDisplayModel(EntityId("preview"), "Uzun Türkçe hedef adı için okunabilirlik denemesi", Money(100_000, Currency.TRY), Money(120_000, Currency.TRY), Money(0, Currency.TRY), "1.000,00 ₺", "1.200,00 ₺", "0,00 ₺", Currency.TRY, LocalDate(2026, 9, 1), "1 Eylül 2026", "#4E8C62", "savings", GoalStatus.ACHIEVED, RateBasisPoints(12_000), 1f, true)
private val previewSummary = GoalsSummaryUiModel(1, 0, 1, RateBasisPoints(10_000), listOf(GoalCurrencySummaryUiModel(Currency.TRY, Money(120_000, Currency.TRY), Money(100_000, Currency.TRY), "1.200,00 ₺", "1.000,00 ₺")))

@Composable private fun PreviewGoals(state: GoalsUiState, dark: Boolean = false) = FeniqoTheme(darkTheme = dark) { GoalsScreen(state, {}, {}, {}, {}) }

@Preview(name = "Goals - Loading", showBackground = true) @Composable private fun GoalsLoadingPreview() = PreviewGoals(GoalsUiState(isLoading = true))
@Preview(name = "Goals - Single currency", showBackground = true) @Composable private fun GoalsSingleCurrencyPreview() = PreviewGoals(GoalsUiState(isLoading = false, allGoals = listOf(previewGoal), visibleGoals = listOf(previewGoal), summary = previewSummary))
@Preview(name = "Goals - Multi currency", showBackground = true) @Composable private fun GoalsMultiCurrencyPreview() = PreviewGoals(GoalsUiState(isLoading = false, allGoals = listOf(previewGoal), visibleGoals = listOf(previewGoal), summary = previewSummary.copy(currencySummaries = previewSummary.currencySummaries + GoalCurrencySummaryUiModel(Currency.USD, Money(5_000, Currency.USD), Money(10_000, Currency.USD), "\$50.00", "\$100.00"))))
@Preview(name = "Goals - Active filter", showBackground = true) @Composable private fun GoalsActivePreview() = PreviewGoals(GoalsUiState(isLoading = false, allGoals = listOf(previewGoal), selectedFilter = GoalStatusFilter.ACTIVE))
@Preview(name = "Goals - Achieved filter", showBackground = true) @Composable private fun GoalsAchievedPreview() = PreviewGoals(GoalsUiState(isLoading = false, allGoals = listOf(previewGoal), visibleGoals = listOf(previewGoal), selectedFilter = GoalStatusFilter.ACHIEVED, summary = previewSummary))
@Preview(name = "Goals - Filter empty", showBackground = true) @Composable private fun GoalsFilterEmptyPreview() = PreviewGoals(GoalsUiState(isLoading = false, allGoals = listOf(previewGoal), selectedFilter = GoalStatusFilter.ACTIVE))
@Preview(name = "Goals - Empty", showBackground = true) @Composable private fun GoalsEmptyPreview() = PreviewGoals(GoalsUiState(isLoading = false))
@Preview(name = "Goals - Summary error", showBackground = true) @Composable private fun GoalsSummaryErrorPreview() = PreviewGoals(GoalsUiState(isLoading = false, allGoals = listOf(previewGoal), visibleGoals = listOf(previewGoal), isSummaryCalculationError = true))
@Preview(name = "Goals - Observation error", showBackground = true) @Composable private fun GoalsErrorPreview() = PreviewGoals(GoalsUiState(isLoading = false, observationError = com.feniqo.mobile.presentation.common.FinanceUiMessage.GENERIC_ERROR))
@Preview(name = "Goals - Dark", showBackground = true) @Composable private fun GoalsDarkPreview() = PreviewGoals(GoalsUiState(isLoading = false, allGoals = listOf(previewGoal), visibleGoals = listOf(previewGoal), summary = previewSummary), dark = true)

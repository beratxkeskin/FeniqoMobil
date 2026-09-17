package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.feniqo.mobile.presentation.component.*
import com.feniqo.mobile.presentation.goal.*
import com.feniqo.mobile.presentation.theme.*

/**
 * Hedef yönetiminin stateless, Room-verisiyle beslenen warm-luxury sunumu.
 * Onaylı 01 Hedefler ve 11 Boş Durum panellerine tam uyumludur.
 */
@Composable
fun GoalsScreen(
    state: GoalsUiState,
    onRetry: () -> Unit,
    onAddGoal: () -> Unit,
    onGoalClick: (EntityId) -> Unit,
    onFilterSelected: (GoalStatusFilter) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = FeniqoWarmStoneBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Üst Bar: Geri (varsa), Hedefler Başlığı ve Yuvarlak (+) Buton
                item("top-bar") {
                    GoalsTopBar(
                        workspaceName = state.activeWorkspaceName,
                        onBack = onBack,
                        onAddGoal = onAddGoal,
                    )
                }

                when {
                    state.isLoading -> item("loading") {
                        LoadingContent(
                            message = "Hedefler yükleniyor…",
                            modifier = Modifier.fillParentMaxWidth().heightIn(min = 280.dp),
                        )
                    }
                    state.observationError != null -> item("error") {
                        ErrorState(
                            title = "Hedefler Yüklenemedi",
                            description = state.observationError.toDisplayText(),
                            onRetry = onRetry,
                        )
                    }
                    state.isEmpty -> item("empty") {
                        GoalsEmptyState(onAddGoal = onAddGoal)
                    }
                    else -> {
                        // Filtre Sekmeleri: Tümü, Aktif, Tamamlanan
                        item("filters") {
                            GoalFilterTabs(
                                selectedFilter = state.selectedFilter,
                                onFilterSelected = onFilterSelected,
                            )
                        }

                        // Grafit Özet Kartı
                        item("summary") {
                            GoalsSummaryHeroCard(
                                summary = state.summary,
                                isError = state.isSummaryCalculationError,
                                filter = state.selectedFilter,
                            )
                        }

                        // Hedef Kartları veya Filtre Boş Durumu
                        if (state.isFilterEmpty) {
                            item("filter-empty") {
                                FilterEmptyState(
                                    filter = state.selectedFilter,
                                    onShowAll = { onFilterSelected(GoalStatusFilter.ALL) },
                                )
                            }
                        } else {
                            items(state.visibleGoals, key = { it.id.value }) { goal ->
                                GoalCard(
                                    item = goal,
                                    onClick = { onGoalClick(goal.id) },
                                )
                            }
                        }

                        // İçgörüler
                        if (state.insights.isNotEmpty()) {
                            item("insights-heading") {
                                Text(
                                    text = "İçgörüler",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = FeniqoTextPrimary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            items(state.insights, key = { it.id }) { insight ->
                                GoalInsightCard(insight)
                            }
                        }
                    }
                }

                item("spacer") {
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Alt Sabit Eylem: "+ Yeni hedef" butonu (Liste dolu ve hata yokken)
            if (!state.isLoading && state.observationError == null && !state.isEmpty) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = FeniqoWarmStoneBackground,
                    shadowElevation = 4.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = onAddGoal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FeniqoGoalSageGreen,
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Yeni hedef",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 01 Paneline uygun Üst Bar: Geri navigasyonu, "Hedefler" başlığı ve yuvarlak (+) aksiyonu.
 */
@Composable
private fun GoalsTopBar(
    workspaceName: String?,
    onBack: (() -> Unit)?,
    onAddGoal: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = FeniqoTextPrimary,
                )
            }
            Spacer(Modifier.width(4.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Hedefler",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = FeniqoTextPrimary,
            )
            if (!workspaceName.isNullOrBlank()) {
                ActiveWorkspaceIndicator(workspaceName = workspaceName, isCompact = true)
            }
        }

        Surface(
            shape = CircleShape,
            color = FeniqoGoalSageGreen,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onAddGoal),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Yeni hedef ekle",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private val previewGoal = GoalDisplayModel(
    id = EntityId("preview"),
    name = "Seyahat",
    targetAmount = Money(3_000_000, Currency.TRY),
    currentAmount = Money(1_200_000, Currency.TRY),
    remainingAmount = Money(1_800_000, Currency.TRY),
    formattedTargetAmount = "₺30.000",
    formattedCurrentAmount = "₺12.000",
    formattedRemainingAmount = "₺18.000",
    currency = Currency.TRY,
    targetDate = LocalDate(2026, 12, 31),
    formattedTargetDate = "31 Aralık 2026",
    colorHex = "#2D5A43",
    iconKey = "travel",
    status = GoalStatus.IN_PROGRESS,
    progressBasisPoints = RateBasisPoints(4_000),
    progressFraction = 0.4f,
    isAchieved = false,
)

private val previewSummary = GoalsSummaryUiModel(
    scopedGoalCount = 2,
    activeGoalCount = 2,
    achievedGoalCount = 0,
    averageProgressBasisPoints = RateBasisPoints(4_000),
    currencySummaries = listOf(
        GoalCurrencySummaryUiModel(
            currency = Currency.TRY,
            savedAmount = Money(3_200_000, Currency.TRY),
            targetAmount = Money(8_000_000, Currency.TRY),
            formattedSavedAmount = "₺32.000",
            formattedTargetAmount = "₺80.000",
        ),
    ),
)

@Composable
private fun PreviewGoals(state: GoalsUiState, dark: Boolean = false) = FeniqoTheme(darkTheme = dark) {
    GoalsScreen(state, {}, {}, {}, {})
}

@Preview(name = "Goals - Loading", showBackground = true)
@Composable
private fun GoalsLoadingPreview() = PreviewGoals(GoalsUiState(isLoading = true))

@Preview(name = "Goals - Single currency", showBackground = true)
@Composable
private fun GoalsSingleCurrencyPreview() = PreviewGoals(GoalsUiState(isLoading = false, allGoals = listOf(previewGoal), visibleGoals = listOf(previewGoal), summary = previewSummary))

@Preview(name = "Goals - Empty", showBackground = true)
@Composable
private fun GoalsEmptyPreview() = PreviewGoals(GoalsUiState(isLoading = false))

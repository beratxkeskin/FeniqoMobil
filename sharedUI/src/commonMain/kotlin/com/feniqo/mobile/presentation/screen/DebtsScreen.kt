@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
)

package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.common.toLocalizedText
import com.feniqo.mobile.presentation.component.DebtEmptyState
import com.feniqo.mobile.presentation.component.DebtGroupedSectionCard
import com.feniqo.mobile.presentation.component.DebtInsightCard
import com.feniqo.mobile.presentation.component.DebtSnowballEntryCard
import com.feniqo.mobile.presentation.component.DebtsGraphiteSummaryCard
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.component.UpcomingPaymentsSection
import com.feniqo.mobile.presentation.debt.DebtsUiState
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Borç ve Alacaklar ana genel bakış ekranı (Panel 01).
 */
@Composable
fun DebtsScreen(
    state: DebtsUiState,
    onRetry: () -> Unit,
    onAddDebt: () -> Unit,
    onDebtClick: (EntityId) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToSnowballPlan: (() -> Unit)? = null,
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

            // Üst Başlık & Eylem Bölümü
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (onNavigateBack != null) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.debt_main_nav_back),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Text(
                            text = stringResource(Res.string.debt_main_title),
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                letterSpacing = (-0.3).sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    val addDebtDesc = stringResource(Res.string.debt_main_add_desc)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(FeniqoSageGreen)
                            .clickable(
                                role = Role.Button,
                                onClick = onAddDebt,
                            )
                            .semantics {
                                contentDescription = addDebtDesc
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(Res.string.debt_main_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Large))

            // Ana İçerik Alanı
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.isLoading -> {
                        LoadingContent(
                            message = stringResource(Res.string.debt_main_loading),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    state.observationError != null -> {
                        ErrorState(
                            title = stringResource(Res.string.debt_main_error_title),
                            description = state.observationError.toLocalizedText(),
                            onRetry = onRetry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    state.isEmpty -> {
                        DebtEmptyState(
                            onAddDebt = onAddDebt,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
                            contentPadding = PaddingValues(bottom = 32.dp),
                        ) {
                            // 1. Grafit Özet Kartı (#303536)
                            state.summary?.let { summary ->
                                item(key = "graphite_summary_card") {
                                    DebtsGraphiteSummaryCard(summary = summary)
                                }
                            }

                            // 2. Açıklanabilir Finansal İçgörü Kartı (varsa)
                            state.insight?.let { insight ->
                                item(key = "debt_insight_card") {
                                    DebtInsightCard(
                                        insight = insight,
                                        onClick = if (insight.payload is com.feniqo.mobile.presentation.debt.DebtInsightPayload.SnowballSuggestion) {
                                            onNavigateToSnowballPlan
                                        } else null,
                                    )
                                }
                            }

                            // 3. Yaklaşan Vadeler Bölümü
                            if (state.upcomingItems.isNotEmpty()) {
                                item(key = "upcoming_section") {
                                    UpcomingPaymentsSection(
                                        upcomingItems = state.upcomingItems,
                                        onItemClick = { onDebtClick(it.id) },
                                    )
                                }
                            }

                            // 4. Borçlarım Grup Kartı
                            if (state.activeDebts.isNotEmpty()) {
                                item(key = "debts_group_card") {
                                    DebtGroupedSectionCard(
                                        title = stringResource(Res.string.debt_main_my_debts_title),
                                        countText = pluralStringResource(
                                            Res.plurals.debt_active_debt_count_plural,
                                            state.activeDebts.size,
                                            state.activeDebts.size,
                                        ),
                                        isDebtSection = true,
                                        items = state.activeDebts,
                                        onItemClick = onDebtClick,
                                    )
                                }
                            }

                            // 5. Alacaklarım Grup Kartı
                            if (state.activeReceivables.isNotEmpty()) {
                                item(key = "receivables_group_card") {
                                    DebtGroupedSectionCard(
                                        title = stringResource(Res.string.debt_main_my_receivables_title),
                                        countText = pluralStringResource(
                                            Res.plurals.debt_active_receivable_count_plural,
                                            state.activeReceivables.size,
                                            state.activeReceivables.size,
                                        ),
                                        isDebtSection = false,
                                        items = state.activeReceivables,
                                        onItemClick = onDebtClick,
                                    )
                                }
                            }

                            // 6. Tamamlananlar / Kapanan Kayıtlar (varsa)
                            if (state.settledItems.isNotEmpty()) {
                                item(key = "settled_group_card") {
                                    DebtGroupedSectionCard(
                                        title = stringResource(Res.string.debt_main_settled_title),
                                        countText = pluralStringResource(
                                            Res.plurals.debt_settled_count_plural,
                                            state.settledItems.size,
                                            state.settledItems.size,
                                        ),
                                        isDebtSection = true,
                                        items = state.settledItems,
                                        onItemClick = onDebtClick,
                                    )
                                }
                            }

                            // 7. Borç Kapatma Planı Giriş Kartı
                            if (onNavigateToSnowballPlan != null) {
                                item(key = "snowball_entry_card") {
                                    DebtSnowballEntryCard(onClick = onNavigateToSnowballPlan)
                                }
                            }

                            // 8. Alt '+ Yeni kayıt' birincil butonu (56dp)
                            item(key = "add_debt_button") {
                                Button(
                                    onClick = onAddDebt,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.debt_main_add_button),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
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

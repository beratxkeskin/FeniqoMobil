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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.DebtGroupedSectionCard
import com.feniqo.mobile.presentation.component.DebtInsightCard
import com.feniqo.mobile.presentation.component.DebtSummaryCardsRow
import com.feniqo.mobile.presentation.component.EmptyState
import com.feniqo.mobile.presentation.component.ErrorState
import com.feniqo.mobile.presentation.component.LoadingContent
import com.feniqo.mobile.presentation.component.UpcomingPaymentsSection
import com.feniqo.mobile.presentation.debt.DebtsUiState
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary
import com.feniqo.mobile.presentation.theme.FeniqoWarmStoneBackground

/**
 * Borç ve alacaklar liste ekranının warm-luxury Jetpack Compose sunumudur.
 * Referans görseldeki görsel hiyerarşi, ferah kart düzeni, üçlü finans özeti,
 * yaklaşan vadeler, borç/alacak ayrımı ve Feniqo içgörü kartı ile sunulur.
 */
@Composable
fun DebtsScreen(
    state: DebtsUiState,
    onRetry: () -> Unit,
    onAddDebt: () -> Unit,
    onDebtClick: (EntityId) -> Unit,
    onNavigateToSnowballPlan: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FeniqoWarmStoneBackground,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FeniqoSpacing.Large),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Üst Başlık & Eylem Bölümü (Referans görseldeki görsel hiyerarşi)
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Satır: Marka ve Çalışma Alanı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Feniqo",
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = FeniqoTextPrimary,
                    )
                    ActiveWorkspaceIndicator(
                        workspaceName = state.activeWorkspaceName,
                        isCompact = true,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Satır: Başlık ("Borçlar ve Alacaklar") & Sağ Üst Yeşil '+' Dairesel Buton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Borçlar ve Alacaklar",
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = FeniqoTextPrimary,
                        modifier = Modifier.weight(1f),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (onNavigateToSnowballPlan != null && !state.isLoading && state.observationError == null) {
                            OutlinedButton(
                                onClick = onNavigateToSnowballPlan,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp),
                            ) {
                                Text(
                                    text = "Plan",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        // Referans görseldeki dairesel yeşil '+' butonu
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(FeniqoSageGreen)
                                .clickable(
                                    role = Role.Button,
                                    onClick = onAddDebt,
                                )
                                .semantics {
                                    contentDescription = "Yeni borç veya alacak ekle"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 3. Satır: Rehber Alt Açıklama
                Text(
                    text = "Kime ne kadar borcunuz olduğunu ve kimden alacağınız olduğunu takip edin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FeniqoTextSecondary,
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
                            message = "Borç ve alacaklar yükleniyor...",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    state.observationError != null -> {
                        ErrorState(
                            title = "Borç ve Alacaklar Yüklenemedi",
                            description = state.observationError.toDisplayText(),
                            onRetry = onRetry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    state.isEmpty -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                        ) {
                            EmptyState(
                                title = "Kayıtlı borç veya alacak yok.",
                                description = "Kişi ve kurumlara olan borçlarınızı ve alacaklarınızı ekleyerek net durumunuzu kolayca yönetin.",
                            )
                            Button(
                                onClick = onAddDebt,
                                shape = RoundedCornerShape(FeniqoRadius.Medium),
                            ) {
                                Text("Borç / Alacak Ekle")
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
                            contentPadding = PaddingValues(bottom = 32.dp),
                        ) {
                            // 1. Üçlü Finans Özeti (Borcunuz, Alacağınız, Net durum)
                            state.summary?.let { summary ->
                                item(key = "summary_cards") {
                                    DebtSummaryCardsRow(summary = summary)
                                }
                            }

                            // 2. Yaklaşan Vadeler Kartı (Upcoming Payments)
                            if (state.upcomingItems.isNotEmpty()) {
                                item(key = "upcoming_section") {
                                    UpcomingPaymentsSection(
                                        upcomingItems = state.upcomingItems,
                                        onItemClick = { onDebtClick(it.id) },
                                    )
                                }
                            }

                            // 3. Borçlarım Grup Kartı (Your Debts)
                            if (state.activeDebts.isNotEmpty()) {
                                item(key = "debts_group_card") {
                                    DebtGroupedSectionCard(
                                        title = "Borçlarım",
                                        countText = "${state.activeDebts.size} aktif borç",
                                        isDebtSection = true,
                                        items = state.activeDebts,
                                        onItemClick = onDebtClick,
                                    )
                                }
                            }

                            // 4. Alacaklarım Grup Kartı (Receivables)
                            if (state.activeReceivables.isNotEmpty()) {
                                item(key = "receivables_group_card") {
                                    DebtGroupedSectionCard(
                                        title = "Alacaklarım",
                                        countText = "${state.activeReceivables.size} aktif alacak",
                                        isDebtSection = false,
                                        items = state.activeReceivables,
                                        onItemClick = onDebtClick,
                                    )
                                }
                            }

                            // 5. Tamamlanan / Kapanan Kayıtlar (varsa)
                            if (state.settledItems.isNotEmpty()) {
                                item(key = "settled_group_card") {
                                    DebtGroupedSectionCard(
                                        title = "Tamamlananlar",
                                        countText = "${state.settledItems.size} kapalı kayıt",
                                        isDebtSection = true,
                                        items = state.settledItems,
                                        onItemClick = onDebtClick,
                                    )
                                }
                            }

                            // 6. Feniqo İçgörü Kartı (Insight Card)
                            state.insight?.let { insight ->
                                item(key = "insight_card") {
                                    DebtInsightCard(
                                        insight = insight,
                                        onClick = onNavigateToSnowballPlan,
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

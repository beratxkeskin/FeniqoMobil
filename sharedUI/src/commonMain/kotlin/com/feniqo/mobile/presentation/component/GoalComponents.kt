package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.GoalStatus
import com.feniqo.mobile.presentation.goal.GoalDisplayModel
import com.feniqo.mobile.presentation.goal.GoalInsightUiModel
import com.feniqo.mobile.presentation.goal.GoalStatusFilter
import com.feniqo.mobile.presentation.goal.GoalsSummaryUiModel
import com.feniqo.mobile.presentation.theme.*

// Onaylı görsel referans renkleri
val FeniqoGoalGraphite = Color(0xFF303536)
val FeniqoGoalSageGreen = Color(0xFF2D5A43)
val FeniqoGoalSageGreenLight = Color(0xFFE8F1EC)
val FeniqoGoalExpenseRed = Color(0xFFC0392B)
val FeniqoGoalCardBorder = Color(0xFFE5E7EB)

/**
 * 01 Hedefler ekranı için Grafit Özet Kartı (#303536).
 * Filtreye göre dinamik başlık, büyük biriken tutar, toplam hedef,
 * hedef sayısı ve ilerleme çubuğunu içerir.
 */
@Composable
fun GoalsSummaryHeroCard(
    summary: GoalsSummaryUiModel?,
    isError: Boolean,
    filter: GoalStatusFilter,
    modifier: Modifier = Modifier,
) {
    val filterTitle = when (filter) {
        GoalStatusFilter.ALL -> "Hedeflerde biriken"
        GoalStatusFilter.ACTIVE -> "Aktif hedeflerde biriken"
        GoalStatusFilter.ACHIEVED -> "Tamamlanan hedeflerde biriken"
    }

    val countLabel = when (filter) {
        GoalStatusFilter.ALL -> "toplam hedef"
        GoalStatusFilter.ACTIVE -> "aktif hedef"
        GoalStatusFilter.ACHIEVED -> "tamamlanan hedef"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = FeniqoGoalGraphite,
            contentColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                isError -> {
                    Text(
                        text = "Özet güvenle hesaplanamadı.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE0E0E0),
                    )
                }
                summary == null || summary.currencySummaries.isEmpty() -> {
                    Text(
                        text = filterTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFB0B8BA),
                    )
                    Text(
                        text = "₺0",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "Bu filtrede gösterilecek birikim bulunmuyor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E9799),
                    )
                }
                else -> {
                    val primaryCurrency = summary.currencySummaries.first()
                    val progressRatio = (summary.averageProgressBasisPoints?.value?.coerceIn(0, 10_000)?.toFloat() ?: 0f) / 10_000f
                    val progressPercent = summary.averageProgressBasisPoints?.let { it.value / 100 } ?: 0

                    // Üst Kısım: Başlık & Hedef Sayısı
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = filterTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFFB0B8BA),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = primaryCurrency.formattedSavedAmount,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${primaryCurrency.formattedTargetAmount} toplam hedef",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB0B8BA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Sağ Üst: Hedef Sayacı
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            Text(
                                text = summary.scopedGoalCount.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = countLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB0B8BA),
                                textAlign = TextAlign.End,
                            )
                        }
                    }

                    // İlerleme Çubuğu ve Yüzde
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .weight(1f)
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF34D399),
                            trackColor = Color(0xFF454B4D),
                        )
                        Text(
                            text = "%$progressPercent",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE0E0E0),
                        )
                    }

                    // Çoklu Para Birimi Varsa Ek Satırlar
                    if (summary.currencySummaries.size > 1) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        summary.currencySummaries.drop(1).forEach { extra ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = extra.currency.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB0B8BA),
                                )
                                Text(
                                    text = "${extra.formattedSavedAmount} / ${extra.formattedTargetAmount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
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

/**
 * 01 Hedefler ekranı filtre çipleri (Tümü, Aktif, Tamamlanan).
 * Seçili olan Feniqo yeşili (#2D5A43), seçilmeyenler sakin açık yüzey.
 */
@Composable
fun GoalFilterTabs(
    selectedFilter: GoalStatusFilter,
    onFilterSelected: (GoalStatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GoalStatusFilter.entries.forEach { filter ->
            val isSelected = selectedFilter == filter
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Tab) { onFilterSelected(filter) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) FeniqoGoalSageGreen else Color.White,
                border = if (isSelected) null else BorderStroke(1.dp, FeniqoGoalCardBorder),
                shadowElevation = if (isSelected) 1.dp else 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White else FeniqoTextPrimary,
                    )
                }
            }
        }
    }
}

/**
 * 01 Hedefler ekranı hedef liste kartı.
 */
@Composable
fun GoalCard(
    item: GoalDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = if (item.isAchieved) "Tamamlandı" else "Aktif"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "${item.name}. ${item.formattedCurrentAmount} birikmiş, hedef ${item.formattedTargetAmount}, yüzde ${item.progressBasisPoints.value / 100}, $statusText"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, FeniqoGoalCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Üst Satır: İkon + İsim + Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(FeniqoGoalSageGreenLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CategorySemanticIconResolver.resolve(item.iconKey),
                        contentDescription = null,
                        tint = FeniqoGoalSageGreen,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FeniqoTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = FeniqoTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Tutar Satırı: ₺12.000 / ₺30.000
            Text(
                text = "${item.formattedCurrentAmount} / ${item.formattedTargetAmount}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = FeniqoTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // İlerleme Çubuğu ve Yüzde
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .semantics {
                            progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(item.progressFraction, 0f..1f)
                        },
                    color = if (item.isAchieved) FeniqoTrendGreen else FeniqoGoalSageGreen,
                    trackColor = Color(0xFFF1F5F9),
                )
                Text(
                    text = "%${item.progressBasisPoints.value / 100}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isAchieved) FeniqoTrendGreen else FeniqoGoalSageGreen,
                )
            }

            // Alt Satır: Hedef Tarihi ve Vade Durumu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = FeniqoTextSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "Hedef tarihi: ${item.formattedTargetDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FeniqoTextSecondary,
                    )
                }

                when {
                    item.isAchieved -> Text(
                        text = "Hedefe ulaşıldı",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = FeniqoTrendGreen,
                    )
                    item.isTargetDatePast -> Text(
                        text = "Hedef tarihi geçti",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = FeniqoGoalExpenseRed,
                    )
                    else -> Text(
                        text = "${item.formattedRemainingAmount} kaldı",
                        style = MaterialTheme.typography.labelSmall,
                        color = FeniqoTextSecondary,
                    )
                }
            }
        }
    }
}

/**
 * 11 Hedefler boş durum paneli.
 * Büyük dairesel hedef simgesi, açıklama ve "İlk hedefini oluştur" butonu.
 */
@Composable
fun GoalsEmptyState(
    onAddGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hedef İllüstrasyonu / İkon Alanı
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(FeniqoGoalSageGreenLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategorySemanticIconResolver.resolve("savings"),
                contentDescription = null,
                tint = FeniqoGoalSageGreen,
                modifier = Modifier.size(52.dp),
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Henüz bir hedefin yok",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = FeniqoTextPrimary,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "İlk hedefini oluşturarak\nbirikimini takip et.",
            style = MaterialTheme.typography.bodyMedium,
            color = FeniqoTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onAddGoal,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FeniqoGoalSageGreen,
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "İlk hedefini oluştur",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Filtrede sonuç bulunamadığında gösterilen kart.
 */
@Composable
fun FilterEmptyState(
    filter: GoalStatusFilter,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, FeniqoGoalCardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${filter.label} hedef bulunmuyor",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = FeniqoTextPrimary,
            )
            Text(
                text = "Farklı bir durum filtresi seçebilirsin.",
                style = MaterialTheme.typography.bodySmall,
                color = FeniqoTextSecondary,
            )
            if (filter != GoalStatusFilter.ALL) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onShowAll) {
                    Text("Tüm hedefleri göster", color = FeniqoGoalSageGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 10 Hedefi sil onay diyaloğu.
 * Kırmızı çöp kutusu ikonu, hedef adı, "Vazgeç" ve "Sil" butonları.
 */
@Composable
fun GoalDeleteDialog(
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    goalName: String? = null,
) {
    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Kırmızı Daire İkon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Başlık & Açıklama
                Text(
                    text = "Hedefi silinsin mi?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoTextPrimary,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = if (!goalName.isNullOrBlank()) {
                        "$goalName hedefini silmek istediğine emin misin?"
                    } else {
                        "Bu hedefi ve birikim kayıtlarını silmek istediğine emin misin?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = FeniqoTextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(4.dp))

                // Butonlar (Yan yana eşit)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF1F5F9),
                            contentColor = Color(0xFF334155),
                        ),
                    ) {
                        Text("Vazgeç", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onConfirm,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White,
                        ),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Sil", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 09 Para birimi seçim modalı (ModalBottomSheet).
 * Desteklenen para birimlerini listeleyip seçim sunar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerSheet(
    selectedCurrency: Currency,
    onCurrencySelected: (Currency) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Para birimi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoTextPrimary,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Kapat", tint = FeniqoTextSecondary)
                }
            }

            Currency.entries.forEach { currency ->
                val isSelected = currency == selectedCurrency
                val symbol = when (currency) {
                    Currency.TRY -> "₺"
                    Currency.USD -> "$"
                    Currency.EUR -> "€"
                }
                val name = when (currency) {
                    Currency.TRY -> "Türk lirası"
                    Currency.USD -> "Amerikan doları"
                    Currency.EUR -> "Euro"
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onCurrencySelected(currency)
                            onDismiss()
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) FeniqoGoalSageGreenLight else Color.Transparent,
                    border = if (isSelected) null else BorderStroke(1.dp, FeniqoGoalCardBorder),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = symbol,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) FeniqoGoalSageGreen else FeniqoTextPrimary,
                            modifier = Modifier.width(32.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currency.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FeniqoTextPrimary,
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = FeniqoTextSecondary,
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(FeniqoGoalSageGreen),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color(0xFFCBD5E1), CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Geriye dönük uyumluluk için eski CreateGoalActionCard bileşeni.
 */
@Composable
fun CreateGoalActionCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Yeni hedef oluştur" },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, FeniqoGoalCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = FeniqoGoalSageGreenLight,
                contentColor = FeniqoGoalSageGreen,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Yeni hedef oluştur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Hayallerini somut bir plana dönüştür.", style = MaterialTheme.typography.bodySmall, color = FeniqoTextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FeniqoTextSecondary)
        }
    }
}

/**
 * İçgörü kartı.
 */
@Composable
fun GoalInsightCard(insight: GoalInsightUiModel, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, FeniqoGoalCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(FeniqoGoalSageGreenLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = FeniqoGoalSageGreen,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = FeniqoTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = FeniqoTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

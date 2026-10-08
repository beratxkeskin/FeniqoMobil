package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.debt.DebtDisplayModel
import com.feniqo.mobile.presentation.debt.DebtDueStatus
import com.feniqo.mobile.presentation.debt.DebtInsightUiModel
import com.feniqo.mobile.presentation.debt.DebtsSummaryUiModel
import com.feniqo.mobile.presentation.debt.resolveTitleAndDescription
import com.feniqo.mobile.presentation.debt.toLocalizedDebtTypeLabel
import com.feniqo.mobile.presentation.debt.toLocalizedDueStatusLabel
import com.feniqo.mobile.presentation.common.toLocalizedNameText
import com.feniqo.mobile.presentation.common.toLocalizedReadableDate
import com.feniqo.mobile.presentation.common.toLocalizedShortReadableDate
import com.feniqo.mobile.presentation.debt.toLocalizedText
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTabularNumberStyle
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget
import com.feniqo.mobile.presentation.theme.FeniqoTrendGreen
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.budget_overview_excluded_currency
import feniqomobil.sharedui.generated.resources.debt_currency_picker_close
import feniqomobil.sharedui.generated.resources.debt_currency_picker_title
import feniqomobil.sharedui.generated.resources.debt_date_picker_close
import feniqomobil.sharedui.generated.resources.debt_date_picker_confirm
import feniqomobil.sharedui.generated.resources.debt_date_picker_default_title
import feniqomobil.sharedui.generated.resources.debt_delete_dialog_cancel
import feniqomobil.sharedui.generated.resources.debt_delete_dialog_confirm
import feniqomobil.sharedui.generated.resources.debt_delete_dialog_content_desc
import feniqomobil.sharedui.generated.resources.debt_delete_dialog_desc_general
import feniqomobil.sharedui.generated.resources.debt_delete_dialog_desc_with_title
import feniqomobil.sharedui.generated.resources.debt_delete_dialog_title
import feniqomobil.sharedui.generated.resources.debt_due_status_due_today
import feniqomobil.sharedui.generated.resources.debt_empty_create_button
import feniqomobil.sharedui.generated.resources.debt_empty_desc
import feniqomobil.sharedui.generated.resources.debt_empty_title
import feniqomobil.sharedui.generated.resources.debt_item_remaining_prefix
import feniqomobil.sharedui.generated.resources.debt_item_settled_label
import feniqomobil.sharedui.generated.resources.debt_settled_banner_debt
import feniqomobil.sharedui.generated.resources.debt_settled_banner_receivable
import feniqomobil.sharedui.generated.resources.debt_snowball_card_title
import feniqomobil.sharedui.generated.resources.debt_snowball_subtitle
import feniqomobil.sharedui.generated.resources.debt_snowball_title
import feniqomobil.sharedui.generated.resources.debt_summary_my_debts
import feniqomobil.sharedui.generated.resources.debt_summary_my_receivables
import feniqomobil.sharedui.generated.resources.debt_summary_net_position
import feniqomobil.sharedui.generated.resources.debt_summary_person_count_plural
import feniqomobil.sharedui.generated.resources.debt_summary_record_count_plural
import feniqomobil.sharedui.generated.resources.debt_upcoming_collapse_desc
import feniqomobil.sharedui.generated.resources.debt_upcoming_expand_desc
import feniqomobil.sharedui.generated.resources.debt_upcoming_section_title
import feniqomobil.sharedui.generated.resources.debt_upcoming_see_all
import feniqomobil.sharedui.generated.resources.debt_upcoming_show_less
import feniqomobil.sharedui.generated.resources.debt_upcoming_subtitle_within_7_days_plural
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Referans görseldeki ↗ (sağ üst) kırmızı çapraz ok ikonu.
 */
@Composable
fun ArrowUpRightIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val arrow = Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.75f)
            lineTo(size.width * 0.75f, size.height * 0.25f)
            moveTo(size.width * 0.40f, size.height * 0.25f)
            lineTo(size.width * 0.75f, size.height * 0.25f)
            lineTo(size.width * 0.75f, size.height * 0.60f)
        }
        drawPath(
            path = arrow,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * Referans görseldeki ↙ (sol alt) yeşil çapraz ok ikonu.
 */
@Composable
fun ArrowDownLeftIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val arrow = Path().apply {
            moveTo(size.width * 0.75f, size.height * 0.25f)
            lineTo(size.width * 0.25f, size.height * 0.75f)
            moveTo(size.width * 0.60f, size.height * 0.75f)
            lineTo(size.width * 0.25f, size.height * 0.75f)
            lineTo(size.width * 0.25f, size.height * 0.40f)
        }
        drawPath(
            path = arrow,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * Referans Görsel 01'deki grafit "Net durum" özet kartıdır.
 */
@Composable
fun DebtsGraphiteSummaryCard(
    summary: DebtsSummaryUiModel,
    modifier: Modifier = Modifier,
) {
    val netAmountColor = when {
        summary.isNetNegative -> Color(0xFFEF4444)
        summary.isNetPositive -> Color(0xFF10B981)
        else -> Color.White
    }

    val localizedNetBalance = if (summary.netBalanceMinor == 0L) {
        Money(0L, summary.baseCurrency).toLocalizedFormatted()
    } else {
        summary.netBalanceDelta.toLocalizedFormatted(showPositiveSign = true)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF303536),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Net Durum Başlığı ve Büyük Tutar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(Res.string.debt_summary_net_position),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                    )
                    Text(
                        text = localizedNetBalance,
                        style = MaterialTheme.typography.headlineMedium.merge(FeniqoTabularNumberStyle),
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        color = netAmountColor,
                    )
                }

                HorizontalDivider(
                    color = Color(0xFF434A4C),
                    thickness = 1.dp,
                )

                // İki Eşit Kolon: Borçlarım & Alacaklarım
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Sol Kolon: Borçlarım
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.debt_summary_my_debts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = summary.totalDebt.toLocalizedFormatted(),
                            style = MaterialTheme.typography.titleMedium.merge(FeniqoTabularNumberStyle),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = pluralStringResource(
                                Res.plurals.debt_summary_record_count_plural,
                                summary.activeDebtCount,
                                summary.activeDebtCount,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }

                    // Sağ Kolon: Alacaklarım
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.debt_summary_my_receivables),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = summary.totalReceivable.toLocalizedFormatted(),
                            style = MaterialTheme.typography.titleMedium.merge(FeniqoTabularNumberStyle),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = pluralStringResource(
                                    Res.plurals.debt_summary_record_count_plural,
                                    summary.activeReceivableCount,
                                    summary.activeReceivableCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                            Text(
                                text = summary.baseCurrency.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        // Çoklu para birimi güvenliği uyarısı
        if (summary.excludedCurrenciesCount > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeniqoRadius.Small),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(
                            Res.string.budget_overview_excluded_currency,
                            summary.excludedCurrenciesCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * 3'lü finansal özet kartları bileşeni.
 */
@Composable
fun DebtSummaryCardsRow(
    summary: DebtsSummaryUiModel,
    modifier: Modifier = Modifier,
    onDebtSummaryClick: (() -> Unit)? = null,
    onReceivableSummaryClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. Toplam Borç Kartı
            DebtSummaryCard(
                title = stringResource(Res.string.debt_summary_my_debts),
                amount = summary.totalDebt.toLocalizedFormatted(),
                countText = pluralStringResource(
                    Res.plurals.debt_summary_person_count_plural,
                    summary.activeDebtCount,
                    summary.activeDebtCount,
                ),
                icon = {
                    ArrowUpRightIcon(
                        color = Color(0xFFDC2626),
                        modifier = Modifier.size(14.dp),
                    )
                },
                iconBgColor = Color(0xFFFDE8E8),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                borderColor = Color(0xFFFFE4E1),
                onClick = onDebtSummaryClick,
                modifier = Modifier.weight(1f),
            )

            // 2. Toplam Alacak Kartı
            DebtSummaryCard(
                title = stringResource(Res.string.debt_summary_my_receivables),
                amount = summary.totalReceivable.toLocalizedFormatted(),
                countText = pluralStringResource(
                    Res.plurals.debt_summary_person_count_plural,
                    summary.activeReceivableCount,
                    summary.activeReceivableCount,
                ),
                icon = {
                    ArrowDownLeftIcon(
                        color = Color(0xFF16A34A),
                        modifier = Modifier.size(14.dp),
                    )
                },
                iconBgColor = Color(0xFFDCFCE7),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                borderColor = Color(0xFFD7F0E3),
                onClick = onReceivableSummaryClick,
                modifier = Modifier.weight(1f),
            )

            // 3. Net Durum Kartı
            val netAmountColor = when {
                summary.isNetNegative -> Color(0xFFDC2626)
                summary.isNetPositive -> Color(0xFF16A34A)
                else -> MaterialTheme.colorScheme.onSurface
            }

            val localizedNet = if (summary.netBalanceMinor == 0L) {
                Money(0L, summary.baseCurrency).toLocalizedFormatted()
            } else {
                summary.netBalanceDelta.toLocalizedFormatted(showPositiveSign = true)
            }

            DebtSummaryCard(
                title = stringResource(Res.string.debt_summary_net_position),
                amount = localizedNet,
                amountColor = netAmountColor,
                countText = summary.netStatus.toLocalizedText(),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Scale,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                },
                iconBgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                containerColor = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                onClick = null,
                modifier = Modifier.weight(1f),
            )
        }

        // Çoklu para birimi güvenliği uyarısı
        if (summary.excludedCurrenciesCount > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeniqoRadius.Small),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = FeniqoSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(
                            Res.string.budget_overview_excluded_currency,
                            summary.excludedCurrenciesCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DebtSummaryCard(
    title: String,
    amount: String,
    countText: String,
    icon: @Composable () -> Unit,
    iconBgColor: Color,
    containerColor: Color,
    borderColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(clickableModifier)
            .semantics {
                contentDescription = "$title, $amount, $countText"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.merge(FeniqoTabularNumberStyle),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = amountColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/**
 * Referans görseldeki "Yaklaşan Vadeler" kartı bileşenidir.
 */
@Composable
fun UpcomingPaymentsSection(
    upcomingItems: List<DebtDisplayModel>,
    onItemClick: (DebtDisplayModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (upcomingItems.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }
    val subtitle = pluralStringResource(
        Res.plurals.debt_upcoming_subtitle_within_7_days_plural,
        upcomingItems.size,
        upcomingItems.size,
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Large)),
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
        ) {
            // Başlık Satırı
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(Res.string.debt_upcoming_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val collapseDesc = stringResource(Res.string.debt_upcoming_collapse_desc)
                val expandDesc = stringResource(Res.string.debt_upcoming_expand_desc)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isExpanded = !isExpanded }
                        .semantics {
                            contentDescription = if (isExpanded) collapseDesc else expandDesc
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = if (isExpanded && upcomingItems.size > 2) {
                                stringResource(Res.string.debt_upcoming_show_less)
                            } else {
                                stringResource(Res.string.debt_upcoming_see_all)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = if (isExpanded && upcomingItems.size > 2) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            val displayedItems = if (isExpanded || upcomingItems.size <= 2) upcomingItems else upcomingItems.take(2)
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                displayedItems.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = FeniqoSpacing.Small),
                        )
                    }
                    UpcomingPaymentItemRow(
                        item = item,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingPaymentItemRow(
    item: DebtDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainingFormatted = item.remainingAmount.toLocalizedFormatted()
    val dueFormatted = item.dueDate.toLocalizedReadableDate()
    val shortDueFormatted = item.dueDate.toLocalizedShortReadableDate()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Small))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics {
                contentDescription = "${item.title}, $remainingFormatted, $dueFormatted"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${stringResource(Res.string.debt_due_status_due_today)} • $shortDueFormatted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = remainingFormatted,
            style = MaterialTheme.typography.titleMedium.merge(FeniqoTabularNumberStyle),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
        )

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Gruplanmış borç veya alacak bölüm kartı bileşeni.
 */
@Composable
fun DebtGroupedSectionCard(
    title: String,
    countText: String,
    isDebtSection: Boolean,
    items: List<DebtDisplayModel>,
    onItemClick: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Large)),
        shape = RoundedCornerShape(FeniqoRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
                    modifier = Modifier.weight(1f),
                ) {
                    val (iconBg, iconTint) = if (isDebtSection) {
                        Pair(Color(0xFFFDE8E8), Color(0xFFDC2626))
                    } else {
                        Pair(Color(0xFFDCFCE7), Color(0xFF16A34A))
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isDebtSection) {
                            ArrowUpRightIcon(color = iconTint, modifier = Modifier.size(16.dp))
                        } else {
                            ArrowDownLeftIcon(color = iconTint, modifier = Modifier.size(16.dp))
                        }
                    }

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isExpanded = !isExpanded },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.debt_upcoming_see_all),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = if (isExpanded && items.size > 4) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            val displayedItems = if (isExpanded || items.size <= 4) items else items.take(4)
            Column(modifier = Modifier.fillMaxWidth()) {
                displayedItems.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = FeniqoSpacing.Small),
                        )
                    }
                    DebtGroupedItemRow(
                        item = item,
                        onClick = { onItemClick(item.id) },
                    )
                }
            }
        }
    }
}

/**
 * Referans görseldeki liste satırı.
 */
@Composable
fun DebtGroupedItemRow(
    item: DebtDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainingFormatted = item.remainingAmount.toLocalizedFormatted()
    val dueFormatted = item.dueDate.toLocalizedReadableDate()
    val shortDueFormatted = item.dueDate.toLocalizedShortReadableDate()
    val typeLabel = item.type.toLocalizedDebtTypeLabel()
    val dueStatusLabel = item.dueStatus.toLocalizedDueStatusLabel()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Small))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics {
                contentDescription = "${item.title}, $typeLabel, $remainingFormatted, $dueFormatted, $dueStatusLabel"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (iconBoxBg, iconTint) = if (item.type == DebtType.DEBT) {
            Pair(Color(0xFFF1EDE6), Color(0xFF475569))
        } else {
            Pair(Color(0xFFE8F1EC), FeniqoSageGreen)
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBoxBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (item.type == DebtType.DEBT) Icons.Outlined.CreditCard else Icons.Outlined.Person,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtext = if (item.isSettled) {
                stringResource(Res.string.debt_item_settled_label)
            } else if (item.type == DebtType.DEBT) {
                stringResource(Res.string.debt_item_remaining_prefix, remainingFormatted)
            } else {
                remainingFormatted
            }
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isSettled) FeniqoTrendGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (item.isSettled) {
                WarmDueStatusBadge(status = item.dueStatus, label = stringResource(Res.string.debt_item_settled_label))
            } else if (item.dueStatus is DebtDueStatus.Overdue || item.dueStatus is DebtDueStatus.DueToday) {
                WarmDueStatusBadge(status = item.dueStatus, label = dueStatusLabel)
            } else {
                Text(
                    text = shortDueFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Geriye dönük uyumluluk için liste satırı kartı.
 */
@Composable
fun WarmDebtListItem(
    item: DebtDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeniqoRadius.Medium))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        DebtGroupedItemRow(
            item = item,
            onClick = onClick,
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium, vertical = 6.dp),
        )
    }
}

/**
 * Geriye dönük uyumluluk için bölüm başlığı bileşeni.
 */
@Composable
fun DebtSectionHeader(
    title: String,
    countText: String,
    isDebtSection: Boolean,
    modifier: Modifier = Modifier,
    onSeeAllClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            val (iconBg, iconTint) = if (isDebtSection) {
                Pair(Color(0xFFFDE8E8), Color(0xFFDC2626))
            } else {
                Pair(Color(0xFFDCFCE7), Color(0xFF16A34A))
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                if (isDebtSection) {
                    ArrowUpRightIcon(color = iconTint, modifier = Modifier.size(16.dp))
                } else {
                    ArrowDownLeftIcon(color = iconTint, modifier = Modifier.size(16.dp))
                }
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (onSeeAllClick != null) {
            TextButton(
                onClick = onSeeAllClick,
                modifier = Modifier.heightIn(min = FeniqoTouchTarget.Minimum),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.debt_upcoming_see_all),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Referans görseldeki "Feniqo İçgörü" kartı.
 */
@Composable
fun DebtInsightCard(
    insight: DebtInsightUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    val (title, message) = insight.payload.resolveTitleAndDescription()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(clickableModifier)
            .semantics {
                contentDescription = "$title: $message"
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(24.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun WarmDueStatusBadge(
    status: DebtDueStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor) = when (status) {
        is DebtDueStatus.Overdue -> Pair(Color(0xFFFEE2E2), Color(0xFFDC2626))
        is DebtDueStatus.DueToday -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
        is DebtDueStatus.DueSoon -> Pair(Color(0xFFFFF0E6), Color(0xFFD96B27))
        is DebtDueStatus.OnTime -> Pair(Color(0xFFDCFCE7), Color(0xFF16A34A))
        is DebtDueStatus.Settled -> Pair(Color(0xFFF1EDE6), Color(0xFF64748B))
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/**
 * Geriye dönük uyumluluk için korunan eski DebtCard bileşeni.
 */
@Composable
fun DebtCard(
    item: DebtDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WarmDebtListItem(
        item = item,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun DebtDeleteDialog(
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    debtTitle: String = "",
) {
    val dialogContentDesc = stringResource(Res.string.debt_delete_dialog_content_desc)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        modifier = modifier.semantics {
            contentDescription = dialogContentDesc
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(Res.string.debt_delete_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
            )
        },
        text = {
            val descriptionText = if (debtTitle.isNotBlank()) {
                stringResource(Res.string.debt_delete_dialog_desc_with_title, debtTitle)
            } else {
                stringResource(Res.string.debt_delete_dialog_desc_general)
            }
            Text(
                text = descriptionText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White,
                ),
                modifier = Modifier.height(44.dp),
            ) {
                if (isSubmitting) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.debt_delete_dialog_confirm),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.height(44.dp),
            ) {
                Text(
                    text = stringResource(Res.string.debt_delete_dialog_cancel),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
    )
}

/**
 * "Borç kapatma planı" giriş kartı.
 */
@Composable
fun DebtSnowballEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardDesc = stringResource(Res.string.debt_snowball_card_title)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = cardDesc },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Leaderboard,
                    contentDescription = null,
                    tint = FeniqoSageGreen,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.debt_snowball_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                )
                Text(
                    text = stringResource(Res.string.debt_snowball_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Vade tarihi modal bottom sheet seçicisidir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDatePickerSheet(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    title: String = "",
    modifier: Modifier = Modifier,
) {
    val initialDate = selectedDate ?: LocalDate(2026, 9, 15)
    val initialUtcMillis = initialDate.toEpochDays() * 86_400_000L

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcMillis,
    )

    val resolvedTitle = title.ifBlank { stringResource(Res.string.debt_date_picker_default_title) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = Color(0xFFCBD5E1),
            ) {}
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = resolvedTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(Res.string.debt_date_picker_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                title = null,
                headline = null,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedDayContainerColor = FeniqoSageGreen,
                    todayDateBorderColor = FeniqoSageGreen,
                    selectedDayContentColor = Color.White,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            val currentMillis = datePickerState.selectedDateMillis
            val currentLocalDate = currentMillis?.let {
                LocalDate.fromEpochDays((it / 86_400_000L).toInt())
            } ?: initialDate

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentLocalDate.toLocalizedReadableDate(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                )

                Button(
                    onClick = {
                        onDateSelected(currentLocalDate)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FeniqoSageGreen,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.height(46.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.debt_date_picker_confirm),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

/**
 * Para birimi modal bottom sheet seçicisidir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtCurrencyPickerSheet(
    selectedCurrency: Currency,
    onCurrencySelected: (Currency) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = Color(0xFFCBD5E1),
            ) {}
        },
        modifier = modifier,
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
                    text = stringResource(Res.string.debt_currency_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(Res.string.debt_currency_picker_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Currency.entries.forEach { currency ->
                val isSelected = currency == selectedCurrency
                val symbol = when (currency) {
                    Currency.TRY -> "₺"
                    Currency.USD -> "$"
                    Currency.EUR -> "€"
                    Currency.GBP -> "£"
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
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(
                                    BorderStroke(
                                        2.dp,
                                        if (isSelected) FeniqoSageGreen else Color(0xFF94A3B8),
                                    ),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(FeniqoSageGreen),
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currency.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                            )
                            Text(
                                text = currency.toLocalizedNameText(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }

                        Text(
                            text = symbol,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Henüz kayıt yok" boş durum bileşeni.
 */
@Composable
fun DebtEmptyState(
    onAddDebt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = FeniqoSageGreen,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(Res.string.debt_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(Res.string.debt_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddDebt,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FeniqoSageGreen,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(Res.string.debt_empty_create_button),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
    }
}

/**
 * Tamamlanmış borç / alacak bilgi banner'ı.
 */
@Composable
fun DebtSettledBanner(
    isDebt: Boolean,
    modifier: Modifier = Modifier,
) {
    val bannerText = if (isDebt) {
        stringResource(Res.string.debt_settled_banner_debt)
    } else {
        stringResource(Res.string.debt_settled_banner_receivable)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = bannerText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 14.sp,
            )
        }
    }
}

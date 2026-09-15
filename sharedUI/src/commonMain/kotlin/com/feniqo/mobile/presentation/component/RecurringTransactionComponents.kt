package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.recurring.RecurringTransactionDisplayModel
import com.feniqo.mobile.presentation.recurring.RecurringTransactionDisplayModelMapper
import com.feniqo.mobile.presentation.theme.FeniqoExpense
import com.feniqo.mobile.presentation.theme.FeniqoPureWhite
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoTabularNumberStyle
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary
import com.feniqo.mobile.presentation.theme.FeniqoTrendGreen
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Tekrarlayan işlem kuralını onaylı tasarım dilinde (Görsel 01 & 08) listeleyen kart bileşenidir.
 */
@Composable
fun RecurringTransactionCard(
    item: RecurringTransactionDisplayModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusDescription = if (item.isPaused) "Duraklatıldı" else "Aktif"
    val nextDateText = when {
        item.isPaused -> null
        item.formattedNextOccurrenceDate != null -> "Sonraki: ${item.formattedNextOccurrenceDate}"
        else -> "Tekrar sona erdi"
    }

    val categoryColor = ColorParser.parseHexColorOrNull(item.categoryColorHex)
        ?: if (item.isCategoryMissing) MaterialTheme.colorScheme.outline else FeniqoSageGreen

    // Tutar rengi kuralı: Giderler eksi işaretli ve okunaklı kırmızı (duraklatılmış olsa dahi), Gelirler yeşil.
    val amountColor = when (item.type) {
        TransactionType.EXPENSE -> FeniqoExpense
        TransactionType.INCOME -> FeniqoTrendGreen
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "${item.displayTitle} tekrarlayan işlemi",
                onClick = onClick,
            )
            .semantics {
                contentDescription = "${item.displayTitle}, ${item.displayFormattedAmount}, ${item.formattedFrequency}, ${nextDateText ?: statusDescription}"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = FeniqoPureWhite,
        ),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sol: Kategori Tonal İkonu
            CategoryTonalIcon(
                iconKey = item.categoryIconKey,
                color = categoryColor,
                containerSize = 44.dp,
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Orta: Başlık, Tutar, Frekans ve Vade
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = FeniqoTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Tutar
                Text(
                    text = item.displayFormattedAmount,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ).merge(FeniqoTabularNumberStyle),
                    color = amountColor,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Frekans
                Text(
                    text = item.formattedFrequency,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = FeniqoTextSecondary,
                )

                if (nextDateText != null) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = nextDateText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = FeniqoTextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Sağ: Durum Rozeti ve İleri Ok
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecurringStatusBadge(isPaused = item.isPaused)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Görsel 01'deki kompakt grafit bilgilendirme kartı.
 * "Düzenini bir kez kur. Gelir ve giderlerin belirlediğin aralıklarla kaydedilsin."
 */
@Composable
fun RecurringOverviewGraphiteCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF202E28),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Düzenini bir kez kur.",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gelir ve giderlerin belirlediğin aralıklarla kaydedilsin.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    color = Color(0xFFB0C4B8),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0xFF2F443A), shape = CircleShape)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Autorenew,
                    contentDescription = null,
                    tint = Color(0xFF68D391),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Aktif / Duraklatıldı rozeti.
 */
@Composable
fun RecurringStatusBadge(
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor, text) = if (isPaused) {
        Triple(
            Color(0xFFF1F5F9),
            Color(0xFF64748B),
            "Duraklatıldı",
        )
    } else {
        Triple(
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            "Aktif",
        )
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = "Durum: $text"
        },
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/**
 * Görsel 09: Silme Onayı Modal Diyaloğu.
 * "Kural silinsin mi? Bu işlem geri alınamaz. Gelecekteki otomatik tekrarlar durdurulur."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionDeleteDialog(
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = Color.White,
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
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Sağ üst kapat butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = { if (!isSubmitting) onDismiss() },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pembe dairede kırmızı çöp kutusu ikonu
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFFFFEBEE), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Kural silinsin mi?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = FeniqoTextPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bu işlem geri alınamaz.\nGelecekteki otomatik tekrarlar durdurulur.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = FeniqoTextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Kırmızı: Kuralı sil butonu
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White,
                ),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "Kuralı sil",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Nötr: Vazgeç butonu
            Button(
                onClick = onDismiss,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF1F5F9),
                    contentColor = Color(0xFF334155),
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = "Vazgeç",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

/**
 * Görsel 04: Tekrar Düzeni Seçim Paneli (Modal Bottom Sheet).
 * Günlük / Haftalık / Aylık / Yıllık + Step aralık kontrolleri.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrencePatternSheet(
    initialFrequency: RecurrenceFrequency,
    initialInterval: Int,
    onApply: (RecurrenceFrequency, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tempFrequency by remember { mutableStateOf(initialFrequency) }
    var tempInterval by remember { mutableIntStateOf(initialInterval.coerceAtLeast(1)) }
    var intervalText by remember { mutableStateOf(tempInterval.toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
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
            // Başlık ve Kapat Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tekrar düzeni",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = FeniqoTextPrimary,
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4'lü Sıklık Kartları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val items = listOf(
                    Triple(RecurrenceFrequency.DAILY, "Günlük", Icons.Outlined.CalendarToday),
                    Triple(RecurrenceFrequency.WEEKLY, "Haftalık", Icons.Outlined.DateRange),
                    Triple(RecurrenceFrequency.MONTHLY, "Aylık", Icons.Outlined.CalendarMonth),
                    Triple(RecurrenceFrequency.YEARLY, "Yıllık", Icons.Outlined.EventRepeat),
                )

                items.forEach { (freq, label, icon) ->
                    val isSelected = freq == tempFrequency
                    Surface(
                        onClick = { tempFrequency = freq },
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) FeniqoSageGreen else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) FeniqoSageGreen else Color(0xFFE2E8F0),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color(0xFF475569),
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (isSelected) Color.White else Color(0xFF475569),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tekrar aralığı başlığı
            Text(
                text = "Tekrar aralığı",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = FeniqoTextPrimary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Azalt / Aralık Değeri / Artır Kontrolü
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Eksi butonu
                Surface(
                    onClick = {
                        if (tempInterval > 1) {
                            tempInterval -= 1
                            intervalText = tempInterval.toString()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    enabled = tempInterval > 1,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (tempInterval > 1) FeniqoTextPrimary else Color(0xFFCBD5E1),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Klavyeyle de yazılabilen aralık alanı
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }
                        intervalText = filtered
                        val parsed = filtered.toIntOrNull()
                        if (parsed != null && parsed >= 1) {
                            tempInterval = parsed
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ).merge(FeniqoTabularNumberStyle),
                    modifier = Modifier.width(80.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FeniqoSageGreen,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                    ),
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Artı butonu
                Surface(
                    onClick = {
                        tempInterval += 1
                        intervalText = tempInterval.toString()
                    },
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = FeniqoTextPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dinamik açıklama metni ("Her 1 ayda bir", vb.)
            val summaryDescription = RecurringTransactionDisplayModelMapper.formatRecurrenceSummary(
                frequency = tempFrequency,
                interval = tempInterval,
            )
            Text(
                text = summaryDescription,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = FeniqoTextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Uygula Butonu
            Button(
                onClick = { onApply(tempFrequency, tempInterval) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FeniqoSageGreen,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Uygula",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

/**
 * Görsel 06: Kategori Seçimi Modal Bottom Sheet.
 * İlgili işlem türündeki (gelir/gider) kategorileri listeler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringCategoryPickerSheet(
    categories: List<Category>,
    selectedCategoryId: EntityId?,
    onCategorySelected: (EntityId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tempCategoryId by remember { mutableStateOf(selectedCategoryId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
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
                    text = "Kategori seç",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = FeniqoTextPrimary,
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (categories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Bu tür için tanımlı kategori bulunamadı.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FeniqoTextSecondary,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    categories.forEach { category ->
                        val isSelected = category.id == tempCategoryId
                        val catColor = ColorParser.parseHexColorOrNull(category.color.hex) ?: FeniqoSageGreen

                        Surface(
                            onClick = { tempCategoryId = category.id },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFF0FDF4) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryTonalIcon(
                                    iconKey = category.icon?.key,
                                    color = catColor,
                                    containerSize = 40.dp,
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = FeniqoTextPrimary,
                                    modifier = Modifier.weight(1f),
                                )

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { tempCategoryId = category.id },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = FeniqoSageGreen,
                                        unselectedColor = Color(0xFFCBD5E1),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    tempCategoryId?.let { onCategorySelected(it) }
                },
                enabled = tempCategoryId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FeniqoSageGreen,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Seçimi uygula",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

/**
 * Görsel 07: Ödeme Yöntemi Modal Bottom Sheet.
 * Nakit, Kredi kartı, Banka kartı, Banka transferi, Diğer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringPaymentMethodPickerSheet(
    selectedMethod: PaymentMethod,
    onMethodSelected: (PaymentMethod) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tempMethod by remember { mutableStateOf(selectedMethod) }

    val methods = listOf(
        Pair(PaymentMethod.CASH, Pair("Nakit", Icons.Outlined.AccountBalanceWallet)),
        Pair(PaymentMethod.CREDIT_CARD, Pair("Kredi kartı", Icons.Outlined.CreditCard)),
        Pair(PaymentMethod.DEBIT_CARD, Pair("Banka kartı", Icons.Outlined.Payment)),
        Pair(PaymentMethod.BANK_TRANSFER, Pair("Banka transferi", Icons.AutoMirrored.Outlined.CompareArrows)),
        Pair(PaymentMethod.OTHER, Pair("Diğer", Icons.Outlined.MoreHoriz)),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
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
                    text = "Ödeme yöntemi",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = FeniqoTextPrimary,
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                methods.forEach { (method, info) ->
                    val isSelected = method == tempMethod
                    val (label, icon) = info

                    Surface(
                        onClick = { tempMethod = method },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFFF0FDF4) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFF1F5F9), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) FeniqoSageGreen else Color(0xFF475569),
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = FeniqoTextPrimary,
                                modifier = Modifier.weight(1f),
                            )

                            RadioButton(
                                selected = isSelected,
                                onClick = { tempMethod = method },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = FeniqoSageGreen,
                                    unselectedColor = Color(0xFFCBD5E1),
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onMethodSelected(tempMethod) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FeniqoSageGreen,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Seçimi uygula",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

/**
 * Görsel 05: Tarih Seçimi Modal Bottom Sheet.
 * Başlangıç veya Bitiş tarihi takvimi. Bitiş için "Süresiz" seçeneği bulunur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringDatePickerSheet(
    title: String,
    initialDate: LocalDate?,
    minDate: LocalDate? = null,
    isEndDate: Boolean = false,
    onDateSelected: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    currentDateProvider: () -> LocalDate = { LocalDate(2026, 9, 15) },
) {
    val initialEffectiveDate = initialDate ?: minDate ?: currentDateProvider()
    val initialUtcMillis = initialEffectiveDate.toEpochDays() * 86_400_000L

    val selectableDates = remember(minDate) {
        if (minDate != null) {
            val minMillis = minDate.toEpochDays() * 86_400_000L
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= minMillis
            }
        } else {
            object : SelectableDates {}
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcMillis,
        selectableDates = selectableDates,
    )

    var isIndefiniteChecked by remember { mutableStateOf(isEndDate && initialDate == null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
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
            // Başlık ve Kapat Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = FeniqoTextPrimary,
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Seçili Tarih Kartı
            val selectedMillis = datePickerState.selectedDateMillis
            val selectedLocalDate = selectedMillis?.let {
                LocalDate.fromEpochDays((it / 86_400_000L).toInt())
            } ?: initialEffectiveDate

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = FeniqoSageGreen,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEndDate && isIndefiniteChecked) "Süresiz" else DateFormatter.formatReadableDate(selectedLocalDate),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = FeniqoTextPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Takvim
            if (!isIndefiniteChecked) {
                DatePicker(
                    state = datePickerState,
                    showModeToggle = false,
                    title = null,
                    headline = null,
                    colors = DatePickerDefaults.colors(
                        containerColor = Color.White,
                        selectedDayContainerColor = FeniqoSageGreen,
                        todayDateBorderColor = FeniqoSageGreen,
                        selectedDayContentColor = Color.White,
                    ),
                )
            }

            // Bitiş tarihi için "Süresiz" Checkbox
            if (isEndDate) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isIndefiniteChecked = !isIndefiniteChecked },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isIndefiniteChecked,
                        onCheckedChange = { isIndefiniteChecked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = FeniqoSageGreen,
                        ),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Süresiz",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = FeniqoTextPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tarihi seç Butonu
            Button(
                onClick = {
                    if (isEndDate && isIndefiniteChecked) {
                        onDateSelected(null)
                    } else {
                        onDateSelected(selectedLocalDate)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FeniqoSageGreen,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Tarihi seç",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            // Bitiş tarihi kaldırma ikincil butonu
            if (isEndDate && initialDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDateSelected(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                ) {
                    Text(
                        text = "Bitiş tarihini kaldır",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FeniqoTextSecondary,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Görsel 08 & 12: İşlem Sonrası Bildirim Çubuğu (Toast/Banner).
 * "✓ Kural duraklatıldı" veya "✓ Kural kaydedildi."
 */
@Composable
fun RecurringSuccessNotification(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFDCFCE7),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0xFF16A34A), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color(0xFF166534),
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Kapat",
                    tint = Color(0xFF166534),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}


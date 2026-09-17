package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.presentation.component.FeniqoGoalCardBorder
import com.feniqo.mobile.presentation.component.FeniqoGoalExpenseRed
import com.feniqo.mobile.presentation.component.FeniqoGoalSageGreen
import com.feniqo.mobile.presentation.component.FeniqoGoalSageGreenLight
import com.feniqo.mobile.presentation.goal.GoalContributionFormFieldError
import com.feniqo.mobile.presentation.goal.GoalContributionFormInput
import com.feniqo.mobile.presentation.goal.GoalContributionFormInputErrors
import com.feniqo.mobile.presentation.theme.*
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Hedef birikim hareketi ekleme / çıkarma formu.
 * 06 Para ekle, 07 Para çıkar ve 13 Para çıkar doğrulama panellerine tam uyumludur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalContributionFormScreen(
    parentGoal: Goal,
    input: GoalContributionFormInput,
    errors: GoalContributionFormInputErrors,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onDirectionChange: (GoalContributionDirection) -> Unit,
    onDateClick: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnabled = !isSubmitting
    val isAdd = input.direction == GoalContributionDirection.ADD
    val isRemove = input.direction == GoalContributionDirection.REMOVE
    val isExceedError = errors.amountError == GoalContributionFormFieldError.EXCEEDS_CURRENT_AMOUNT

    // Buton aktiflik durumu (Panel 13: limit aşıldığında veya geçersiz olduğunda buton disabled görünür)
    val isSubmitEnabled = isEnabled && !errors.hasErrors && input.amountInput.isNotBlank()

    // Hedef ilerleme oranı
    val progressRatio = if (parentGoal.targetAmount.amountMinor > 0) {
        (parentGoal.currentAmount.amountMinor.toFloat() / parentGoal.targetAmount.amountMinor.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val progressPercent = (progressRatio * 100).toInt()

    val formattedCurrent = MoneyFormatter.format(parentGoal.currentAmount)
    val formattedTarget = MoneyFormatter.format(parentGoal.targetAmount)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FeniqoWarmStoneBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Hedef hareketi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FeniqoTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = isEnabled) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = FeniqoTextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FeniqoWarmStoneBackground,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = FeniqoWarmStoneBackground,
                shadowElevation = 4.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                ) {
                    val buttonColor = if (isAdd) FeniqoGoalSageGreen else FeniqoGoalExpenseRed

                    Button(
                        onClick = onSubmit,
                        enabled = isSubmitEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFD1D5DB),
                            disabledContentColor = Color.White,
                        ),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = if (isAdd) "Birikime ekle" else "Para çıkar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // 1. Kompakt Üst Hedef Kartı (Paneller 06, 07, 13)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = FeniqoGoalSageGreenLight),
                border = BorderStroke(1.dp, Color(0xFFD5E3DC)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TrackChanges,
                            contentDescription = null,
                            tint = FeniqoGoalSageGreen,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = parentGoal.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FeniqoTextPrimary,
                        )

                        Spacer(Modifier.height(2.dp))

                        Text(
                            text = "$formattedCurrent / $formattedTarget",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FeniqoTextSecondary,
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { progressRatio },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = FeniqoGoalSageGreen,
                                trackColor = Color.White.copy(alpha = 0.7f),
                            )
                            Text(
                                text = "%$progressPercent",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = FeniqoTextSecondary,
                            )
                        }
                    }
                }
            }

            // 2. Yön Seçim Segmenti (Para ekle [Yeşil] / Para çıkar [Kırmızı])
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Para Ekle Butonu
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = isEnabled) {
                            onDirectionChange(GoalContributionDirection.ADD)
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAdd) FeniqoGoalSageGreen else Color.White,
                    border = if (isAdd) null else BorderStroke(1.dp, FeniqoGoalCardBorder),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Para ekle",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isAdd) Color.White else FeniqoTextSecondary,
                        )
                    }
                }

                // Para Çıkar Butonu
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = isEnabled) {
                            onDirectionChange(GoalContributionDirection.REMOVE)
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isRemove) FeniqoGoalExpenseRed else Color.White,
                    border = if (isRemove) null else BorderStroke(1.dp, FeniqoGoalCardBorder),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Para çıkar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRemove) Color.White else FeniqoTextSecondary,
                        )
                    }
                }
            }

            // 3. Tutar Girişi (Paneller 06, 07, 13)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tutar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FeniqoTextPrimary,
                )

                val isAmountError = errors.amountError != null
                val amountTextColor = if (isRemove) FeniqoGoalExpenseRed else FeniqoTextPrimary
                val borderColor = when {
                    isAmountError -> Color(0xFFEF4444)
                    isRemove -> Color(0xFFFCA5A5)
                    else -> FeniqoGoalCardBorder
                }

                OutlinedTextField(
                    value = input.amountInput,
                    onValueChange = onAmountChange,
                    placeholder = { Text("0,00") },
                    isError = isAmountError,
                    trailingIcon = {
                        if (isAmountError) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = "Hata",
                                tint = Color(0xFFEF4444),
                            )
                        }
                    },
                    singleLine = true,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = amountTextColor,
                        unfocusedTextColor = amountTextColor,
                        focusedBorderColor = if (isRemove && !isAmountError) FeniqoGoalExpenseRed else borderColor,
                        unfocusedBorderColor = borderColor,
                        errorBorderColor = Color(0xFFEF4444),
                    ),
                )

                // Alt Açıklama ve Hata Bildirimleri (Paneller 06, 07, 13)
                if (isExceedError) {
                    Text(
                        text = "◆ Çıkarılacak tutar mevcut birikimi aşamaz.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444),
                    )
                    Text(
                        text = "Maksimum çekilebilir: $formattedCurrent",
                        style = MaterialTheme.typography.bodySmall,
                        color = FeniqoTextSecondary,
                    )
                } else if (errors.amountError != null) {
                    Text(
                        text = when (errors.amountError) {
                            GoalContributionFormFieldError.AMOUNT_REQUIRED -> "Tutar zorunludur."
                            GoalContributionFormFieldError.AMOUNT_NON_POSITIVE -> "Tutar sıfırdan büyük olmalıdır."
                            GoalContributionFormFieldError.CURRENCY_MISMATCH -> "Para birimi uyuşmuyor."
                            else -> "Geçersiz tutar."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF4444),
                    )
                } else if (isRemove) {
                    Text(
                        text = "Maksimum çekilebilir: $formattedCurrent",
                        style = MaterialTheme.typography.bodySmall,
                        color = FeniqoTextSecondary,
                    )
                } else {
                    Text(
                        text = "${parentGoal.targetAmount.currency.code} • Hedefin para birimi",
                        style = MaterialTheme.typography.bodySmall,
                        color = FeniqoTextSecondary,
                    )
                }
            }

            // 4. Tarih Seçici Kartı
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tarih",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FeniqoTextPrimary,
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            role = Role.Button,
                            enabled = isEnabled,
                            onClick = onDateClick,
                        ),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (errors.dateError != null) MaterialTheme.colorScheme.error else FeniqoGoalCardBorder
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = FeniqoTextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = input.occurredOn?.let { DateFormatter.formatReadableDate(it) } ?: "Tarih seçin",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (input.occurredOn != null) FeniqoTextPrimary else FeniqoTextSecondary,
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Tarih seç",
                            tint = FeniqoTextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (errors.dateError != null) {
                    Text(
                        text = "İşlem tarihi seçilmelidir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            // 5. Not Alanı (Opsiyonel)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Not (isteğe bağlı)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FeniqoTextPrimary,
                )

                OutlinedTextField(
                    value = input.noteInput,
                    onValueChange = onNoteChange,
                    placeholder = { Text(if (isAdd) "Aylık birikim, Prim..." else "İhtiyaç için...") },
                    isError = errors.noteError != null,
                    supportingText = {
                        errors.noteError?.let {
                            Text(
                                text = "Not en fazla 500 karakter olabilir.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    singleLine = true,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = FeniqoGoalSageGreen,
                        unfocusedBorderColor = FeniqoGoalCardBorder,
                    ),
                )
            }

            // 6. Bilgilendirme Notu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = FeniqoTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Bu kayıt hedef birikiminizi günceller.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FeniqoTextSecondary,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.component.ActiveWorkspaceIndicator
import com.feniqo.mobile.presentation.component.CurrencyPickerSheet
import com.feniqo.mobile.presentation.component.FeniqoGoalCardBorder
import com.feniqo.mobile.presentation.component.FeniqoGoalSageGreen
import com.feniqo.mobile.presentation.component.FeniqoGoalSageGreenLight
import com.feniqo.mobile.presentation.goal.GOAL_PRESET_COLORS
import com.feniqo.mobile.presentation.goal.GoalContributionHistoryItemUiModel
import com.feniqo.mobile.presentation.goal.GoalFormFieldError
import com.feniqo.mobile.presentation.goal.GoalFormInput
import com.feniqo.mobile.presentation.goal.GoalFormInputErrors
import com.feniqo.mobile.presentation.theme.*
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Birikim hedefi oluşturma ve düzenleme ekranı.
 * 04 Yeni hedef ve 05 Hedefi düzenle panellerine tam uyumludur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalFormScreen(
    input: GoalFormInput,
    errors: GoalFormInputErrors,
    isSubmitting: Boolean,
    isEditMode: Boolean,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onTargetAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onInitialAmountChange: (String) -> Unit,
    onTargetDateClick: () -> Unit,
    onColorChange: (String) -> Unit,
    onRequestDelete: () -> Unit,
    onSubmit: () -> Unit,
    onAddContribution: (() -> Unit)? = null,
    contributionsHistory: List<GoalContributionHistoryItemUiModel> = emptyList(),
    currentAmount: Money? = null,
    activeWorkspaceName: String? = null,
    modifier: Modifier = Modifier,
) {
    val isEnabled = !isSubmitting
    var showCurrencySheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FeniqoWarmStoneBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Hedefi düzenle" else "Yeni hedef",
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
                actions = {
                    ActiveWorkspaceIndicator(
                        workspaceName = activeWorkspaceName,
                        isCompact = true,
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FeniqoGoalSageGreenLight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TrackChanges,
                            contentDescription = null,
                            tint = FeniqoGoalSageGreen,
                            modifier = Modifier.size(20.dp),
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
                    Button(
                        onClick = onSubmit,
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FeniqoGoalSageGreen,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFC7D0CD),
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
                                text = if (isEditMode) "Değişiklikleri kaydet" else "Hedef oluştur",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            // Başlık & Açıklama
            item("header-titles") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isEditMode) "Hedefini güncelle" else "Yeni bir hedef oluştur",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = FeniqoTextPrimary,
                    )
                    Text(
                        text = if (isEditMode) "Hedef detaylarını düzenleyebilirsin." else "Hayallerin için plan yap, adım adım ilerle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FeniqoTextSecondary,
                    )
                }
            }

            // 1. Hedef Adı Alanı
            item("name-field") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Hedef adı",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FeniqoTextPrimary,
                    )
                    OutlinedTextField(
                        value = input.nameInput,
                        onValueChange = onNameChange,
                        placeholder = { Text("Seyahat, Yeni bilgisayar...") },
                        isError = errors.nameError != null,
                        supportingText = {
                            errors.nameError?.let {
                                Text(
                                    text = when (it) {
                                        GoalFormFieldError.NAME_REQUIRED -> "Hedef adı zorunludur."
                                        GoalFormFieldError.NAME_TOO_LONG -> "Hedef adı en fazla 500 karakter olabilir."
                                        else -> "Geçersiz hedef adı."
                                    },
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        singleLine = true,
                        enabled = isEnabled,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
            }

            // 2. Hedef Tutarı ve Para Birimi (Yan yana)
            item("amount-and-currency") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Hedef Tutar
                    Column(
                        modifier = Modifier.weight(1.8f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Hedef tutar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FeniqoTextPrimary,
                        )
                        OutlinedTextField(
                            value = input.targetAmountInput,
                            onValueChange = onTargetAmountChange,
                            placeholder = { Text("₺30.000") },
                            isError = errors.targetAmountError != null,
                            supportingText = {
                                errors.targetAmountError?.let {
                                    Text(
                                        text = when (it) {
                                            GoalFormFieldError.TARGET_AMOUNT_REQUIRED -> "Hedef tutar zorunludur."
                                            GoalFormFieldError.TARGET_AMOUNT_NON_POSITIVE -> "Hedef tutar sıfırdan büyük olmalıdır."
                                            GoalFormFieldError.TARGET_AMOUNT_INVALID -> "Geçerli bir tutar girin."
                                            else -> "Geçersiz tutar."
                                        },
                                        color = MaterialTheme.colorScheme.error,
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
                                focusedBorderColor = FeniqoGoalSageGreen,
                                unfocusedBorderColor = FeniqoGoalCardBorder,
                            ),
                        )
                    }

                    // Para Birimi Seçimi (Edit modunda kilitli ve açıklamalı)
                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Para birimi",
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
                                    enabled = isEnabled && !isEditMode,
                                    role = Role.Button,
                                    onClick = { showCurrencySheet = true },
                                ),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isEditMode) Color(0xFFF1F5F9) else Color.White,
                            border = BorderStroke(1.dp, if (isEditMode) Color(0xFFE2E8F0) else FeniqoGoalCardBorder),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = input.currency.code,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isEditMode) FeniqoTextSecondary else FeniqoTextPrimary,
                                )
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Para birimi seç",
                                    tint = if (isEditMode) Color(0xFF94A3B8) else FeniqoTextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        if (isEditMode) {
                            Text(
                                text = "ⓘ Para birimi değiştirilemez.",
                                style = MaterialTheme.typography.labelSmall,
                                color = FeniqoTextSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            // 3. Başlangıç Birikimi (Yalnızca oluşturma modunda - Panel 04)
            if (!isEditMode) {
                item("initial-amount-field") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Başlangıç birikimi (isteğe bağlı)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FeniqoTextPrimary,
                        )
                        OutlinedTextField(
                            value = input.initialAmountInput,
                            onValueChange = onInitialAmountChange,
                            placeholder = { Text("₺5.000") },
                            isError = errors.initialAmountError != null,
                            supportingText = {
                                if (errors.initialAmountError != null) {
                                    Text(
                                        text = when (errors.initialAmountError) {
                                            GoalFormFieldError.INITIAL_AMOUNT_NEGATIVE -> "Başlangıç tutarı negatif olamaz."
                                            GoalFormFieldError.INITIAL_AMOUNT_INVALID -> "Geçerli bir tutar girin."
                                            else -> "Geçersiz başlangıç tutarı."
                                        },
                                        color = MaterialTheme.colorScheme.error,
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
                                focusedBorderColor = FeniqoGoalSageGreen,
                                unfocusedBorderColor = FeniqoGoalCardBorder,
                            ),
                        )
                    }
                }
            }

            // 4. Mevcut Birikim Kartı (Yalnızca düzenleme modunda - Panel 05)
            if (isEditMode) {
                item("current-savings-card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, FeniqoGoalCardBorder),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(FeniqoGoalSageGreenLight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Savings,
                                        contentDescription = null,
                                        tint = FeniqoGoalSageGreen,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Mevcut birikim",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = FeniqoTextSecondary,
                                    )
                                    Text(
                                        text = currentAmount?.let { MoneyFormatter.format(it) } ?: "₺0",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = FeniqoTextPrimary,
                                    )
                                }
                            }

                            if (onAddContribution != null) {
                                TextButton(
                                    onClick = onAddContribution,
                                    enabled = isEnabled,
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Text(
                                        text = "Hareket ekle >",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = FeniqoGoalSageGreen,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Hedef Tarihi Seçici Kartı
            item("target-date-card") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Hedef tarihi",
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
                                onClick = onTargetDateClick,
                            ),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (errors.targetDateError != null) MaterialTheme.colorScheme.error else FeniqoGoalCardBorder
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
                                    text = if (input.targetDate != null) {
                                        DateFormatter.formatReadableDate(input.targetDate)
                                    } else {
                                        "Tarih seçin"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (input.targetDate != null) FeniqoTextPrimary else FeniqoTextSecondary,
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

                    if (errors.targetDateError != null) {
                        Text(
                            text = "Hedef tarihi seçilmelidir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }

            // 6. Hedef Rengi Seçici
            item("color-picker") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Hedef rengi",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FeniqoTextPrimary,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GOAL_PRESET_COLORS.forEach { colorHex ->
                            val color = ColorParser.parseHexColorOrNull(colorHex) ?: Color.Gray
                            val isSelected = input.colorHex.equals(colorHex, ignoreCase = true)

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) FeniqoGoalSageGreen else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable(
                                        role = Role.Button,
                                        enabled = isEnabled,
                                        onClick = { onColorChange(colorHex) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Seçili renk",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. Hareket Geçmişi (Yalnızca düzenleme modunda)
            if (isEditMode && contributionsHistory.isNotEmpty()) {
                item("history-header") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Hareket Geçmişi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FeniqoTextPrimary,
                    )
                }

                items(contributionsHistory, key = { it.id.value }) { item ->
                    val isAdd = item.direction == GoalContributionDirection.ADD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, FeniqoGoalCardBorder),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isAdd) FeniqoGoalSageGreenLight else Color(0xFFFEE2E2)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isAdd) Icons.Outlined.Add else Icons.Outlined.ArrowUpward,
                                    contentDescription = null,
                                    tint = if (isAdd) FeniqoGoalSageGreen else Color(0xFFC0392B),
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.formattedDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FeniqoTextSecondary,
                                )
                                Text(
                                    text = item.note?.takeIf { it.isNotBlank() } ?: if (isAdd) "Birikim eklendi" else "Para çıkarıldı",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = FeniqoTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Text(
                                text = item.formattedAmount,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isAdd) FeniqoTrendGreen else Color(0xFFC0392B),
                            )
                        }
                    }
                }
            }
        }
    }

    // Para Birimi Seçim BottomSheet'i (Panel 09)
    if (showCurrencySheet) {
        CurrencyPickerSheet(
            selectedCurrency = input.currency,
            onCurrencySelected = onCurrencyChange,
            onDismiss = { showCurrencySheet = false },
        )
    }
}

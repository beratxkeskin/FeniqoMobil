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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
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
import com.feniqo.mobile.presentation.goal.toLocalizedReadableDate
import com.feniqo.mobile.presentation.goal.toLocalizedText
import com.feniqo.mobile.presentation.theme.*
import com.feniqo.mobile.presentation.util.ColorParser
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocusRequester = remember { FocusRequester() }
    val targetAmountFocusRequester = remember { FocusRequester() }
    val initialAmountFocusRequester = remember { FocusRequester() }
    val nameErrorText = errors.nameError?.toLocalizedText()
    val targetAmountErrorText = errors.targetAmountError?.toLocalizedText()
    val initialAmountErrorText = errors.initialAmountError?.toLocalizedText()
    val nameContentDesc = stringResource(Res.string.goal_form_name_desc)
    val targetAmountContentDesc = stringResource(Res.string.goal_form_target_amount_desc)
    val initialAmountContentDesc = stringResource(Res.string.goal_form_initial_amount_desc)
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(errors, isEditMode) {
        when {
            errors.nameError != null -> nameFocusRequester.requestFocus()
            errors.targetAmountError != null -> targetAmountFocusRequester.requestFocus()
            !isEditMode && errors.initialAmountError != null -> initialAmountFocusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) {
                            stringResource(Res.string.goal_form_edit_title)
                        } else {
                            stringResource(Res.string.goal_form_create_title)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = isEnabled) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.goal_top_bar_back_desc),
                            tint = MaterialTheme.colorScheme.onSurface,
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
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 4.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
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
                                text = if (isEditMode) {
                                    stringResource(Res.string.goal_form_save_changes)
                                } else {
                                    stringResource(Res.string.goal_form_save_create)
                                },
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
                        text = if (isEditMode) {
                            stringResource(Res.string.goal_form_edit_header_title)
                        } else {
                            stringResource(Res.string.goal_form_create_header_title)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (isEditMode) {
                            stringResource(Res.string.goal_form_edit_header_subtitle)
                        } else {
                            stringResource(Res.string.goal_form_create_header_subtitle)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 1. Hedef Adı Alanı
            item("name-field") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(Res.string.goal_form_name_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedTextField(
                        value = input.nameInput,
                        onValueChange = onNameChange,
                        placeholder = { Text(stringResource(Res.string.goal_form_name_placeholder)) },
                        isError = errors.nameError != null,
                        supportingText = {
                            nameErrorText?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine = true,
                        enabled = isEnabled,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { targetAmountFocusRequester.requestFocus() },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester)
                            .semantics {
                                contentDescription = nameContentDesc
                                nameErrorText?.let { error(it) }
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
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
                            text = stringResource(Res.string.goal_form_target_amount_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedTextField(
                            value = input.targetAmountInput,
                            onValueChange = onTargetAmountChange,
                            placeholder = { Text(stringResource(Res.string.goal_form_target_amount_placeholder)) },
                            isError = errors.targetAmountError != null,
                            supportingText = {
                                targetAmountErrorText?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                            },
                            singleLine = true,
                            enabled = isEnabled,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = if (isEditMode) ImeAction.Done else ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { initialAmountFocusRequester.requestFocus() },
                                onDone = { dismissKeyboard() },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(targetAmountFocusRequester)
                                .semantics {
                                    contentDescription = targetAmountContentDesc
                                    targetAmountErrorText?.let { error(it) }
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
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
                            text = stringResource(Res.string.goal_form_currency_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
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
                            color = if (isEditMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isEditMode) MaterialTheme.colorScheme.outlineVariant else FeniqoGoalCardBorder),
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
                                    color = if (isEditMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = stringResource(Res.string.goal_form_currency_select_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        if (isEditMode) {
                            Text(
                                text = stringResource(Res.string.goal_form_currency_locked_notice),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            text = stringResource(Res.string.goal_form_initial_amount_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedTextField(
                            value = input.initialAmountInput,
                            onValueChange = onInitialAmountChange,
                            placeholder = { Text(stringResource(Res.string.goal_form_initial_amount_placeholder)) },
                            isError = errors.initialAmountError != null,
                            supportingText = {
                                initialAmountErrorText?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                            },
                            singleLine = true,
                            enabled = isEnabled,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(initialAmountFocusRequester)
                                .semantics {
                                    contentDescription = initialAmountContentDesc
                                    initialAmountErrorText?.let { error(it) }
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                        text = stringResource(Res.string.goal_form_current_savings_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = currentAmount?.toLocalizedFormatted() ?: "₺0",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
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
                                        text = stringResource(Res.string.goal_form_add_movement_action),
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
                        text = stringResource(Res.string.goal_form_target_date_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.surface,
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = input.targetDate?.toLocalizedReadableDate() ?: stringResource(Res.string.goal_form_target_date_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (input.targetDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(Res.string.goal_form_target_date_select_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (errors.targetDateError != null) {
                        Text(
                            text = errors.targetDateError.toLocalizedText(),
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
                        text = stringResource(Res.string.goal_form_color_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
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
                                        contentDescription = stringResource(Res.string.goal_form_color_selected_desc),
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
                        text = stringResource(Res.string.goal_form_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                items(contributionsHistory, key = { it.id.value }) { item ->
                    val isAdd = item.direction == GoalContributionDirection.ADD
                    val prefix = if (isAdd) "+" else "-"
                    val localizedDate = item.occurredOn.toLocalizedReadableDate()
                    val localizedAmount = "$prefix${item.amount.toLocalizedFormatted()}"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                    text = localizedDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = item.note?.takeIf { it.isNotBlank() } ?: if (isAdd) {
                                        stringResource(Res.string.goal_detail_movement_added)
                                    } else {
                                        stringResource(Res.string.goal_detail_movement_removed)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Text(
                                text = localizedAmount,
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

package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.validation.MoneyAmountParser
import com.feniqo.mobile.presentation.common.currentLocaleDecimalSeparator
import com.feniqo.mobile.presentation.common.symbol
import com.feniqo.mobile.presentation.common.toLocalizedFormatted
import com.feniqo.mobile.presentation.common.toLocalizedNameText
import com.feniqo.mobile.presentation.common.toLocalizedReadableDate
import com.feniqo.mobile.presentation.component.CategorySemanticIconResolver
import com.feniqo.mobile.presentation.subscription.POPULAR_SUBSCRIPTION_TEMPLATES
import com.feniqo.mobile.presentation.subscription.PopularSubscriptionTemplate
import com.feniqo.mobile.presentation.subscription.SubscriptionFormFieldError
import com.feniqo.mobile.presentation.subscription.SubscriptionFormInput
import com.feniqo.mobile.presentation.subscription.SubscriptionFormInputErrors
import com.feniqo.mobile.presentation.subscription.SubscriptionMutationState
import com.feniqo.mobile.presentation.subscription.toCycleSummaryText
import com.feniqo.mobile.presentation.subscription.toLocalizedFrequencyLabel
import com.feniqo.mobile.presentation.subscription.toLocalizedMessage
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.subscription_badge_paused
import feniqomobil.sharedui.generated.resources.subscription_common_cancel
import feniqomobil.sharedui.generated.resources.subscription_decrease
import feniqomobil.sharedui.generated.resources.subscription_filter_active
import feniqomobil.sharedui.generated.resources.subscription_form_apply
import feniqomobil.sharedui.generated.resources.subscription_form_category_clear
import feniqomobil.sharedui.generated.resources.subscription_form_category_select
import feniqomobil.sharedui.generated.resources.subscription_form_category_sheet_title
import feniqomobil.sharedui.generated.resources.subscription_form_clear_end_date
import feniqomobil.sharedui.generated.resources.subscription_form_delete_button
import feniqomobil.sharedui.generated.resources.subscription_form_delete_confirm_dialog_message
import feniqomobil.sharedui.generated.resources.subscription_form_delete_confirm_dialog_title
import feniqomobil.sharedui.generated.resources.subscription_form_disclaimer
import feniqomobil.sharedui.generated.resources.subscription_form_estimated_yearly_cost
import feniqomobil.sharedui.generated.resources.subscription_form_field_amount
import feniqomobil.sharedui.generated.resources.subscription_form_field_category
import feniqomobil.sharedui.generated.resources.subscription_form_field_currency
import feniqomobil.sharedui.generated.resources.subscription_form_field_cycle
import feniqomobil.sharedui.generated.resources.subscription_form_field_end_date
import feniqomobil.sharedui.generated.resources.subscription_form_field_name
import feniqomobil.sharedui.generated.resources.subscription_form_field_start_date
import feniqomobil.sharedui.generated.resources.subscription_form_first_renewal
import feniqomobil.sharedui.generated.resources.subscription_form_frequency_sheet_subtitle
import feniqomobil.sharedui.generated.resources.subscription_form_info_settings_continue
import feniqomobil.sharedui.generated.resources.subscription_form_interval
import feniqomobil.sharedui.generated.resources.subscription_form_nav_back
import feniqomobil.sharedui.generated.resources.subscription_form_no_end_date
import feniqomobil.sharedui.generated.resources.subscription_form_notes_label
import feniqomobil.sharedui.generated.resources.subscription_form_notes_placeholder
import feniqomobil.sharedui.generated.resources.subscription_form_other_templates
import feniqomobil.sharedui.generated.resources.subscription_form_pause_button
import feniqomobil.sharedui.generated.resources.subscription_form_placeholder_name
import feniqomobil.sharedui.generated.resources.subscription_form_question
import feniqomobil.sharedui.generated.resources.subscription_form_quick_pick
import feniqomobil.sharedui.generated.resources.subscription_form_reminder
import feniqomobil.sharedui.generated.resources.subscription_form_reminder_dialog_dismiss
import feniqomobil.sharedui.generated.resources.subscription_form_reminder_dialog_grant
import feniqomobil.sharedui.generated.resources.subscription_form_reminder_dialog_message
import feniqomobil.sharedui.generated.resources.subscription_form_reminder_dialog_title
import feniqomobil.sharedui.generated.resources.subscription_form_reminder_subtitle
import feniqomobil.sharedui.generated.resources.subscription_form_renewal_tracking
import feniqomobil.sharedui.generated.resources.subscription_form_renewal_tracking_subtitle
import feniqomobil.sharedui.generated.resources.subscription_form_repeat
import feniqomobil.sharedui.generated.resources.subscription_form_resume_button
import feniqomobil.sharedui.generated.resources.subscription_form_save_button
import feniqomobil.sharedui.generated.resources.subscription_form_section_extra
import feniqomobil.sharedui.generated.resources.subscription_form_section_info
import feniqomobil.sharedui.generated.resources.subscription_form_section_settings
import feniqomobil.sharedui.generated.resources.subscription_form_select_date
import feniqomobil.sharedui.generated.resources.subscription_form_show_templates
import feniqomobil.sharedui.generated.resources.subscription_form_title_create
import feniqomobil.sharedui.generated.resources.subscription_form_title_edit
import feniqomobil.sharedui.generated.resources.subscription_form_update_button
import feniqomobil.sharedui.generated.resources.subscription_form_website_label
import feniqomobil.sharedui.generated.resources.subscription_form_website_placeholder
import feniqomobil.sharedui.generated.resources.subscription_increase
import feniqomobil.sharedui.generated.resources.subscription_workspace_personal
import org.jetbrains.compose.resources.stringResource

private val FeniqoBackgroundSand = Color(0xFFF7F5F0)
private val FeniqoSageGreen = Color(0xFF2D5A43)
private val FeniqoExpenseRed = Color(0xFFE53935)
private val FeniqoGraphite = Color(0xFF1E232A)

/**
 * Görsel 2 (04, 05, 06) ve Görsel 3 (07, 08, 09, 10, 13, 14) onaylı Abonelik Form Ekranı.
 * Oluşturma ve Düzenleme durumlarını kusursuz sıcak kırık beyaz zemin, beyaz kartlar,
 * grafit özet alanı ve Feniqo yeşili tasarım diliyle yönetir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionFormScreen(
    input: SubscriptionFormInput,
    categories: List<Category>,
    errors: SubscriptionFormInputErrors,
    mutationState: SubscriptionMutationState,
    isEditMode: Boolean,
    isActive: Boolean,
    onBack: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onRequestAdvanceRenewal: () -> Unit,
    onRequestDelete: () -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onCategoryChange: (EntityId?) -> Unit,
    onFrequencyChange: (RecurrenceFrequency) -> Unit,
    onIntervalChange: (String) -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onClearEndDate: () -> Unit,
    onAutoRenewChange: (Boolean) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onWebsiteUrlChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onTemplateSelect: (PopularSubscriptionTemplate) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnabled = !mutationState.isSubmitting
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocusRequester = remember { FocusRequester() }
    val amountFocusRequester = remember { FocusRequester() }
    val websiteFocusRequester = remember { FocusRequester() }
    val notesFocusRequester = remember { FocusRequester() }
    val nameErrorText = errors.nameError?.toLocalizedMessage()
    val amountErrorText = errors.amountError?.toLocalizedMessage()
    val websiteErrorText = errors.websiteUrlError?.toLocalizedMessage()
    val notesErrorText = errors.notesError?.toLocalizedMessage()
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(errors) {
        when {
            errors.nameError != null -> nameFocusRequester.requestFocus()
            errors.amountError != null -> amountFocusRequester.requestFocus()
            errors.websiteUrlError != null -> websiteFocusRequester.requestFocus()
            errors.notesError != null -> notesFocusRequester.requestFocus()
        }
    }

    // Modal Sheet Durumları
    var showFrequencySheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showCurrencyAndTemplatesSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showReminderPermissionDialog by remember { mutableStateOf(false) }

    val selectedCategory = remember(categories, input.categoryId) {
        categories.firstOrNull { it.id == input.categoryId }
    }

    // Grafit kart için yıllık maliyet ve sonraki yenileme hesaplama
    val parseResult = remember(input.amountInput, input.currency) {
        MoneyAmountParser.parseToMinorUnits(input.amountInput, input.currency)
    }

    val yearlyCostMoney = remember(parseResult, input.frequency, input.intervalInput, input.currency) {
        if (parseResult is MoneyAmountParser.ParseResult.Success && parseResult.amountMinor > 0) {
            val interval = input.intervalInput.trim().toIntOrNull() ?: 1
            val yearlyMinor = when (input.frequency) {
                RecurrenceFrequency.MONTHLY -> (parseResult.amountMinor * 12) / interval.coerceAtLeast(1)
                RecurrenceFrequency.YEARLY -> parseResult.amountMinor / interval.coerceAtLeast(1)
                RecurrenceFrequency.WEEKLY -> (parseResult.amountMinor * 52) / interval.coerceAtLeast(1)
                RecurrenceFrequency.DAILY -> (parseResult.amountMinor * 365) / interval.coerceAtLeast(1)
            }
            Money(yearlyMinor, input.currency)
        } else {
            null
        }
    }
    val yearlyCostFormatted = yearlyCostMoney?.toLocalizedFormatted() ?: "—"

    val firstRenewalDate = remember(input.startDate, input.nextRenewalDate) {
        input.nextRenewalDate ?: input.startDate
    }
    val firstRenewalFormatted = firstRenewalDate?.toLocalizedReadableDate() ?: "—"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        // 1. Üst Bar: Geri Dönüş, Başlık ve (Düzenlemede) Durum Rozeti
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack, enabled = isEnabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(Res.string.subscription_form_nav_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = if (isEditMode) {
                        stringResource(Res.string.subscription_form_title_edit)
                    } else {
                        stringResource(Res.string.subscription_form_title_create)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (isEditMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                ) {
                    Text(
                        text = if (isActive) {
                            stringResource(Res.string.subscription_filter_active)
                        } else {
                            stringResource(Res.string.subscription_badge_paused)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) FeniqoSageGreen else Color(0xFFD97706),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // 2. Doğal Dikey Kaydırılabilir Form Gövdesi (04 ve 05 Tek Akışta)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Görsel 04: Çalışma Alanı Hapı (Yalnızca oluşturmada)
            if (!isEditMode) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(FeniqoSageGreen, CircleShape),
                        )
                        Text(
                            text = stringResource(Res.string.subscription_workspace_personal),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // "Hangi servisi eklemek istiyorsunuz?"
                Text(
                    text = stringResource(Res.string.subscription_form_question),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Hızlı Seçim Şablonları (3'lü Kartlar)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.subscription_form_quick_pick),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val top3Templates = POPULAR_SUBSCRIPTION_TEMPLATES.take(3)
                        top3Templates.forEach { template ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(84.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = isEnabled) { onTemplateSelect(template) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    // Marka İkonu
                                    TemplateBrandIcon(name = template.name, size = 32.dp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = template.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // "Diğer şablonlar - Hazır abonelik şablonlarını göster >"
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = isEnabled) { showCurrencyAndTemplatesSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.GridView,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column {
                                    Text(
                                        text = stringResource(Res.string.subscription_form_other_templates),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(Res.string.subscription_form_show_templates),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // "Abonelik bilgileri" Bölümü
            Text(
                text = stringResource(Res.string.subscription_form_section_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 1. Servis / Abonelik Adı Alanı
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val nameFieldLabel = stringResource(Res.string.subscription_form_field_name)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = nameFieldLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(text = " *", color = FeniqoExpenseRed, fontWeight = FontWeight.Bold)
                }

                val hasNameError = errors.nameError != null
                OutlinedTextField(
                    value = input.nameInput,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester)
                        .semantics {
                            contentDescription = nameFieldLabel
                            nameErrorText?.let { error(it) }
                        },
                    enabled = isEnabled,
                    placeholder = { Text(stringResource(Res.string.subscription_form_placeholder_name), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    isError = hasNameError,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        errorContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = FeniqoSageGreen,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        errorBorderColor = FeniqoExpenseRed,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { amountFocusRequester.requestFocus() }),
                )

                if (hasNameError && nameErrorText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = FeniqoExpenseRed,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = nameErrorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = FeniqoExpenseRed,
                        )
                    }
                }
            }

            // 2. Kategori (İsteğe Bağlı) Seçici Kart
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.subscription_form_field_category),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = isEnabled) { showCategorySheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val icon = CategorySemanticIconResolver.resolve(selectedCategory?.icon?.key)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = FeniqoSageGreen,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            Text(
                                text = selectedCategory?.name ?: stringResource(Res.string.subscription_form_category_select),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedCategory != null) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedCategory != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = stringResource(Res.string.subscription_form_category_select),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // 3. Para Birimi Seçici Kart
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.subscription_form_field_currency),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                val currencySymbol = input.currency.symbol()
                val currencyFullName = "${input.currency.name} · ${input.currency.toLocalizedNameText()}"

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = isEnabled) { showCurrencyAndTemplatesSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = currencySymbol,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = currencyFullName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // 4. Dönemsel Tutar Alanı (Kırmızı ve Büyük)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val amountFieldLabel = stringResource(Res.string.subscription_form_field_amount)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = amountFieldLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(text = " *", color = FeniqoExpenseRed, fontWeight = FontWeight.Bold)
                }

                val hasAmountError = errors.amountError != null
                val amountPlaceholder = if (currentLocaleDecimalSeparator() == ',') "0,00" else "0.00"
                OutlinedTextField(
                    value = input.amountInput,
                    onValueChange = onAmountChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(amountFocusRequester)
                        .semantics {
                            contentDescription = amountFieldLabel
                            amountErrorText?.let { error(it) }
                        },
                    enabled = isEnabled,
                    placeholder = { Text(amountPlaceholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    isError = hasAmountError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { websiteFocusRequester.requestFocus() }),
                    shape = RoundedCornerShape(12.dp),
                    prefix = {
                        val symbol = input.currency.symbol()
                        Text(
                            text = "$symbol ",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = FeniqoExpenseRed,
                        )
                    },
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FeniqoExpenseRed,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        errorContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = FeniqoSageGreen,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        errorBorderColor = FeniqoExpenseRed,
                    ),
                )

                if (hasAmountError && amountErrorText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = FeniqoExpenseRed,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = amountErrorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = FeniqoExpenseRed,
                        )
                    }
                }
            }

            // 5. Fatura Dönemi Seçici Kart
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.subscription_form_field_cycle),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                val interval = input.intervalInput.trim().toIntOrNull() ?: 1
                val cycleText = toCycleSummaryText(input.frequency, interval)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = isEnabled) { showFrequencySheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = cycleText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // 6. Başlangıç Tarihi ve Bitiş Tarihi (2 Sütun)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Başlangıç Tarihi
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.subscription_form_field_start_date),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = isEnabled) { onStartDateClick() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = input.startDate?.toLocalizedReadableDate() ?: stringResource(Res.string.subscription_form_select_date),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Bitiş Tarihi
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.subscription_form_field_end_date),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = isEnabled) { onEndDateClick() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Repeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = input.endDate?.toLocalizedReadableDate() ?: stringResource(Res.string.subscription_form_no_end_date),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (input.endDate != null) {
                                IconButton(
                                    onClick = onClearEndDate,
                                    modifier = Modifier.size(18.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(Res.string.subscription_form_clear_end_date),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Görsel 04 altındaki bilgilendirme kutusu (Oluşturmada)
            if (!isEditMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(Res.string.subscription_form_info_settings_continue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Görsel 05: "Abonelik ayarları"
            Text(
                text = stringResource(Res.string.subscription_form_section_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    // Yenileme Takibi Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Repeat,
                                    contentDescription = null,
                                    tint = FeniqoSageGreen,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(Res.string.subscription_form_renewal_tracking),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(Res.string.subscription_form_renewal_tracking_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Switch(
                            checked = input.autoRenew,
                            onCheckedChange = onAutoRenewChange,
                            enabled = isEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = FeniqoSageGreen,
                            ),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )

                    // Hatırlatıcı Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint = FeniqoSageGreen,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(Res.string.subscription_form_reminder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(Res.string.subscription_form_reminder_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Switch(
                            checked = input.reminderEnabled,
                            onCheckedChange = { checked ->
                                onReminderEnabledChange(checked)
                                if (checked) {
                                    showReminderPermissionDialog = true
                                }
                            },
                            enabled = isEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = FeniqoSageGreen,
                            ),
                        )
                    }
                }
            }

            // Görsel 05: "Ek bilgiler (isteğe bağlı)"
            Text(
                text = stringResource(Res.string.subscription_form_section_extra),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Web sitesi
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val websiteFieldLabel = stringResource(Res.string.subscription_form_website_label)
                        Text(
                            text = websiteFieldLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = input.websiteUrlInput,
                            onValueChange = onWebsiteUrlChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(websiteFocusRequester)
                                .semantics {
                                    contentDescription = websiteFieldLabel
                                    websiteErrorText?.let { error(it) }
                                },
                            enabled = isEnabled,
                            placeholder = { Text(stringResource(Res.string.subscription_form_website_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { notesFocusRequester.requestFocus() }),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = FeniqoSageGreen,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                    }

                    // Notlar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val notesFieldLabel = stringResource(Res.string.subscription_form_notes_label)
                        Text(
                            text = notesFieldLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = input.notesInput,
                            onValueChange = onNotesChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(notesFocusRequester)
                                .semantics {
                                    contentDescription = notesFieldLabel
                                    notesErrorText?.let { error(it) }
                                },
                            enabled = isEnabled,
                            placeholder = { Text(stringResource(Res.string.subscription_form_notes_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = FeniqoSageGreen,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                    }
                }
            }

            // Görsel 05: Grafit Tahmini Özet Kartı
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = FeniqoGraphite),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = stringResource(Res.string.subscription_form_estimated_yearly_cost),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB0BEC5),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = yearlyCostFormatted,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(Res.string.subscription_form_first_renewal),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB0BEC5),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = firstRenewalFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = Color(0xFFCFD8DC),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(Res.string.subscription_form_disclaimer),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color(0xFFCFD8DC),
                            )
                        }
                    }
                }
            }

            // Aksiyon Butonları (05 Ekle veya 06 Düzenle)
            if (!isEditMode) {
                // Görsel 05: "Aboneliği kaydet" & "Vazgeç"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSubmit,
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FeniqoSageGreen,
                            contentColor = Color.White,
                        ),
                    ) {
                        if (mutationState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(Res.string.subscription_form_save_button),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = onBack,
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            text = stringResource(Res.string.subscription_common_cancel),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                // Görsel 06 Düzenle: "Değişiklikleri kaydet", "Takibi duraklat", "Kaydı sil"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSubmit,
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FeniqoSageGreen,
                            contentColor = Color.White,
                        ),
                    ) {
                        if (mutationState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(Res.string.subscription_form_update_button),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = { onSetActive(!isActive) },
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isActive) {
                                stringResource(Res.string.subscription_form_pause_button)
                            } else {
                                stringResource(Res.string.subscription_form_resume_button)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = FeniqoExpenseRed,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = FeniqoExpenseRed,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.subscription_form_delete_button),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ==========================================
    // 07 FATURA DÖNEMİ MODAL BOTTOM SHEET
    // ==========================================
    if (showFrequencySheet) {
        var tempFrequency by remember { mutableStateOf(input.frequency) }
        var tempIntervalInt by remember {
            mutableStateOf(input.intervalInput.trim().toIntOrNull()?.coerceIn(1, 99) ?: 1)
        }

        ModalBottomSheet(
            onDismissRequest = { showFrequencySheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Başlık
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showFrequencySheet = false }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(Res.string.subscription_form_nav_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(Res.string.subscription_form_field_cycle),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(Res.string.subscription_form_frequency_sheet_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 4 Sıklık Çipi (Günlük, Haftalık, Aylık, Yıllık)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FrequencyChipItem(
                        title = RecurrenceFrequency.DAILY.toLocalizedFrequencyLabel(),
                        icon = Icons.Outlined.WbSunny,
                        isSelected = tempFrequency == RecurrenceFrequency.DAILY,
                        onClick = { tempFrequency = RecurrenceFrequency.DAILY },
                        modifier = Modifier.weight(1f),
                    )
                    FrequencyChipItem(
                        title = RecurrenceFrequency.WEEKLY.toLocalizedFrequencyLabel(),
                        icon = Icons.Outlined.CalendarMonth,
                        isSelected = tempFrequency == RecurrenceFrequency.WEEKLY,
                        onClick = { tempFrequency = RecurrenceFrequency.WEEKLY },
                        modifier = Modifier.weight(1f),
                    )
                    FrequencyChipItem(
                        title = RecurrenceFrequency.MONTHLY.toLocalizedFrequencyLabel(),
                        icon = Icons.Outlined.CalendarMonth,
                        isSelected = tempFrequency == RecurrenceFrequency.MONTHLY,
                        onClick = { tempFrequency = RecurrenceFrequency.MONTHLY },
                        modifier = Modifier.weight(1f),
                    )
                    FrequencyChipItem(
                        title = RecurrenceFrequency.YEARLY.toLocalizedFrequencyLabel(),
                        icon = Icons.Outlined.CalendarMonth,
                        isSelected = tempFrequency == RecurrenceFrequency.YEARLY,
                        onClick = { tempFrequency = RecurrenceFrequency.YEARLY },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Tekrar aralığı Sayaç: [-] [1] [+] (Pozitif tam sayı!)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(Res.string.subscription_form_interval),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(
                                onClick = { if (tempIntervalInt > 1) tempIntervalInt-- },
                                enabled = tempIntervalInt > 1,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Remove,
                                    contentDescription = stringResource(Res.string.subscription_decrease),
                                    tint = if (tempIntervalInt > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Text(
                                text = tempIntervalInt.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            IconButton(
                                onClick = { if (tempIntervalInt < 99) tempIntervalInt++ },
                                enabled = tempIntervalInt < 99,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = stringResource(Res.string.subscription_increase),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // Tekrar Açıklaması
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(Res.string.subscription_form_repeat),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val repeatExplanation = toCycleSummaryText(tempFrequency, tempIntervalInt)

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = repeatExplanation,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Uygula Butonu
                Button(
                    onClick = {
                        onFrequencyChange(tempFrequency)
                        onIntervalChange(tempIntervalInt.toString())
                        showFrequencySheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FeniqoSageGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(Res.string.subscription_form_apply),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ==========================================
    // 08 KATEGORİ SEÇ MODAL BOTTOM SHEET
    // ==========================================
    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.subscription_form_category_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { category ->
                        val isSelected = category.id == input.categoryId
                        val icon = CategorySemanticIconResolver.resolve(category.icon?.key)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onCategoryChange(category.id)
                                    showCategorySheet = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (isSelected) Color(0xFFC8E6C9) else Color(0xFFF5F5F5),
                                                CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) FeniqoSageGreen else Color(0xFF616161),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }

                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onCategoryChange(category.id)
                                        showCategorySheet = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = FeniqoSageGreen,
                                        unselectedColor = Color(0xFFBDBDBD),
                                    ),
                                )
                            }
                        }
                    }

                    // Kategori seçimini kaldır butonu
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onCategoryChange(null)
                                showCategorySheet = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(Res.string.subscription_form_category_clear),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF616161),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ==========================================
    // 09 PARA BİRİMİ VE ŞABLONLAR MODAL BOTTOM SHEET
    // ==========================================
    if (showCurrencyAndTemplatesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCurrencyAndTemplatesSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Para Birimi Seçimi
                Text(
                    text = stringResource(Res.string.subscription_form_field_currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CurrencyChip(
                        currency = Currency.TRY,
                        symbol = "₺",
                        name = "TRY",
                        isSelected = input.currency == Currency.TRY,
                        onClick = { onCurrencyChange(Currency.TRY) },
                        modifier = Modifier.weight(1f),
                    )
                    CurrencyChip(
                        currency = Currency.USD,
                        symbol = "$",
                        name = "USD",
                        isSelected = input.currency == Currency.USD,
                        onClick = { onCurrencyChange(Currency.USD) },
                        modifier = Modifier.weight(1f),
                    )
                    CurrencyChip(
                        currency = Currency.EUR,
                        symbol = "€",
                        name = "EUR",
                        isSelected = input.currency == Currency.EUR,
                        onClick = { onCurrencyChange(Currency.EUR) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Hızlı Seçim Şablonları (8 Popüler Abonelik)
                Text(
                    text = stringResource(Res.string.subscription_form_quick_pick),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    POPULAR_SUBSCRIPTION_TEMPLATES.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            pair.forEach { template ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onTemplateSelect(template)
                                            showCurrencyAndTemplatesSheet = false
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        TemplateBrandIcon(name = template.name, size = 28.dp)
                                        Text(
                                            text = template.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ==========================================
    // 10 HATIRLATMA İZNİ DİYALOĞU
    // ==========================================
    if (showReminderPermissionDialog) {
        BasicAlertDialog(
            onDismissRequest = { showReminderPermissionDialog = false },
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = FeniqoSageGreen,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.subscription_form_reminder_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(Res.string.subscription_form_reminder_dialog_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { showReminderPermissionDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FeniqoSageGreen,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                text = stringResource(Res.string.subscription_form_reminder_dialog_grant),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Button(
                            onClick = { showReminderPermissionDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Text(
                                text = stringResource(Res.string.subscription_form_reminder_dialog_dismiss),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 13 SİLME ONAYI DİYALOĞU
    // ==========================================
    if (showDeleteConfirmDialog) {
        BasicAlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = FeniqoExpenseRed,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.subscription_form_delete_confirm_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(Res.string.subscription_form_delete_confirm_dialog_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                showDeleteConfirmDialog = false
                                onRequestDelete()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FeniqoExpenseRed,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                text = stringResource(Res.string.subscription_form_delete_button),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Button(
                            onClick = { showDeleteConfirmDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Text(
                                text = stringResource(Res.string.subscription_common_cancel),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequencyChipItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CurrencyChip(
    currency: Currency,
    symbol: String,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) FeniqoSageGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Şablon Marka İkonu (Mevcut ikonlar ve renk paletinden temiz çözücü).
 */
@Composable
private fun TemplateBrandIcon(name: String, size: androidx.compose.ui.unit.Dp) {
    val (bgColor, iconTint, letter, vectorIcon) = when (name.lowercase()) {
        "netflix" -> Quadruple(Color(0xFFE50914), Color.White, "N", null)
        "spotify" -> Quadruple(Color(0xFF1DB954), Color.White, "S", null)
        "youtube premium" -> Quadruple(Color(0xFFFF0000), Color.White, null, Icons.Outlined.PlayArrow)
        "icloud+" -> Quadruple(Color(0xFF3B99FC), Color.White, null, Icons.Outlined.Cloud)
        "notion" -> Quadruple(Color(0xFF000000), Color.White, "N", null)
        "amazon prime" -> Quadruple(Color(0xFF00A8E1), Color.White, "a", null)
        "google one" -> Quadruple(Color(0xFF4285F4), Color.White, "G", null)
        "disney+" -> Quadruple(Color(0xFF113CCF), Color.White, "D", null)
        else -> Quadruple(Color(0xFF424242), Color.White, name.take(1).uppercase(), null)
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(bgColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (vectorIcon != null) {
            Icon(
                imageVector = vectorIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(size * 0.55f),
            )
        } else if (letter != null) {
            Text(
                text = letter,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = iconTint,
                fontSize = (size.value * 0.5f).sp,
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.feniqo.mobile.presentation.component.CategorySemanticIconResolver
import com.feniqo.mobile.presentation.subscription.POPULAR_SUBSCRIPTION_TEMPLATES
import com.feniqo.mobile.presentation.subscription.PopularSubscriptionTemplate
import com.feniqo.mobile.presentation.subscription.SubscriptionFormFieldError
import com.feniqo.mobile.presentation.subscription.SubscriptionFormInput
import com.feniqo.mobile.presentation.subscription.SubscriptionFormInputErrors
import com.feniqo.mobile.presentation.subscription.SubscriptionMutationState
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

private val FeniqoBackgroundSand = Color(0xFFF7F5F0)
private val FeniqoSageGreen = Color(0xFF2D5A43)
private val FeniqoExpenseRed = Color(0xFFE53935)
private val FeniqoGraphite = Color(0xFF1E232A)
private val FeniqoCardBg = Color.White
private val FeniqoBorderColor = Color(0xFFEBEBEB)

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

    val yearlyCostFormatted = remember(parseResult, input.frequency, input.intervalInput, input.currency) {
        if (parseResult is MoneyAmountParser.ParseResult.Success && parseResult.amountMinor > 0) {
            val interval = input.intervalInput.trim().toIntOrNull() ?: 1
            val yearlyMinor = when (input.frequency) {
                RecurrenceFrequency.MONTHLY -> (parseResult.amountMinor * 12) / interval.coerceAtLeast(1)
                RecurrenceFrequency.YEARLY -> parseResult.amountMinor / interval.coerceAtLeast(1)
                RecurrenceFrequency.WEEKLY -> (parseResult.amountMinor * 52) / interval.coerceAtLeast(1)
                RecurrenceFrequency.DAILY -> (parseResult.amountMinor * 365) / interval.coerceAtLeast(1)
            }
            MoneyFormatter.format(Money(yearlyMinor, input.currency))
        } else {
            "—"
        }
    }

    val firstRenewalFormatted = remember(input.startDate, input.nextRenewalDate) {
        val date = input.nextRenewalDate ?: input.startDate
        date?.let { DateFormatter.formatReadableDate(it) } ?: "—"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FeniqoBackgroundSand),
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
                        contentDescription = "Geri dön",
                        tint = Color(0xFF1E232A),
                    )
                }
                Text(
                    text = if (isEditMode) "Aboneliği düzenle" else "Abonelik ekle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E232A),
                )
            }

            if (isEditMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                ) {
                    Text(
                        text = if (isActive) "Aktif" else "Duraklatıldı",
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
                    color = Color.White,
                    border = BorderStroke(1.dp, FeniqoBorderColor),
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
                            text = "Kişisel",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF424242),
                        )
                    }
                }

                // "Hangi servisi eklemek istiyorsunuz?"
                Text(
                    text = "Hangi servisi eklemek\nistiyorsunuz?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E232A),
                )

                // Hızlı Seçim Şablonları (3'lü Kartlar)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Hızlı seçim",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E232A),
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
                                color = Color.White,
                                border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                        color = Color(0xFF212121),
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
                        color = Color.White,
                        border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                    tint = Color(0xFF424242),
                                    modifier = Modifier.size(20.dp),
                                )
                                Column {
                                    Text(
                                        text = "Diğer şablonlar",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF212121),
                                    )
                                    Text(
                                        text = "Hazır abonelik şablonlarını göster",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF757575),
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF9E9E9E),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // "Abonelik bilgileri" Bölümü
            Text(
                text = "Abonelik bilgileri",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E232A),
            )

            // 1. Servis / Abonelik Adı Alanı
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Servis / abonelik adı",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF424242),
                    )
                    Text(text = " *", color = FeniqoExpenseRed, fontWeight = FontWeight.Bold)
                }

                val hasNameError = errors.nameError != null
                OutlinedTextField(
                    value = input.nameInput,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isEnabled,
                    placeholder = { Text("İnternet", color = Color(0xFF9E9E9E)) },
                    singleLine = true,
                    isError = hasNameError,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        errorContainerColor = Color.White,
                        focusedBorderColor = FeniqoSageGreen,
                        unfocusedBorderColor = FeniqoBorderColor,
                        errorBorderColor = FeniqoExpenseRed,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )

                if (hasNameError) {
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
                            text = "Bu alan zorunludur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FeniqoExpenseRed,
                        )
                    }
                }
            }

            // 2. Kategori (İsteğe Bağlı) Seçici Kart
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Kategori (isteğe bağlı)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF424242),
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = isEnabled) { showCategorySheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                    .background(Color(0xFFE8F5E9), CircleShape),
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
                                text = selectedCategory?.name ?: "Kategori seçin",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedCategory != null) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedCategory != null) Color(0xFF212121) else Color(0xFF9E9E9E),
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Kategori seç",
                            tint = Color(0xFF757575),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // 3. Para Birimi Seçici Kart
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Para birimi",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF424242),
                )

                val currencySymbol = when (input.currency) {
                    Currency.TRY -> "₺"
                    Currency.USD -> "$"
                    Currency.EUR -> "€"
                }

                val currencyFullName = when (input.currency) {
                    Currency.TRY -> "TRY · Türk lirası"
                    Currency.USD -> "USD · Amerikan doları"
                    Currency.EUR -> "EUR · Euro"
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = isEnabled) { showCurrencyAndTemplatesSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                color = Color(0xFF424242),
                            )
                            Text(
                                text = currencyFullName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF212121),
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // 4. Dönemsel Tutar Alanı (Kırmızı ve Büyük)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Dönemsel tutar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF424242),
                    )
                    Text(text = " *", color = FeniqoExpenseRed, fontWeight = FontWeight.Bold)
                }

                val hasAmountError = errors.amountError != null
                OutlinedTextField(
                    value = input.amountInput,
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isEnabled,
                    placeholder = { Text("0,00", color = Color(0xFF9E9E9E)) },
                    singleLine = true,
                    isError = hasAmountError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    prefix = {
                        val symbol = when (input.currency) {
                            Currency.TRY -> "₺"
                            Currency.USD -> "$"
                            Currency.EUR -> "€"
                        }
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
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        errorContainerColor = Color.White,
                        focusedBorderColor = FeniqoSageGreen,
                        unfocusedBorderColor = FeniqoBorderColor,
                        errorBorderColor = FeniqoExpenseRed,
                    ),
                )

                if (hasAmountError) {
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
                            text = "Sıfırdan büyük bir tutar gir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FeniqoExpenseRed,
                        )
                    }
                }
            }

            // 5. Fatura Dönemi Seçici Kart
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Fatura dönemi",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF424242),
                )

                val interval = input.intervalInput.trim().toIntOrNull() ?: 1
                val cycleText = when (input.frequency) {
                    RecurrenceFrequency.MONTHLY -> if (interval == 1) "Her ay" else "Her $interval ayda bir"
                    RecurrenceFrequency.YEARLY -> if (interval == 1) "Her yıl" else "Her $interval yılda bir"
                    RecurrenceFrequency.WEEKLY -> if (interval == 1) "Her hafta" else "Her $interval haftada bir"
                    RecurrenceFrequency.DAILY -> if (interval == 1) "Her gün" else "Her $interval günde bir"
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = isEnabled) { showFrequencySheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                tint = Color(0xFF424242),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = cycleText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF212121),
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF9E9E9E),
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
                        text = "Başlangıç tarihi",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF424242),
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = isEnabled) { onStartDateClick() },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                tint = Color(0xFF424242),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = input.startDate?.let { DateFormatter.formatReadableDate(it) } ?: "Tarih seç",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF212121),
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
                        text = "Bitiş tarihi",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF424242),
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = isEnabled) { onEndDateClick() },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                    tint = Color(0xFF424242),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = input.endDate?.let { DateFormatter.formatReadableDate(it) } ?: "Süresiz",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF212121),
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
                                        contentDescription = "Temizle",
                                        tint = Color(0xFF9E9E9E),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFF9E9E9E),
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
                    color = Color.White,
                    border = BorderStroke(1.dp, FeniqoBorderColor),
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
                            tint = Color(0xFF757575),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Ayarlar ve ek bilgiler aşağıda devam ediyor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF757575),
                        )
                    }
                }
            }

            // Görsel 05: "Abonelik ayarları"
            Text(
                text = "Abonelik ayarları",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E232A),
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                    .background(Color(0xFFE8F5E9), CircleShape),
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
                                    text = "Yenileme takibi",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF212121),
                                )
                                Text(
                                    text = "Feniqo içinde takip edilir",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF757575),
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
                            .background(Color(0xFFF0F0F0)),
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
                                    .background(Color(0xFFE8F5E9), CircleShape),
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
                                    text = "Hatırlatıcı",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF212121),
                                )
                                Text(
                                    text = "7 gün önce ve yenileme günü",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF757575),
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
                text = "Ek bilgiler (isteğe bağlı)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E232A),
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, FeniqoBorderColor),
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
                        Text(
                            text = "Web sitesi",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF757575),
                        )
                        OutlinedTextField(
                            value = input.websiteUrlInput,
                            onValueChange = onWebsiteUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isEnabled,
                            placeholder = { Text("https://...", color = Color(0xFFBDBDBD)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = null,
                                    tint = Color(0xFF757575),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = FeniqoSageGreen,
                                unfocusedBorderColor = FeniqoBorderColor,
                            ),
                        )
                    }

                    // Notlar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Notlar",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF757575),
                        )
                        OutlinedTextField(
                            value = input.notesInput,
                            onValueChange = onNotesChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isEnabled,
                            placeholder = { Text("Ev interneti", color = Color(0xFFBDBDBD)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = Color(0xFF757575),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = FeniqoSageGreen,
                                unfocusedBorderColor = FeniqoBorderColor,
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
                                text = "Tahmini yıllık maliyet",
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
                                text = "İlk yenileme",
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
                                text = "Banka hesabından para çekilmez. Sadece Feniqo içinde takip edilir.",
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
                            text = "Aboneliği kaydet",
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
                            containerColor = Color(0xFFEEEEEE),
                            contentColor = Color(0xFF424242),
                        ),
                    ) {
                        Text(
                            text = "Vazgeç",
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
                            text = "Değişiklikleri kaydet",
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
                            containerColor = Color(0xFFEEEEEE),
                            contentColor = Color(0xFF424242),
                        ),
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF424242),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isActive) "Takibi duraklat" else "Takibi devam ettir",
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
                            containerColor = Color(0xFFFFEBEE),
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
                            text = "Kaydı sil",
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
            containerColor = Color.White,
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
                            contentDescription = "Geri",
                            tint = Color(0xFF1E232A),
                        )
                    }
                    Column {
                        Text(
                            text = "Fatura dönemi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E232A),
                        )
                        Text(
                            text = "Yenileme sıklığını ve ilk dönemi seç.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF757575),
                        )
                    }
                }

                // 4 Sıklık Çipi (Günlük, Haftalık, Aylık, Yıllık)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FrequencyChipItem(
                        title = "Günlük",
                        icon = Icons.Outlined.WbSunny,
                        isSelected = tempFrequency == RecurrenceFrequency.DAILY,
                        onClick = { tempFrequency = RecurrenceFrequency.DAILY },
                        modifier = Modifier.weight(1f),
                    )
                    FrequencyChipItem(
                        title = "Haftalık",
                        icon = Icons.Outlined.CalendarMonth,
                        isSelected = tempFrequency == RecurrenceFrequency.WEEKLY,
                        onClick = { tempFrequency = RecurrenceFrequency.WEEKLY },
                        modifier = Modifier.weight(1f),
                    )
                    FrequencyChipItem(
                        title = "Aylık",
                        icon = Icons.Outlined.CalendarMonth,
                        isSelected = tempFrequency == RecurrenceFrequency.MONTHLY,
                        onClick = { tempFrequency = RecurrenceFrequency.MONTHLY },
                        modifier = Modifier.weight(1f),
                    )
                    FrequencyChipItem(
                        title = "Yıllık",
                        icon = Icons.Outlined.CalendarMonth,
                        isSelected = tempFrequency == RecurrenceFrequency.YEARLY,
                        onClick = { tempFrequency = RecurrenceFrequency.YEARLY },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Tekrar aralığı Sayaç: [-] [1] [+] (Pozitif tam sayı!)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Tekrar aralığı",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF424242),
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                    contentDescription = "Azalt",
                                    tint = if (tempIntervalInt > 1) Color(0xFF212121) else Color(0xFFBDBDBD),
                                )
                            }

                            Text(
                                text = tempIntervalInt.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121),
                            )

                            IconButton(
                                onClick = { if (tempIntervalInt < 99) tempIntervalInt++ },
                                enabled = tempIntervalInt < 99,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = "Artır",
                                    tint = Color(0xFF212121),
                                )
                            }
                        }
                    }
                }

                // Tekrar Açıklaması
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Tekrar",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF757575),
                    )

                    val repeatExplanation = when (tempFrequency) {
                        RecurrenceFrequency.MONTHLY -> if (tempIntervalInt == 1) "Her ay" else "Her $tempIntervalInt ayda bir"
                        RecurrenceFrequency.YEARLY -> if (tempIntervalInt == 1) "Her yıl" else "Her $tempIntervalInt yılda bir"
                        RecurrenceFrequency.WEEKLY -> if (tempIntervalInt == 1) "Her hafta" else "Her $tempIntervalInt haftada bir"
                        RecurrenceFrequency.DAILY -> if (tempIntervalInt == 1) "Her gün" else "Her $tempIntervalInt günde bir"
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFAFAFA),
                        border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                color = Color(0xFF212121),
                            )
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF757575),
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
                        text = "Uygula",
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
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Kategori seç",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E232A),
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
                            color = if (isSelected) Color(0xFFE8F5E9) else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) FeniqoSageGreen else FeniqoBorderColor),
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
                                        color = Color(0xFF212121),
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
                        color = Color(0xFFFAFAFA),
                        border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                tint = Color(0xFF757575),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Kategori seçimini kaldır",
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
            containerColor = Color.White,
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
                    text = "Para birimi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E232A),
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
                    text = "Hızlı seçim",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E232A),
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
                                    color = Color.White,
                                    border = BorderStroke(1.dp, FeniqoBorderColor),
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
                                            color = Color(0xFF212121),
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
                color = Color.White,
                border = BorderStroke(1.dp, FeniqoBorderColor),
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
                            .background(Color(0xFFE8F5E9), CircleShape),
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
                            text = "Hatırlatıcılar için izin gerekli",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E232A),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Yenilemeden 7 gün önce ve yenileme günü bildirim al.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF757575),
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
                                text = "İzin ver",
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
                                containerColor = Color(0xFFEEEEEE),
                                contentColor = Color(0xFF424242),
                            ),
                        ) {
                            Text(
                                text = "Şimdi değil",
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
                color = Color.White,
                border = BorderStroke(1.dp, FeniqoBorderColor),
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
                            .background(Color(0xFFFFEBEE), CircleShape),
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
                            text = "Kaydı silmek istiyor musun?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E232A),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Feniqo'daki yenileme takibi durdurulur. Hizmet sağlayıcındaki abonelik iptal edilmez.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF757575),
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
                                text = "Kaydı sil",
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
                                containerColor = Color(0xFFEEEEEE),
                                contentColor = Color(0xFF424242),
                            ),
                        ) {
                            Text(
                                text = "Vazgeç",
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
        color = if (isSelected) Color(0xFFE8F5E9) else Color.White,
        border = BorderStroke(1.dp, if (isSelected) FeniqoSageGreen else FeniqoBorderColor),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) FeniqoSageGreen else Color(0xFF757575),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) FeniqoSageGreen else Color(0xFF424242),
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
        color = if (isSelected) Color(0xFFE8F5E9) else Color.White,
        border = BorderStroke(1.dp, if (isSelected) FeniqoSageGreen else FeniqoBorderColor),
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
                color = if (isSelected) FeniqoSageGreen else Color(0xFF424242),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) FeniqoSageGreen else Color(0xFF757575),
            )
        }
    }
}

/**
 * Şablon Marka İkonu (Mevcut ikonlar ve renk paletinden temiz çözücü).
 */
@Composable
private fun TemplateBrandIcon(name: String, size: androidx.compose.ui.unit.Dp) {
    val (bgColor, iconTint, letter) = when (name.lowercase()) {
        "netflix" -> Triple(Color(0xFFE50914), Color.White, "N")
        "spotify" -> Triple(Color(0xFF1DB954), Color.White, "S")
        "youtube premium" -> Triple(Color(0xFFFF0000), Color.White, "▶")
        "icloud+" -> Triple(Color(0xFF3B99FC), Color.White, "☁")
        "notion" -> Triple(Color(0xFF000000), Color.White, "N")
        "amazon prime" -> Triple(Color(0xFF00A8E1), Color.White, "a")
        "google one" -> Triple(Color(0xFF4285F4), Color.White, "G")
        "disney+" -> Triple(Color(0xFF113CCF), Color.White, "D")
        else -> Triple(Color(0xFF424242), Color.White, name.take(1).uppercase())
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(bgColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = iconTint,
            fontSize = (size.value * 0.5f).sp,
        )
    }
}

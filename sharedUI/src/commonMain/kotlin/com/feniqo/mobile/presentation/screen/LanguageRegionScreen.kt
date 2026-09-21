package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DateFormatPreference
import com.feniqo.mobile.domain.model.FirstDayOfWeekPreference
import com.feniqo.mobile.domain.model.NumberFormatPreference
import com.feniqo.mobile.presentation.component.FormatPreviewGraphiteCard
import com.feniqo.mobile.presentation.component.SettingsGroupCard
import com.feniqo.mobile.presentation.component.SettingsRowItem
import com.feniqo.mobile.presentation.component.SettingsTopBar
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTouchTarget

/**
 * 05 Dil ve Bölge Ekranı, 06 Dil Seçimi, 19 Para Birimi Seçimi ve 20 Biçim Tercihleri.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageRegionScreen(
    currentLanguage: AppLanguage,
    currentRegion: String = "TR",
    currentCurrency: Currency,
    currentDateFormat: DateFormatPreference,
    currentNumberFormat: NumberFormatPreference,
    currentFirstDayOfWeek: FirstDayOfWeekPreference,
    onBack: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onRegionChange: (String) -> Unit = {},
    onCurrencyChange: (Currency) -> Unit,
    onDateFormatChange: (DateFormatPreference) -> Unit,
    onNumberFormatChange: (NumberFormatPreference) -> Unit,
    onFirstDayOfWeekChange: (FirstDayOfWeekPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showFormatSheet by remember { mutableStateOf(false) }

    // Dinamik biçim önizleme metni
    val previewText = remember(currentCurrency, currentNumberFormat, currentDateFormat) {
        val symbol = currentCurrency.symbol()
        val numberPart = currentNumberFormat.sample
        val datePart = currentDateFormat.sample
        "$symbol$numberPart / $datePart"
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = FeniqoSpacing.Screen,
                vertical = FeniqoSpacing.Medium,
            ),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
        ) {
            item {
                SettingsTopBar(
                    title = "Dil ve bölge",
                    onBack = onBack,
                )
            }

            // Biçim Önizleme Grafit Kartı
            item {
                FormatPreviewGraphiteCard(formattedPreview = previewText)
            }

            // Tercih Satırları
            item {
                SettingsGroupCard {
                    SettingsRowItem(
                        title = "Uygulama dili",
                        subtitle = when (currentLanguage) {
                            AppLanguage.TR -> "Türkçe"
                            AppLanguage.EN -> "English"
                        },
                        icon = Icons.Outlined.Translate,
                        onClick = { showLanguageSheet = true },
                    )
                    SettingsRowItem(
                        title = "Bölge",
                        subtitle = if (currentRegion.equals("US", ignoreCase = true)) "Amerika Birleşik Devletleri" else "Türkiye",
                        icon = Icons.Outlined.Place,
                        onClick = null,
                        showChevron = false,
                    )
                    SettingsRowItem(
                        title = "Varsayılan para birimi",
                        subtitle = "${currentCurrency.code} (${currentCurrency.displayName()})",
                        icon = Icons.Outlined.Paid,
                        onClick = { showCurrencySheet = true },
                    )
                    SettingsRowItem(
                        title = "Tarih biçimi",
                        subtitle = currentDateFormat.sample,
                        icon = Icons.Outlined.CalendarToday,
                        onClick = { showFormatSheet = true },
                    )
                    SettingsRowItem(
                        title = "Sayı biçimi",
                        subtitle = currentNumberFormat.sample,
                        icon = Icons.Outlined.Pin,
                        onClick = { showFormatSheet = true },
                    )
                    SettingsRowItem(
                        title = "Haftanın başlangıcı",
                        subtitle = currentFirstDayOfWeek.label,
                        icon = Icons.Outlined.DateRange,
                        showDivider = false,
                        onClick = { showFormatSheet = true },
                    )
                }
            }

            // Bilgi Banner'ı
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                    Text(
                        text = "Para birimi tercihi geçmiş işlemleri dönüştürmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // 06 Dil Seçimi Bottom Sheet
    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            var searchQuery by remember { mutableStateOf("") }
            var selectedLanguage by remember { mutableStateOf(currentLanguage) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                Text(
                    text = "Dil seçimi",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Dil ara") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                SettingsGroupCard {
                    val languages = listOf(
                        AppLanguage.TR to ("Türkçe" to "Uygulama Türkçe olarak gösterilir."),
                        AppLanguage.EN to ("English" to "App will be shown in English."),
                    ).filter { it.second.first.contains(searchQuery, ignoreCase = true) }

                    languages.forEachIndexed { index, (lang, textPair) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLanguage = lang }
                                .padding(FeniqoSpacing.Large),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = textPair.first,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                )
                                Text(
                                    text = textPair.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RadioButton(
                                selected = selectedLanguage == lang,
                                onClick = { selectedLanguage = lang },
                                colors = RadioButtonDefaults.colors(selectedColor = FeniqoSageGreen),
                            )
                        }
                        if (index < languages.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }

                Button(
                    onClick = {
                        onLanguageChange(selectedLanguage)
                        showLanguageSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FeniqoTouchTarget.PrimaryAction),
                ) {
                    Text("Uygula", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
            }
        }
    }

    // 19 Para Birimi Seçimi Bottom Sheet
    if (showCurrencySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCurrencySheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            var searchQuery by remember { mutableStateOf("") }
            val filteredCurrencies = remember(searchQuery) {
                Currency.entries.filter {
                    it.code.contains(searchQuery, ignoreCase = true) ||
                        it.displayName().contains(searchQuery, ignoreCase = true)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                Text(
                    text = "Para birimi seçimi",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Para birimi ara") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                ) {
                    items(filteredCurrencies) { currency ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCurrencyChange(currency)
                                    showCurrencySheet = false
                                }
                                .padding(vertical = FeniqoSpacing.Medium, horizontal = FeniqoSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currency.symbol(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.width(36.dp),
                            )
                            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currency.code,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    text = currency.displayName(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RadioButton(
                                selected = currentCurrency == currency,
                                onClick = {
                                    onCurrencyChange(currency)
                                    showCurrencySheet = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = FeniqoSageGreen),
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
            }
        }
    }

    // 20 Biçim Tercihleri Bottom Sheet
    if (showFormatSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFormatSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            var selectedDate by remember { mutableStateOf(currentDateFormat) }
            var selectedNumber by remember { mutableStateOf(currentNumberFormat) }
            var selectedFirstDay by remember { mutableStateOf(currentFirstDayOfWeek) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Medium),
            ) {
                Text(
                    text = "Biçim tercihleri",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )

                // Tarih Biçimi
                Text("Tarih biçimi", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                SettingsGroupCard {
                    DateFormatPreference.entries.forEachIndexed { i, df ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDate = df }
                                .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(df.sample, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            RadioButton(
                                selected = selectedDate == df,
                                onClick = { selectedDate = df },
                                colors = RadioButtonDefaults.colors(selectedColor = FeniqoSageGreen),
                            )
                        }
                        if (i < DateFormatPreference.entries.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }

                // Sayı Biçimi
                Text("Sayı biçimi", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                SettingsGroupCard {
                    NumberFormatPreference.entries.forEachIndexed { i, nf ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedNumber = nf }
                                .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(nf.sample, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            RadioButton(
                                selected = selectedNumber == nf,
                                onClick = { selectedNumber = nf },
                                colors = RadioButtonDefaults.colors(selectedColor = FeniqoSageGreen),
                            )
                        }
                        if (i < NumberFormatPreference.entries.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }

                // Haftanın İlk Günü
                Text("Haftanın ilk günü", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                SettingsGroupCard {
                    FirstDayOfWeekPreference.entries.forEachIndexed { i, fd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFirstDay = fd }
                                .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(fd.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            RadioButton(
                                selected = selectedFirstDay == fd,
                                onClick = { selectedFirstDay = fd },
                                colors = RadioButtonDefaults.colors(selectedColor = FeniqoSageGreen),
                            )
                        }
                        if (i < FirstDayOfWeekPreference.entries.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }

                Button(
                    onClick = {
                        onDateFormatChange(selectedDate)
                        onNumberFormatChange(selectedNumber)
                        onFirstDayOfWeekChange(selectedFirstDay)
                        showFormatSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FeniqoTouchTarget.PrimaryAction),
                ) {
                    Text("Uygula", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
            }
        }
    }
}

private fun Currency.displayName(): String = when (this) {
    Currency.TRY -> "Türk lirası"
    Currency.USD -> "Amerikan doları"
    Currency.EUR -> "Euro"
    Currency.GBP -> "İngiliz sterlini"
}

private fun Currency.symbol(): String = com.feniqo.mobile.presentation.util.MoneyFormatter.getCurrencySymbol(this)

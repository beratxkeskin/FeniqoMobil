package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.presentation.component.DebtCurrencyPickerSheet
import com.feniqo.mobile.presentation.component.DebtSettledBanner
import com.feniqo.mobile.presentation.component.symbol
import com.feniqo.mobile.presentation.debt.DebtBalanceSummaryUiModel
import com.feniqo.mobile.presentation.debt.DebtFormFieldError
import com.feniqo.mobile.presentation.debt.DebtFormInput
import com.feniqo.mobile.presentation.debt.DebtFormInputErrors
import com.feniqo.mobile.presentation.debt.DebtPaymentHistoryItemUiModel
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary
import com.feniqo.mobile.presentation.theme.FeniqoWarmStoneBackground
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Borç ve Alacak kayıt oluşturma (Panel 04) ve düzenleme/detay (Panel 02 & 03) ekranı.
 * Grafit bakiye özeti (#303536), ödeme geçmişi, vade ve para birimi seçicileri (Panel 09-10),
 * ve kapanmış kayıt durumu (Panel 13) entegredir.
 */
@Composable
fun DebtFormScreen(
    input: DebtFormInput,
    errors: DebtFormInputErrors,
    isSubmitting: Boolean,
    isEditMode: Boolean,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onTypeChange: (DebtType) -> Unit,
    onDueDateClick: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRequestDelete: () -> Unit,
    onSubmit: () -> Unit,
    onAddPayment: (() -> Unit)? = null,
    paymentsHistory: List<DebtPaymentHistoryItemUiModel> = emptyList(),
    balanceSummary: DebtBalanceSummaryUiModel? = null,
    activeWorkspaceName: String? = null,
    modifier: Modifier = Modifier,
) {
    val isEnabled = !isSubmitting
    var showCurrencyPicker by remember { mutableStateOf(false) }

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

            // Üst Başlık & Geri / Sil Barı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(
                        onClick = onBack,
                        enabled = isEnabled,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = FeniqoTextPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEditMode) "Kaydı düzenle" else "Yeni kayıt",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.3).sp,
                        ),
                        color = FeniqoTextPrimary,
                    )
                }

                if (isEditMode) {
                    IconButton(
                        onClick = onRequestDelete,
                        enabled = isEnabled,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Kaydı sil",
                            tint = Color(0xFFDC2626),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Kaydırılabilir Form İçeriği
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Panel 02: Üst Kayıt Bilgi Kartı (Sadece Düzenleme Modunda)
                if (isEditMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val isDebt = input.type == DebtType.DEBT
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDebt) Color(0xFFFEF2F2) else Color(0xFFECFDF5)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isDebt) Icons.Outlined.CreditCard else Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = if (isDebt) Color(0xFFDC2626) else Color(0xFF16A34A),
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = input.titleInput.ifBlank { "Borç / Alacak Kaydı" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = FeniqoTextPrimary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isDebt) "Borç" else "Alacak",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FeniqoTextSecondary,
                                )
                            }

                            // Durum rozeti (Aktif / Tamamlandı)
                            val isSettled = balanceSummary?.isSettled == true
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSettled) Color(0xFFDCFCE7) else Color(0xFFE0F2FE))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (isSettled) "Tamamlandı" else "Aktif",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSettled) Color(0xFF166534) else Color(0xFF0369A1),
                                )
                            }
                        }
                    }
                }

                // Panel 02: Grafit Bakiye Durum Kartı (#303536)
                if (isEditMode && balanceSummary != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF303536)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(
                                text = if (input.type == DebtType.DEBT) "Kalan Borç" else "Kalan Alacak",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = balanceSummary.formattedRemainingAmount,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 26.sp,
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // İlerleme Çubuğu
                            LinearProgressIndicator(
                                progress = { balanceSummary.progressRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF22C55E),
                                trackColor = Color(0xFF4B5563),
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Toplam: ${balanceSummary.formattedPrincipalAmount}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFD1D5DB),
                                )
                                Text(
                                    text = "${if (input.type == DebtType.DEBT) "Ödenen" else "Tahsil"}: ${balanceSummary.formattedTotalPaid}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFD1D5DB),
                                )
                                Text(
                                    text = "%${(balanceSummary.progressRatio * 100).toInt()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF22C55E),
                                )
                            }
                        }
                    }
                }

                // Panel 13: Tamamlandı Banner'ı veya + Ödeme/Tahsilat Ekle Butonu
                if (isEditMode) {
                    if (balanceSummary?.isSettled == true) {
                        DebtSettledBanner(isDebt = input.type == DebtType.DEBT)
                    } else if (onAddPayment != null) {
                        Button(
                            onClick = onAddPayment,
                            enabled = isEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = if (input.type == DebtType.DEBT) "+ Ödeme ekle" else "+ Tahsilat ekle",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color.White,
                            )
                        }
                    }
                }

                // Kayıt Bilgileri Bölüm Başlığı
                Text(
                    text = "Kayıt bilgileri",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FeniqoTextPrimary,
                    fontSize = 15.sp,
                )

                // Tür Seçimi Segmenti (Borç / Alacak)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF3F4F6))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Borç Sekmesi
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (input.type == DebtType.DEBT) FeniqoSageGreen else Color.Transparent)
                                .clickable(
                                    enabled = isEnabled,
                                    role = Role.Tab,
                                    onClick = { onTypeChange(DebtType.DEBT) },
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Borç",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (input.type == DebtType.DEBT) Color.White else Color(0xFF4B5563),
                            )
                        }

                        // Alacak Sekmesi
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (input.type == DebtType.RECEIVABLE) FeniqoSageGreen else Color.Transparent)
                                .clickable(
                                    enabled = isEnabled,
                                    role = Role.Tab,
                                    onClick = { onTypeChange(DebtType.RECEIVABLE) },
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Alacak",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (input.type == DebtType.RECEIVABLE) Color.White else Color(0xFF4B5563),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Borç: ödeyeceğim · Alacak: tahsil edeceğim",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                // Başlık / Kişi / Kurum
                OutlinedTextField(
                    value = input.titleInput,
                    onValueChange = onTitleChange,
                    label = { Text("Kayıt adı / kişi / kurum *") },
                    placeholder = { Text("örn. Ahmet Borç, Garanti Kredi Kartı") },
                    isError = errors.titleError != null,
                    supportingText = {
                        errors.titleError?.let {
                            Text(
                                text = when (it) {
                                    DebtFormFieldError.TITLE_REQUIRED -> "Başlık zorunludur."
                                    DebtFormFieldError.TITLE_TOO_LONG -> "Başlık en fazla 500 karakter olabilir."
                                    else -> "Geçersiz başlık."
                                },
                                color = Color(0xFFDC2626),
                            )
                        }
                    },
                    singleLine = true,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = FeniqoSageGreen,
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                    ),
                )

                // Toplam Tutar
                OutlinedTextField(
                    value = input.amountInput,
                    onValueChange = onAmountChange,
                    label = { Text("Toplam tutar *") },
                    placeholder = { Text("0,00") },
                    trailingIcon = {
                        Text(
                            text = input.currency.symbol(),
                            fontWeight = FontWeight.Bold,
                            color = FeniqoTextSecondary,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                    isError = errors.amountError != null,
                    supportingText = {
                        errors.amountError?.let {
                            Text(
                                text = when (it) {
                                    DebtFormFieldError.AMOUNT_REQUIRED -> "Tutar zorunludur."
                                    DebtFormFieldError.AMOUNT_NON_POSITIVE -> "Tutar sıfırdan büyük olmalıdır."
                                    DebtFormFieldError.AMOUNT_INVALID -> "Geçerli bir tutar girin."
                                    else -> "Geçersiz tutar."
                                },
                                color = Color(0xFFDC2626),
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
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = FeniqoSageGreen,
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                    ),
                )

                // Para Birimi Seçimi (Panel 10 Sheet entegre)
                if (isEditMode) {
                    // Düzenlemede Kilitli Kart
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Para birimi",
                                    fontSize = 11.sp,
                                    color = FeniqoTextSecondary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${input.currency.code} (${input.currency.symbol()})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FeniqoTextPrimary,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Kilitli",
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Değiştirilemez",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9CA3AF),
                                )
                            }
                        }
                    }
                } else {
                    // Yeni Kayıtta Seçilebilir Kart (Panel 10 Sheet tetikler)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = isEnabled,
                                role = Role.Button,
                                onClick = { showCurrencyPicker = true },
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Para birimi",
                                    fontSize = 11.sp,
                                    color = FeniqoTextSecondary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${input.currency.code} (${input.currency.symbol()})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FeniqoTextPrimary,
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Para birimi seç",
                                tint = FeniqoTextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Vade Tarihi Kartı (Panel 09 Sheet tetikler)
                Column {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = isEnabled,
                                role = Role.Button,
                                onClick = onDueDateClick,
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(
                            1.dp,
                            if (errors.dueDateError != null) Color(0xFFDC2626) else Color(0xFFE5E7EB),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Vade tarihi *",
                                    fontSize = 11.sp,
                                    color = FeniqoTextSecondary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (input.dueDate != null) {
                                        DateFormatter.formatReadableDate(input.dueDate)
                                    } else {
                                        "Tarih seçin"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (input.dueDate != null) FeniqoTextPrimary else Color(0xFF9CA3AF),
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.DateRange,
                                contentDescription = "Vade tarihi seç",
                                tint = FeniqoSageGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (errors.dueDateError != null) {
                        Text(
                            text = "Vade tarihi seçilmelidir.",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                }

                // Açıklama
                OutlinedTextField(
                    value = input.descriptionInput,
                    onValueChange = onDescriptionChange,
                    label = { Text("Açıklama (opsiyonel)") },
                    placeholder = { Text("Not veya detay ekleyin") },
                    isError = errors.descriptionError != null,
                    supportingText = {
                        errors.descriptionError?.let {
                            Text(
                                text = "Açıklama en fazla 500 karakter olabilir.",
                                color = Color(0xFFDC2626),
                            )
                        }
                    },
                    minLines = 2,
                    maxLines = 4,
                    enabled = isEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = FeniqoSageGreen,
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                    ),
                )

                // Panel 03: Ödeme / Tahsilat Geçmişi Bölümü (Düzenleme Modunda)
                if (isEditMode) {
                    val isDebt = input.type == DebtType.DEBT
                    val historyTitle = if (isDebt) "Ödeme geçmişi" else "Tahsilat geçmişi"

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = historyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FeniqoTextPrimary,
                        fontSize = 15.sp,
                    )

                    if (paymentsHistory.isEmpty()) {
                        Text(
                            text = if (isDebt) "Henüz bir ödeme kaydı bulunmuyor." else "Henüz bir tahsilat kaydı bulunmuyor.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FeniqoTextSecondary,
                            fontSize = 13.sp,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            paymentsHistory.forEach { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isDebt) Color(0xFFFEF2F2) else Color(0xFFECFDF5)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = if (isDebt) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                                                    contentDescription = null,
                                                    tint = if (isDebt) Color(0xFFDC2626) else Color(0xFF16A34A),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column {
                                                Text(
                                                    text = item.formattedDate,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = FeniqoTextPrimary,
                                                )
                                                Text(
                                                    text = if (isDebt) "Ödeme yapıldı" else "Tahsilat yapıldı",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = FeniqoTextSecondary,
                                                    fontSize = 11.sp,
                                                )
                                            }
                                        }

                                        Text(
                                            text = "${if (isDebt) "-" else "+"}${item.formattedAmount}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDebt) Color(0xFFDC2626) else Color(0xFF16A34A),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Panel 03: Alt 'Kaydı sil' Kırmızı Eylem Butonu
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onRequestDelete,
                        enabled = isEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kaydı sil",
                                color = Color(0xFFDC2626),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Birincil Eylem Butonu (56dp, FeniqoSageGreen)
            Button(
                onClick = onSubmit,
                enabled = isEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FeniqoSageGreen),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (isEditMode) "✓ Değişiklikleri kaydet" else "+ Kaydı oluştur",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        ),
                    )
                }
            }
        }
    }

    // Panel 10: Para Birimi Seçim Bottom Sheet Modal
    if (showCurrencyPicker) {
        DebtCurrencyPickerSheet(
            selectedCurrency = input.currency,
            onDismiss = { showCurrencyPicker = false },
            onCurrencySelected = { currency ->
                onCurrencyChange(currency)
                showCurrencyPicker = false
            },
        )
    }
}

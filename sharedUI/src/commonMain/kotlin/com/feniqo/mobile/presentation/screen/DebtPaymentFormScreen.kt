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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.runtime.Composable
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
import com.feniqo.mobile.domain.model.Debt
import com.feniqo.mobile.domain.model.DebtType
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.presentation.debt.DebtPaymentFormFieldError
import com.feniqo.mobile.presentation.debt.DebtPaymentFormInput
import com.feniqo.mobile.presentation.debt.DebtPaymentFormInputErrors
import com.feniqo.mobile.presentation.theme.FeniqoSageGreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTextPrimary
import com.feniqo.mobile.presentation.theme.FeniqoTextSecondary
import com.feniqo.mobile.presentation.theme.FeniqoWarmStoneBackground
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Borç ödeme ve alacak tahsilat formu ekranı (Panel 05 & 06, hata durumu Panel 14).
 * Grafit bakiye durumu (#303536), ödeme tutarı girişi, maksimum limit rehberliği,
 * aşım hata durumu (Panel 14) ve bilgilendirme notu içerir.
 */
@Composable
fun DebtPaymentFormScreen(
    parentDebt: Debt,
    remainingAmount: Money,
    isSettled: Boolean,
    input: DebtPaymentFormInput,
    errors: DebtPaymentFormInputErrors,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onPaidOnClick: () -> Unit,
    onSubmit: () -> Unit,
    totalPaid: Money = Money(0L, parentDebt.amount.currency),
    modifier: Modifier = Modifier,
) {
    val isEnabled = !isSubmitting && !isSettled
    val isDebt = parentDebt.type == DebtType.DEBT
    val titleText = if (isDebt) "Ödeme ekle" else "Tahsilat ekle"

    val totalMinor = parentDebt.amount.amountMinor
    val paidMinor = totalPaid.amountMinor
    val progressRatio = if (totalMinor > 0L) {
        (paidMinor.toFloat() / totalMinor.toFloat()).coerceIn(0f, 1f)
    } else 0f

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

            // Üst Başlık & Geri Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = !isSubmitting,
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
                    text = titleText,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = FeniqoTextPrimary,
                )
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Kaydırılabilir Form Gövdesi
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Üst Kayıt Bilgi Kartı (Panel 05 & 06)
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

                        Column {
                            Text(
                                text = parentDebt.title,
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
                    }
                }

                // 2. Grafit Bakiye Durum Kartı (#303536, Panel 05 & 06)
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
                            text = if (isDebt) "Kalan Borç" else "Kalan Alacak",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = MoneyFormatter.format(remainingAmount),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 26.sp,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // İlerleme Çubuğu
                        LinearProgressIndicator(
                            progress = { progressRatio },
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
                                text = "Toplam: ${MoneyFormatter.format(parentDebt.amount)}",
                                fontSize = 11.sp,
                                color = Color(0xFFD1D5DB),
                            )
                            Text(
                                text = "${if (isDebt) "Ödenen" else "Tahsil"}: ${MoneyFormatter.format(totalPaid)}",
                                fontSize = 11.sp,
                                color = Color(0xFFD1D5DB),
                            )
                            Text(
                                text = "%${(progressRatio * 100).toInt()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF22C55E),
                            )
                        }
                    }
                }

                // Kapanmış Borç Durumu Bilgisi
                if (isSettled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    ) {
                        Text(
                            text = "Bu borç / alacak tamamen kapanmıştır. Yeni ödeme eklenemez.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF166534),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                // 3. Tutar Giriş Kartı (Panel 05, 06 & Hata Durumu 14)
                Column {
                    val hasAmountError = errors.amountError != null
                    OutlinedTextField(
                        value = input.amountInput,
                        onValueChange = onAmountChange,
                        label = { Text(if (isDebt) "Ödeme Tutarı *" else "Tahsilat Tutarı *") },
                        placeholder = { Text("0,00") },
                        trailingIcon = {
                            Text(
                                text = parentDebt.amount.currency.code,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = FeniqoTextSecondary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                        isError = hasAmountError,
                        singleLine = true,
                        enabled = isEnabled,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDebt) Color(0xFFDC2626) else Color(0xFF16A34A),
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = if (hasAmountError) Color(0xFFDC2626) else FeniqoSageGreen,
                            unfocusedBorderColor = if (hasAmountError) Color(0xFFDC2626) else Color(0xFFE5E7EB),
                        ),
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Panel 14: Kalan Bakiyeyi Aşım veya Diğer Hata Durumu
                    if (errors.amountError != null) {
                        val errorText = when (errors.amountError) {
                            DebtPaymentFormFieldError.EXCEEDS_REMAINING_AMOUNT ->
                                "Ödeme tutarı kalan borcu aşamaz. Maksimum ödeme tutarı: ${MoneyFormatter.format(remainingAmount)}"
                            DebtPaymentFormFieldError.AMOUNT_REQUIRED ->
                                "Ödeme tutarı zorunludur."
                            DebtPaymentFormFieldError.AMOUNT_NON_POSITIVE ->
                                "Tutar sıfırdan büyük olmalıdır."
                            DebtPaymentFormFieldError.DEBT_ALREADY_SETTLED ->
                                "Borç tamamen kapandığı için ödeme yapılamaz."
                            DebtPaymentFormFieldError.CURRENCY_MISMATCH ->
                                "Para birimi uyuşmuyor."
                            else -> "Geçersiz tutar."
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorText,
                                    fontSize = 12.sp,
                                    color = Color(0xFFDC2626),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    } else {
                        // Maksimum limit rehberi (Panel 05 & 06)
                        Text(
                            text = "Maksimum: ${MoneyFormatter.format(remainingAmount)}",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }

                // 4. Ödeme Tarihi Seçici Kartı (Panel 09 Sheet tetikler)
                Column {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = isEnabled,
                                role = Role.Button,
                                onClick = onPaidOnClick,
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(
                            1.dp,
                            if (errors.dateError != null) Color(0xFFDC2626) else Color(0xFFE5E7EB),
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
                                    text = if (isDebt) "Ödeme Tarihi *" else "Tahsilat Tarihi *",
                                    fontSize = 11.sp,
                                    color = FeniqoTextSecondary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = input.paidOn?.let { DateFormatter.formatReadableDate(it) } ?: "Tarih seçin",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (input.paidOn != null) FeniqoTextPrimary else Color(0xFF9CA3AF),
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.DateRange,
                                contentDescription = "Tarih seç",
                                tint = FeniqoSageGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (errors.dateError != null) {
                        Text(
                            text = "Ödeme tarihi seçilmelidir.",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                }

                // 5. Bilgilendirme Notu (Panel 05 & 06)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Bu işlem bir ${if (isDebt) "ödeme" else "tahsilat"} kaydı oluşturur ve borcun kalan bakiyesini günceller.",
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            lineHeight = 16.sp,
                        )
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
                        text = if (isDebt) "+ Ödemeyi kaydet" else "+ Tahsilatı kaydet",
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
}

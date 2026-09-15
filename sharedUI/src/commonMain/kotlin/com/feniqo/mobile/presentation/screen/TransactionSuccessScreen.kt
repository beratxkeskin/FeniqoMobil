package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.component.CategorySemanticIconResolver
import com.feniqo.mobile.presentation.component.toDisplayText
import com.feniqo.mobile.presentation.theme.*
import com.feniqo.mobile.presentation.transaction.TransactionDisplayModel
import com.feniqo.mobile.presentation.transaction.TransactionSuccessUiState
import com.feniqo.mobile.presentation.util.ColorParser
import com.feniqo.mobile.presentation.util.DateFormatter

/**
 * Yeni işlem oluşturulduktan sonra gösterilen warm-luxury başarı ekranıdır.
 * Room SSOT üzerinden yüklenen veriyi gösterir; düzenleme işlemlerinde bu ekran açılmaz.
 */
@Composable
fun TransactionSuccessScreen(
    uiState: TransactionSuccessUiState,
    onAddNewTransaction: (TransactionType) -> Unit,
    onViewTransaction: (EntityId) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Üst Bar: Feniqo Başlığı ve Kapat Butonu
            TransactionSuccessTopBar(onClose = onClose)

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                        )
                    }
                }

                uiState.errorMessage != null || uiState.transaction == null -> {
                    TransactionSuccessErrorView(
                        errorMessage = uiState.errorMessage ?: "İşlem bilgisi yüklenemedi.",
                        onClose = onClose,
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    TransactionSuccessContent(
                        transaction = uiState.transaction,
                        onAddNewTransaction = onAddNewTransaction,
                        onViewTransaction = onViewTransaction,
                        onClose = onClose,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionSuccessTopBar(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FeniqoSpacing.Large, vertical = FeniqoSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Feniqo",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Kapat" },
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransactionSuccessContent(
    transaction: TransactionDisplayModel,
    onAddNewTransaction: (TransactionType) -> Unit,
    onViewTransaction: (EntityId) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = FeniqoSpacing.Large)
            .padding(bottom = FeniqoSpacing.ExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        // Yeşil Başarı Rozeti (Çift Halka)
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Başlık ve Açıklama
        Text(
            text = "İşlem Kaydedildi!",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Bu cihazda kaydedildi. ${transaction.syncStatus.transactionStatusText()}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = FeniqoSpacing.Medium),
        )

        Spacer(Modifier.height(24.dp))

        // İşlem Özet Kartı
        TransactionSummaryCard(transaction = transaction)

        Spacer(Modifier.height(16.dp))

        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) { Text("İşlemlere dön") }
        Spacer(Modifier.height(10.dp))
        // Aksiyon Butonları
        Button(
            onClick = { onAddNewTransaction(transaction.type) },
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .semantics { contentDescription = "Yeni işlem ekle" },
        ) {
            Text(
                text = "Yeni İşlem Ekle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { onViewTransaction(transaction.id) },
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .semantics { contentDescription = "İşlemi düzenle" },
        ) {
            Text(
                text = "İşlemi düzenle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TransactionSummaryCard(
    transaction: TransactionDisplayModel,
    modifier: Modifier = Modifier,
) {
    val categoryColor = ColorParser.parseHexColorOrNull(transaction.categoryColorHex) ?: MaterialTheme.colorScheme.primary
    val categoryIcon = CategorySemanticIconResolver.resolve(transaction.categoryIconKey)
    val amountColor = if (transaction.type == TransactionType.EXPENSE) FeniqoExpense else FeniqoEmerald

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeniqoRadius.Large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // Üst Bölüm: Kategori ve Tutar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Kategori Rozeti
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(categoryColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Text(
                        text = transaction.categoryName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // İşlem Adı
            val displayTitle = transaction.description?.takeIf { it.isNotBlank() } ?: transaction.categoryName
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            // Büyük Tutar
            Text(
                text = transaction.formattedAmount,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                ),
                color = amountColor,
            )

            Spacer(Modifier.height(16.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )

            Spacer(Modifier.height(14.dp))

            // Detay Satırları
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryDetailRow(
                    label = "Tarih",
                    value = DateFormatter.formatReadableDate(transaction.transactionDate),
                )

                SummaryDetailRow(
                    label = "Ödeme Yöntemi",
                    value = transaction.paymentMethod.toDisplayText(),
                )

                if (transaction.installment != null) {
                    SummaryDetailRow(
                        label = "Taksit",
                        value = "${transaction.installment.number}/${transaction.installment.total}",
                    )
                }

                if (!transaction.note.isNullOrBlank()) {
                    SummaryDetailRow(
                        label = "Not",
                        value = transaction.note,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun TransactionSuccessErrorView(
    errorMessage: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(FeniqoSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onClose,
            shape = RoundedCornerShape(FeniqoRadius.Medium),
        ) {
            Text("İşlemlere Dön")
        }
    }
}

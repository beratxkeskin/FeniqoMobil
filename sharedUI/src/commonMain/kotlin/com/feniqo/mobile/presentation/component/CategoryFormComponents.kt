package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.validation.CategoryValidationRules
import com.feniqo.mobile.presentation.category.CategoryFormFieldError
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.ColorParser

private val SageGreen = Color(0xFF2D5A43)
private val SoftExpenseRed = Color(0xFFFEE2E2)
private val ExpenseRedText = Color(0xFFDC2626)
private val SoftIncomeGreen = Color(0xFFDCFCE7)
private val IncomeGreenText = Color(0xFF16A34A)

/**
 * Kategori simgesi seçim modeli.
 */
data class CategoryIconOption(
    val key: String?,
    val label: String,
)

/**
 * Platformdan bağımsız 12 kurumsal/canonical renk paleti.
 */
val PRESET_CATEGORY_COLORS: List<String> = listOf(
    "#10B981",
    "#34D399",
    "#6EE7B7",
    "#059669",
    "#FBBF24",
    "#EF4444",
    "#F59E0B",
    "#3B82F6",
    "#EC4899",
    "#8B5CF6",
    "#6366F1",
    "#6B7280",
)

/**
 * Platformdan bağımsız canonical ve sık kullanılan simge seçenekleri kataloğu.
 */
val PRESET_CATEGORY_ICONS: List<CategoryIconOption> = listOf(
    CategoryIconOption(null, "İkonsuz"),
    CategoryIconOption("briefcase", "Çanta"),
    CategoryIconOption("laptop", "Bilgisayar"),
    CategoryIconOption("graduation-cap", "Mezuniyet"),
    CategoryIconOption("trending-up", "Yükseliş"),
    CategoryIconOption("dollar-sign", "Para"),
    CategoryIconOption("utensils", "Yemek"),
    CategoryIconOption("shopping-cart", "Market"),
    CategoryIconOption("car", "Araç"),
    CategoryIconOption("home", "Ev"),
    CategoryIconOption("file-text", "Belge"),
    CategoryIconOption("music", "Müzik"),
    CategoryIconOption("book-open", "Kitap"),
    CategoryIconOption("heart-pulse", "Sağlık"),
    CategoryIconOption("credit-card", "Kart"),
    CategoryIconOption("percent", "Yüzde"),
    CategoryIconOption("help-circle", "Diğer"),
    CategoryIconOption("piggy-bank", "Birikim"),
    CategoryIconOption("gift", "Hediye"),
    CategoryIconOption("tag", "Etiket"),
)

/**
 * Renk kodunu kullanıcı dostu Türkçe isme dönüştürür.
 */
fun getPresetColorName(hex: String): String = when (hex.trim().uppercase()) {
    "#10B981" -> "Zümrüt"
    "#34D399" -> "Açık Yeşil"
    "#6EE7B7" -> "Nane"
    "#059669" -> "Koyu Yeşil"
    "#FBBF24" -> "Sarı"
    "#EF4444" -> "Kırmızı"
    "#F59E0B" -> "Turuncu"
    "#3B82F6" -> "Mavi"
    "#EC4899" -> "Pembe"
    "#8B5CF6" -> "Mor"
    "#6366F1" -> "İndigo"
    "#6B7280" -> "Gri"
    else -> "Özel Renk"
}

/**
 * Simge anahtarını kullanıcı dostu Türkçe etikete dönüştürür.
 */
fun getPresetIconLabel(key: String?): String {
    val found = PRESET_CATEGORY_ICONS.firstOrNull { it.key == key }
    return found?.label ?: if (key == null) "İkonsuz" else "Özel Simge"
}

/**
 * Üst gezinme ve çalışma alanı başlığı (Görsel 02 & 03).
 */
@Composable
fun CategoryFormHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isBackEnabled: Boolean = true,
    activeWorkspaceName: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(enabled = isBackEnabled, onClick = onBack)
                .semantics {
                    contentDescription = "$title, geri dön"
                    role = Role.Button
                },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        ActiveWorkspaceIndicator(
            workspaceName = activeWorkspaceName,
            isCompact = true,
        )
    }
}

/**
 * Canlı Önizleme Kartı (Görsel 02 & 03).
 * Seçilen ad, renk, simge ve kategori türünü görsel olarak yansıtır.
 */
@Composable
fun CategoryLivePreviewCard(
    name: String,
    type: TransactionType,
    colorHex: String,
    iconKey: String?,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val categoryColor = ColorParser.parseHexColorOrNull(colorHex) ?: SageGreen
    val previewBgColor = if (isDark) {
        categoryColor.copy(alpha = 0.15f)
    } else {
        categoryColor.copy(alpha = 0.10f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = previewBgColor,
        border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.20f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Büyük Tonal İkon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(categoryColor.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CategorySemanticIconResolver.resolve(iconKey),
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(34.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = name.ifBlank { "Kategori Adı" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (name.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (type == TransactionType.EXPENSE) "Gider kategorisi" else "Gelir kategorisi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Kategori adı giriş alanı (Görsel 02, 03 & 11).
 */
@Composable
fun CategoryNameField(
    name: String,
    onNameChanged: (String) -> Unit,
    nameError: CategoryFormFieldError?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Kategori adı",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { input ->
                onNameChanged(input.take(CategoryValidationRules.MAX_NAME_LENGTH))
            },
            placeholder = {
                Text(
                    text = "Kategori adı gir",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            singleLine = true,
            enabled = enabled,
            isError = nameError != null,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
                unfocusedContainerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                errorContainerColor = if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f) else Color.White,
                errorBorderColor = MaterialTheme.colorScheme.error,
                focusedBorderColor = SageGreen,
                unfocusedBorderColor = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .semantics {
                    contentDescription = "Kategori adı metin alanı"
                },
        )

        if (nameError != null) {
            Text(
                text = nameError.toDisplayText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
/**
 * Kategori Türü Seçimi (Görsel 02 & 03).
 * Oluştururken serbestçe değiştirilebilir, düzenlemede kilitlidir.
 */
@Composable
fun CategoryTypeSection(
    selectedType: TransactionType,
    isEditMode: Boolean,
    isTypeEditable: Boolean,
    onTypeSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Kategori türü",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (isEditMode) {
            // Kilitli görünüm (Görsel 03)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color(0xFFF4F2EE),
                border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f) else Color(0xFFE5E2DA)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = if (selectedType == TransactionType.EXPENSE) ExpenseRedText else IncomeGreenText,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (selectedType == TransactionType.EXPENSE) "−" else "+",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                        Text(
                            text = if (selectedType == TransactionType.EXPENSE) "Gider" else "Gelir",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Kategori türü kilitli",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Text(
                text = "Mevcut işlemlerle tutarlılık için tür değiştirilemez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        } else {
            // Düzenlenebilir görünüm (Görsel 02)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Gider Butonu
                val isExpense = selectedType == TransactionType.EXPENSE
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isExpense && isDark -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        isExpense -> SoftExpenseRed
                        isDark -> MaterialTheme.colorScheme.surface
                        else -> Color.White
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isExpense) ExpenseRedText.copy(alpha = 0.5f) else if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clickable(enabled = isTypeEditable) {
                            onTypeSelected(TransactionType.EXPENSE)
                        }
                        .semantics {
                            contentDescription = "Gider kategorisi seçimi, ${if (isExpense) "seçili" else "seçili değil"}"
                            role = Role.Button
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(ExpenseRedText, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "−",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gider",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium,
                            color = if (isExpense) ExpenseRedText else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Gelir Butonu
                val isIncome = selectedType == TransactionType.INCOME
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isIncome && isDark -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        isIncome -> SoftIncomeGreen
                        isDark -> MaterialTheme.colorScheme.surface
                        else -> Color.White
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isIncome) IncomeGreenText.copy(alpha = 0.5f) else if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clickable(enabled = isTypeEditable) {
                            onTypeSelected(TransactionType.INCOME)
                        }
                        .semantics {
                            contentDescription = "Gelir kategorisi seçimi, ${if (isIncome) "seçili" else "seçili değil"}"
                            role = Role.Button
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(IncomeGreenText, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gelir",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Medium,
                            color = if (isIncome) IncomeGreenText else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Görsel Ayarları Bölümü (Görsel 02 & 03).
 * Renk ve Simge seçimi için açılır tetikleyicileri barındırır.
 */
@Composable
fun CategoryVisualSettingsCard(
    colorHex: String,
    iconKey: String?,
    onColorClick: () -> Unit,
    onIconClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val categoryColor = ColorParser.parseHexColorOrNull(colorHex) ?: SageGreen

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Görsel ayarları",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
            ),
            border = BorderStroke(
                1.dp,
                if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f) else Color(0xFFE2E8F0),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Renk Satırı
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled, onClick = onColorClick)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(categoryColor, CircleShape),
                        )
                        Text(
                            text = "Renk",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = getPresetColorName(colorHex),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f) else Color(0xFFF1F5F9),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                // Simge Satırı
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled, onClick = onIconClick)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(categoryColor.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = CategorySemanticIconResolver.resolve(iconKey),
                                contentDescription = null,
                                tint = categoryColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = "Simge",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = getPresetIconLabel(iconKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Text(
            text = "Önizleme seçimlerine göre güncellenir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * 04 Simge Seçimi Modalı (Görsel 04).
 * Canonical 20 simgeyi 4 sütunlu ızgarada sunar.
 * Seçili simge kategori renginde halka ve etiketle vurgulanır.
 * "Seçimi uygula" butonuna basılana kadar değişiklik forma aktarılmaz.
 */
@Composable
fun CategoryIconSelectionDialog(
    currentIconKey: String?,
    categoryColorHex: String,
    onApply: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedKey by remember { mutableStateOf(currentIconKey) }
    val categoryColor = ColorParser.parseHexColorOrNull(categoryColorHex) ?: SageGreen
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Başlık ve Geri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Simge seçimini kapat",
                        )
                    }
                    Text(
                        text = "Simge seç",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // 20 Simge Izgarası
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(PRESET_CATEGORY_ICONS, key = { it.key ?: "none" }) { option ->
                        val isSelected = option.key == selectedKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { selectedKey = option.key }
                                .semantics {
                                    contentDescription = "${option.label} simgesi, ${if (isSelected) "seçili" else "seçili değil"}"
                                    role = Role.Button
                                },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .then(
                                        if (isSelected) {
                                            Modifier
                                                .border(2.dp, categoryColor, CircleShape)
                                                .padding(3.dp)
                                                .background(categoryColor.copy(alpha = 0.15f), CircleShape)
                                        } else {
                                            Modifier.background(
                                                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color(0xFFF1F5F9),
                                                shape = CircleShape,
                                            )
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = CategorySemanticIconResolver.resolve(option.key),
                                    contentDescription = null,
                                    tint = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // Seçimi Uygula Butonu
                Button(
                    onClick = {
                        onApply(selectedKey)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = "Seçimi uygula",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * 05 Renk Seçimi Modalı (Görsel 05).
 * 12 kurumsal rengi sunar, seçili renkte kontrastlı onay işareti ve halka gösterir.
 */
@Composable
fun CategoryColorSelectionDialog(
    currentColorHex: String,
    categoryName: String,
    iconKey: String?,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedHex by remember { mutableStateOf(currentColorHex) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val currentSelectedColor = ColorParser.parseHexColorOrNull(selectedHex) ?: SageGreen

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Başlık ve Geri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Renk seçimini kapat",
                        )
                    }
                    Text(
                        text = "Renk seç",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Üst Canlı Önizleme
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = currentSelectedColor.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(currentSelectedColor.copy(alpha = 0.22f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = CategorySemanticIconResolver.resolve(iconKey),
                                contentDescription = null,
                                tint = currentSelectedColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column {
                            Text(
                                text = categoryName.ifBlank { "Kategori Adı" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Bu renk kategori simgesinde kullanılacak.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 12 Renk Izgarası (4x3)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(PRESET_CATEGORY_COLORS, key = { it }) { hex ->
                        val isSelected = hex.equals(selectedHex, ignoreCase = true)
                        val color = ColorParser.parseHexColorOrNull(hex) ?: SageGreen
                        val checkTint = if (color.luminance() > 0.5f) Color.Black else Color.White

                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clickable { selectedHex = hex }
                                .semantics {
                                    contentDescription = "${getPresetColorName(hex)} rengi, ${if (isSelected) "seçili" else "seçili değil"}"
                                    role = Role.Button
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .then(
                                        if (isSelected) {
                                            Modifier
                                                .border(2.5.dp, color, CircleShape)
                                                .padding(3.dp)
                                                .background(color, CircleShape)
                                        } else {
                                            Modifier.background(color, CircleShape)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = checkTint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Seçimi Uygula Butonu
                Button(
                    onClick = {
                        onApply(selectedHex)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = "Seçimi uygula",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Form Kaydet / Oluştur Birincil Butonu (Görsel 02, 03 & 11).
 */
@Composable
fun CategorySubmitButton(
    isEditMode: Boolean,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonText = if (isEditMode) "Değişiklikleri kaydet" else "Kategoriyi oluştur"

    Button(
        onClick = onSubmit,
        enabled = canSubmit && !isSubmitting,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SageGreen,
            disabledContainerColor = SageGreen.copy(alpha = 0.4f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .semantics {
                contentDescription = if (isEditMode) "Kategori değişikliklerini kaydet" else "Yeni kategori oluştur"
            },
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Kaydediliyor...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        } else {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

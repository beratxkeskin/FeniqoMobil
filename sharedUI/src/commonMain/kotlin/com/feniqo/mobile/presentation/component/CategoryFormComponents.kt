package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.validation.CategoryValidationRules
import com.feniqo.mobile.presentation.category.CategoryFormFieldError
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.ColorParser

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
 * Kategori formunun üst başlık ve geri dönüş alanıdır.
 */
@Composable
fun CategoryFormHeader(
    title: String,
    description: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isBackEnabled: Boolean = true,
    activeWorkspaceName: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = onBack,
                enabled = isBackEnabled,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = "Kategori ekranından geri dön"
                    },
            ) {
                Text(
                    text = "← Geri",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            ActiveWorkspaceIndicator(
                workspaceName = activeWorkspaceName,
                isCompact = true,
            )
        }

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Kategori adı giriş alanıdır.
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

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name,
            onValueChange = { input ->
                onNameChanged(input.take(CategoryValidationRules.MAX_NAME_LENGTH))
            },
            label = { Text("Kategori Adı") },
            placeholder = { Text("Örn. Evcil Hayvan") },
            singleLine = true,
            enabled = enabled,
            isError = nameError != null,
            shape = RoundedCornerShape(FeniqoRadius.Medium),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
            supportingText = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = nameError?.toDisplayText() ?: "",
                        color = if (nameError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "${name.length}/${CategoryValidationRules.MAX_NAME_LENGTH}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    contentDescription = "Kategori adı metin alanı"
                },
        )
    }
}

/**
 * Kategori türü seçim bölümüdür (Create modunda değiştirilebilir, Edit modunda kilitlidir).
 */
@Composable
fun CategoryTypeSection(
    selectedType: TransactionType,
    isEditMode: Boolean,
    isTypeEditable: Boolean,
    onTypeSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Kategori Türü",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        CategoryTypeSelector(
            selectedType = selectedType,
            onTypeSelected = onTypeSelected,
            enabled = isTypeEditable,
        )

        if (isEditMode) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
            Text(
                text = "Mevcut işlemlerle tutarlılığı korumak için kategori türü değiştirilemez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Renk paleti seçici bileşenidir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryColorPicker(
    selectedColorHex: String,
    onColorChanged: (String) -> Unit,
    colorError: CategoryFormFieldError?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val normalizedSelected = selectedColorHex.trim().uppercase()
    val availableColors = if (PRESET_CATEGORY_COLORS.any { it.equals(normalizedSelected, ignoreCase = true) }) {
        PRESET_CATEGORY_COLORS
    } else {
        listOf(normalizedSelected) + PRESET_CATEGORY_COLORS
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Kategori Rengi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        ) {
            availableColors.forEach { hex ->
                val isSelected = hex.equals(normalizedSelected, ignoreCase = true)
                val parsedColor = ColorParser.parseHexColorOrNull(hex) ?: MaterialTheme.colorScheme.primary
                val checkColor = if (parsedColor.luminance() > 0.5f) Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = if (isSelected) "$hex kategori rengi, seçili" else "$hex kategori rengi, seçili değil"
                        }
                        .clickable(enabled = enabled) {
                            onColorChanged(hex)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(color = parsedColor, shape = CircleShape)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = checkColor,
                            )
                        }
                    }
                }
            }
        }

        if (colorError != null) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
            Text(
                text = colorError.toDisplayText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Simge/ikon seçici bileşenidir.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryIconPicker(
    selectedIconKey: String?,
    onIconChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val normalizedSelectedKey = selectedIconKey?.trim()?.takeIf { it.isNotBlank() }
    val availableIcons = if (normalizedSelectedKey == null || PRESET_CATEGORY_ICONS.any { it.key == normalizedSelectedKey }) {
        PRESET_CATEGORY_ICONS
    } else {
        listOf(CategoryIconOption(normalizedSelectedKey, "Mevcut ($normalizedSelectedKey)")) + PRESET_CATEGORY_ICONS
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Kategori Simgesi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
        ) {
            availableIcons.forEach { option ->
                val isSelected = option.key == normalizedSelectedKey
                FilterChip(
                    selected = isSelected,
                    onClick = { onIconChanged(option.key) },
                    label = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    enabled = enabled,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics {
                            contentDescription = if (isSelected) "${option.label} simgesi, seçili" else "${option.label} simgesi, seçili değil"
                        },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

/**
 * Form kaydet / gönder butonu bileşenidir.
 */
@Composable
fun CategorySubmitButton(
    isEditMode: Boolean,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonText = if (isEditMode) "Değişiklikleri Kaydet" else "Kategori Oluştur"

    Button(
        onClick = onSubmit,
        enabled = canSubmit && !isSubmitting,
        shape = RoundedCornerShape(FeniqoRadius.Medium),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .semantics {
                contentDescription = if (isEditMode) "Kategori değişikliklerini kaydet" else "Yeni kategoriyi oluştur"
            },
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(FeniqoSpacing.Small))
            Text(
                text = "Kaydediliyor...",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.category.CategoryFormFieldError
import com.feniqo.mobile.presentation.category.CategoryFormLoadError
import com.feniqo.mobile.presentation.category.CategoryFormUiState
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.component.CategoryColorSelectionDialog
import com.feniqo.mobile.presentation.component.CategoryFormHeader
import com.feniqo.mobile.presentation.component.CategoryIconSelectionDialog
import com.feniqo.mobile.presentation.component.CategoryLivePreviewCard
import com.feniqo.mobile.presentation.component.CategoryMessageBanner
import com.feniqo.mobile.presentation.component.CategoryNameField
import com.feniqo.mobile.presentation.component.CategorySubmitButton
import com.feniqo.mobile.presentation.component.CategoryTypeSection
import com.feniqo.mobile.presentation.component.CategoryVisualSettingsCard
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * Kategori oluşturma ve düzenleme ekranının durumsuz (stateless) Compose sunumudur.
 * Yalnızca UI state ve etkileşim callback'lerini tüketir; ViewModel veya platform bağımlılığı içermez.
 */
@Composable
fun CategoryFormScreen(
    state: CategoryFormUiState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onTypeChanged: (TransactionType) -> Unit,
    onColorChanged: (String) -> Unit,
    onIconChanged: (String?) -> Unit,
    onSubmit: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (state.isEditMode) "Kategoriyi düzenle" else "Yeni kategori"
    var showColorDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Large),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // 1. Üst Başlık ve Geri Dönüş Alanı (Görsel 02 & 03)
            CategoryFormHeader(
                title = title,
                onBack = onBack,
                isBackEnabled = !state.isSubmitting,
                activeWorkspaceName = state.activeWorkspaceName,
            )

            Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

            // 2. Durum Alanı (Initial Loading / Load Error / Form İçeriği)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.isLoadingInitialData -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = FeniqoSpacing.ExtraLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = Color(0xFF2D5A43),
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))
                            Text(
                                text = "Kategori yükleniyor...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    state.loadError != null -> {
                        // Tam Ekran Hata Kartı (Görsel 12)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = FeniqoSpacing.ExtraLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(Color(0xFFFEE2E2), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "!",
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                    }
                                    Text(
                                        text = state.loadError.toDisplayText(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Lütfen daha sonra tekrar dene.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = onBack,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D5A43)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                    ) {
                                        Text("Geri dön", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = FeniqoSpacing.Screen),
                        ) {
                            // Genel Mesaj Bannerı (varsa)
                            if (state.generalMessage != null) {
                                item(key = "general_message_banner") {
                                    CategoryMessageBanner(
                                        message = state.generalMessage,
                                        onDismiss = onDismissMessage,
                                        isDismissEnabled = !state.isSubmitting,
                                    )
                                }
                            }

                            // Canlı Önizleme Kartı (Görsel 02 & 03)
                            item(key = "category_live_preview") {
                                CategoryLivePreviewCard(
                                    name = state.name,
                                    type = state.type,
                                    colorHex = state.colorHex,
                                    iconKey = state.iconKey,
                                )
                            }

                            // Kategori Adı Alanı (Görsel 02, 03 & 11)
                            item(key = "category_name_field") {
                                CategoryNameField(
                                    name = state.name,
                                    onNameChanged = onNameChanged,
                                    nameError = state.nameError,
                                    enabled = state.isFormEnabled,
                                )
                            }

                            // Kategori Türü Seçimi (Görsel 02 & 03)
                            item(key = "category_type_section") {
                                CategoryTypeSection(
                                    selectedType = state.type,
                                    isEditMode = state.isEditMode,
                                    isTypeEditable = state.isTypeEditable,
                                    onTypeSelected = onTypeChanged,
                                )
                            }

                            // Görsel Ayarları (Renk & Simge) (Görsel 02 & 03)
                            item(key = "category_visual_settings") {
                                CategoryVisualSettingsCard(
                                    colorHex = state.colorHex,
                                    iconKey = state.iconKey,
                                    onColorClick = { showColorDialog = true },
                                    onIconClick = { showIconDialog = true },
                                    enabled = state.isFormEnabled,
                                )
                            }

                            // Form Kaydet / Oluştur Butonu (Görsel 02, 03 & 11)
                            item(key = "category_submit_button") {
                                Spacer(modifier = Modifier.height(4.dp))
                                CategorySubmitButton(
                                    isEditMode = state.isEditMode,
                                    canSubmit = state.canSubmit,
                                    isSubmitting = state.isSubmitting,
                                    onSubmit = onSubmit,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 05 Renk Seçimi Modalı (Görsel 05)
    if (showColorDialog) {
        CategoryColorSelectionDialog(
            currentColorHex = state.colorHex,
            categoryName = state.name,
            iconKey = state.iconKey,
            onApply = onColorChanged,
            onDismiss = { showColorDialog = false },
        )
    }

    // 04 Simge Seçimi Modalı (Görsel 04)
    if (showIconDialog) {
        CategoryIconSelectionDialog(
            currentIconKey = state.iconKey,
            categoryColorHex = state.colorHex,
            onApply = onIconChanged,
            onDismiss = { showIconDialog = false },
        )
    }
}

// -------------------------------------------------------------------------
// PREVIEWS
// -------------------------------------------------------------------------

@Preview(name = "Create Expense Light", showBackground = true)
@Composable
private fun CategoryFormCreateExpensePreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                type = TransactionType.EXPENSE,
                name = "",
                colorHex = "#10B981",
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Create Income Dark", showBackground = true)
@Composable
private fun CategoryFormCreateIncomePreviewDark() {
    FeniqoTheme(darkTheme = true) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                type = TransactionType.INCOME,
                name = "Yatırım Getirisi",
                colorHex = "#34D399",
                iconKey = "trending-up",
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Edit Custom Light", showBackground = true)
@Composable
private fun CategoryFormEditCustomPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                categoryId = EntityId("cat-100"),
                type = TransactionType.EXPENSE,
                name = "Evcil Hayvan",
                colorHex = "#EF4444",
                iconKey = "heart-pulse",
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Validation Error Light", showBackground = true)
@Composable
private fun CategoryFormValidationErrorPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                name = "",
                nameError = CategoryFormFieldError.NAME_REQUIRED,
                colorError = CategoryFormFieldError.COLOR_INVALID,
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Initial Loading Light", showBackground = true)
@Composable
private fun CategoryFormInitialLoadingPreviewLight() {
    FeniqoTheme(darkTheme = false) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                categoryId = EntityId("cat-100"),
                isLoadingInitialData = true,
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Load Error Dark", showBackground = true)
@Composable
private fun CategoryFormLoadErrorPreviewDark() {
    FeniqoTheme(darkTheme = true) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                categoryId = EntityId("cat-100"),
                loadError = CategoryFormLoadError.DEFAULT_CATEGORY_READ_ONLY,
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

@Preview(name = "Submitting Dark", showBackground = true)
@Composable
private fun CategoryFormSubmittingPreviewDark() {
    FeniqoTheme(darkTheme = true) {
        CategoryFormScreen(
            state = CategoryFormUiState(
                name = "Abonelik",
                colorHex = "#6366F1",
                isSubmitting = true,
            ),
            onBack = {},
            onNameChanged = {},
            onTypeChanged = {},
            onColorChanged = {},
            onIconChanged = {},
            onSubmit = {},
            onDismissMessage = {},
        )
    }
}

package com.feniqo.mobile.presentation.category

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.feniqo.mobile.App
import com.feniqo.mobile.presentation.component.CategoryColorSelectionDialog
import com.feniqo.mobile.presentation.component.CategoryIconSelectionDialog
import com.feniqo.mobile.presentation.screen.CategoryFormScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryFormLocalizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun category_form_and_typed_error_react_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                CategoryFormScreen(
                    state = CategoryFormUiState(nameError = CategoryFormFieldError.NAME_REQUIRED),
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

        composeRule.onNodeWithText("Yeni kategori").assertIsDisplayed()
        composeRule.onNodeWithText("Kategori adı").assertIsDisplayed()
        composeRule.onNodeWithText("Kategori adı boş bırakılamaz.").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("New category").assertIsDisplayed()
        composeRule.onNodeWithText("Category name").assertIsDisplayed()
        composeRule.onNodeWithText("Category name cannot be empty.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("New category, go back").assertIsDisplayed()
    }

    @Test
    fun icon_picker_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                CategoryIconSelectionDialog(
                    currentIconKey = null,
                    categoryColorHex = "#10B981",
                    onApply = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Simge seç").assertIsDisplayed()
        composeRule.onNodeWithText("İkonsuz").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("İkonsuz simgesi, seçili").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Select icon").assertIsDisplayed()
        composeRule.onNodeWithText("No icon").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("No icon icon, selected").assertIsDisplayed()
    }

    @Test
    fun color_picker_reacts_to_runtime_locale_change() {
        var languageTag by mutableStateOf("tr")

        composeRule.setContent {
            App(languageTag = languageTag) {
                CategoryColorSelectionDialog(
                    currentColorHex = "#10B981",
                    categoryName = "Market",
                    iconKey = "shopping-cart",
                    onApply = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Renk seç").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Zümrüt rengi, seçili").assertIsDisplayed()

        composeRule.runOnIdle { languageTag = "en" }

        composeRule.onNodeWithText("Select color").assertIsDisplayed()
        composeRule.onNodeWithText("This color will be used for the category icon.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Emerald color, selected").assertIsDisplayed()
    }
}

package com.feniqo.mobile.presentation.asset

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import com.feniqo.mobile.presentation.screen.AssetFormScreen
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "tr-rTR-w1080dp-h3000dp")
class AssetFormAccessibilityComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun imeActions_followVisibleTextFieldOrder_andDoneClearsFocus() {
        setAssetForm(
            state = AssetFormUiState(
                input = AssetFormInput(autoTrack = true),
            ),
        )

        val name = compose.onNodeWithContentDescription("Varlık adı")
        val currentValue = compose.onNodeWithContentDescription("Güncel toplam değer")
        val quantity = compose.onNodeWithContentDescription("Varlık miktarı")
        val purchasePrice = compose.onNodeWithContentDescription("Alış birim fiyatı")
        val trackingSymbol = compose.onNodeWithContentDescription("Piyasa sembolü")

        name.performClick()
        name.performImeAction()
        currentValue.assertIsFocused()

        currentValue.performImeAction()
        quantity.assertIsFocused()

        quantity.performImeAction()
        purchasePrice.assertIsFocused()

        purchasePrice.performImeAction()
        trackingSymbol.assertIsFocused()

        trackingSymbol.performImeAction()
        trackingSymbol.assertIsNotFocused()
    }

    @Test
    fun validation_focusesFirstInvalidField_andExposesErrorSemantics() {
        setAssetForm(
            state = AssetFormUiState(
                input = AssetFormInput(autoTrack = true),
                errors = AssetFormErrors(
                    name = AssetFormFieldError.NAME_REQUIRED,
                    currentValue = AssetFormFieldError.CURRENT_VALUE_REQUIRED,
                    quantity = AssetFormFieldError.QUANTITY_INVALID,
                    purchaseUnitPrice = AssetFormFieldError.PURCHASE_PRICE_INVALID,
                    trackingSymbol = AssetFormFieldError.TRACKING_SYMBOL_REQUIRED,
                ),
            ),
        )

        compose.onNodeWithContentDescription("Varlık adı")
            .assertIsFocused()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Error,
                    "Varlık adı zorunludur.",
                ),
            )
    }

    @Test
    fun validation_withoutNameError_focusesCurrentValue() {
        setAssetForm(
            state = AssetFormUiState(
                errors = AssetFormErrors(
                    currentValue = AssetFormFieldError.CURRENT_VALUE_REQUIRED,
                ),
            ),
        )

        compose.onNodeWithContentDescription("Güncel toplam değer").assertIsFocused()
    }

    private fun setAssetForm(state: AssetFormUiState) {
        compose.setContent {
            TestAssetForm(state)
        }
    }

    @Composable
    private fun TestAssetForm(state: AssetFormUiState) {
        FeniqoTheme {
            AssetFormScreen(
                state = state,
                onBack = {},
                onInputChange = {},
                onSubmit = {},
                onRequestDelete = {},
                onConfirmDelete = {},
                onDismissDelete = {},
            )
        }
    }
}

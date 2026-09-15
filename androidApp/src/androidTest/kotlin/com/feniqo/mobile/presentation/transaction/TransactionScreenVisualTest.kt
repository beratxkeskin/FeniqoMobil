package com.feniqo.mobile.presentation.transaction

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.feniqo.mobile.presentation.screen.*
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Gerçek Compose bileşenlerini yalnız test fixture'larıyla render eder; oturumlu kabul testi değildir. */
class TransactionScreenVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun listLight() {
        compose.setContent { TransactionsScreenListLightPreview() }
        compose.onNodeWithText("Dönem neti").assertIsDisplayed()
        capture("01-list-light")
    }

    @Test fun listDark() {
        compose.setContent { TransactionsScreenListDarkPreview() }
        compose.onNodeWithText("Dönem neti").assertIsDisplayed()
        capture("01-list-dark")
    }

    @Test fun expenseAndCategorySelection() {
        compose.setContent { TransactionFormScreenPreview_AddExpense_Light() }
        capture("04-expense")
        compose.onNodeWithText("Market").performScrollTo().performClick()
        compose.onNodeWithText("Kategori ara").assertIsDisplayed()
        capture("07-category")
    }

    @Test fun editAndDetails() {
        compose.setContent { TransactionFormScreenPreview_EditWithHistoricalCategory() }
        capture("06-edit")
        compose.onNodeWithText("Ayrıntılar").performScrollTo().performClick()
        compose.onNodeWithText("Ayrıntıları uygula").performScrollTo().assertIsDisplayed()
        capture("10-details")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "transaction-visual-tests")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

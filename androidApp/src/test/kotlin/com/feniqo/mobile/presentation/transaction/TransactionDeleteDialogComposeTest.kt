package com.feniqo.mobile.presentation.transaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.usecase.InstallmentDeleteScope
import com.feniqo.mobile.presentation.component.InstallmentTransactionDeleteDialog
import com.feniqo.mobile.presentation.component.SingleTransactionDeleteDialog
import com.feniqo.mobile.presentation.theme.FeniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h3000dp")
class TransactionDeleteDialogComposeTest {

    @get:Rule
    val compose = createComposeRule()

    // 1. SingleTransactionDeleteDialog Tests

    @Test
    fun singleDialog_displaysCategoryAndAmountAndDate() {
        val target = sampleTransaction(
            categoryName = "Eğlence",
            formattedAmount = "-₺100,00",
            transactionDate = LocalDate(2026, 9, 14),
        )

        compose.setContent {
            FeniqoTheme {
                SingleTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Single(target),
                    isDeleteInProgress = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("İşlemi sil?").assertIsDisplayed()
        compose.onNodeWithText("Eğlence").assertIsDisplayed()
        compose.onNodeWithText("-₺100,00").assertIsDisplayed()
        compose.onNodeWithText("14 Eylül 2026").assertIsDisplayed()
        compose.onNodeWithText("Bu işlem geri alınamaz.").assertIsDisplayed()
        compose.onNodeWithText("Vazgeç").assertIsDisplayed()
        compose.onNodeWithText("İşlemi sil").assertIsDisplayed()
    }

    @Test
    fun singleDialog_dismissCallsOnDismiss() {
        var dismissed = false
        val target = sampleTransaction()

        compose.setContent {
            FeniqoTheme {
                SingleTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Single(target),
                    isDeleteInProgress = false,
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText("Vazgeç").performClick()
        assertTrue("onDismiss should be called", dismissed)
    }

    @Test
    fun singleDialog_confirmCallsOnConfirm() {
        var confirmed = false
        val target = sampleTransaction()

        compose.setContent {
            FeniqoTheme {
                SingleTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Single(target),
                    isDeleteInProgress = false,
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("İşlemi sil").performClick()
        assertTrue("onConfirm should be called", confirmed)
    }

    @Test
    fun singleDialog_loading_doesNotTriggerConfirmAgain() {
        var confirmCount = 0
        val target = sampleTransaction()

        compose.setContent {
            FeniqoTheme {
                SingleTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Single(target),
                    isDeleteInProgress = true,
                    onConfirm = { confirmCount++ },
                    onDismiss = {},
                )
            }
        }

        // When loading, button shows progress and has contentDescription "İşlemi sil"
        val confirmButton = compose.onNodeWithContentDescription("İşlemi sil")
        confirmButton.assertIsDisplayed()
        confirmButton.performClick()
        assertEquals("onConfirm must not be called when isDeleteInProgress is true", 0, confirmCount)
    }

    @Test
    fun singleDialog_loading_dismissIsDisabled() {
        var dismissed = false
        val target = sampleTransaction()

        compose.setContent {
            FeniqoTheme {
                SingleTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Single(target),
                    isDeleteInProgress = true,
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText("Vazgeç").performClick()
        assertFalse("onDismiss should not be called when isDeleteInProgress is true", dismissed)
    }

    // 2. InstallmentTransactionDeleteDialog Tests

    @Test
    fun installmentDialog_defaultSelectionIsOnlyThis() {
        var confirmedScope: InstallmentDeleteScope? = null
        val target = sampleTransaction(
            categoryName = "Eğlence",
            formattedAmount = "-₺333,34",
            transactionDate = LocalDate(2026, 9, 14),
            installment = InstallmentDisplayModel(3, 3, "3 / 3"),
        )

        compose.setContent {
            FeniqoTheme {
                InstallmentTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Installment(target),
                    isDeleteInProgress = false,
                    onConfirm = { scope -> confirmedScope = scope },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Taksitli işlemi sil").assertIsDisplayed()
        compose.onNodeWithText("Eğlence").assertIsDisplayed()
        compose.onNodeWithText("-₺333,34").assertIsDisplayed()
        compose.onNodeWithText("3 / 3").assertIsDisplayed()

        // Verify default selection is ONLY_THIS
        compose.onNode(hasScopeRole("Yalnızca bu taksiti sil")).assertIsSelected()
        compose.onNode(hasScopeRole("Bu ve sonraki taksitleri sil")).assertIsNotSelected()
        compose.onNode(hasScopeRole("Tüm taksit grubunu sil")).assertIsNotSelected()

        compose.onNodeWithText("Seçilen kapsamı sil").performClick()
        assertEquals(InstallmentDeleteScope.ONLY_THIS, confirmedScope)
    }

    @Test
    fun installmentDialog_selectingSecondOption_sendsThisAndFollowing() {
        var confirmedScope: InstallmentDeleteScope? = null
        val target = sampleTransaction(installment = InstallmentDisplayModel(2, 6, "2 / 6"))

        compose.setContent {
            FeniqoTheme {
                InstallmentTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Installment(target),
                    isDeleteInProgress = false,
                    onConfirm = { scope -> confirmedScope = scope },
                    onDismiss = {},
                )
            }
        }

        // Click the second scope row
        compose.onNode(hasScopeRole("Bu ve sonraki taksitleri sil")).performClick()
        compose.onNode(hasScopeRole("Bu ve sonraki taksitleri sil")).assertIsSelected()
        compose.onNode(hasScopeRole("Yalnızca bu taksiti sil")).assertIsNotSelected()

        compose.onNodeWithText("Seçilen kapsamı sil").performClick()
        assertEquals(InstallmentDeleteScope.THIS_AND_FOLLOWING, confirmedScope)
    }

    @Test
    fun installmentDialog_selectingThirdOption_sendsAllGroup() {
        var confirmedScope: InstallmentDeleteScope? = null
        val target = sampleTransaction(installment = InstallmentDisplayModel(1, 3, "1 / 3"))

        compose.setContent {
            FeniqoTheme {
                InstallmentTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Installment(target),
                    isDeleteInProgress = false,
                    onConfirm = { scope -> confirmedScope = scope },
                    onDismiss = {},
                )
            }
        }

        // Click the third scope row
        compose.onNode(hasScopeRole("Tüm taksit grubunu sil")).performClick()
        compose.onNode(hasScopeRole("Tüm taksit grubunu sil")).assertIsSelected()
        compose.onNode(hasScopeRole("Yalnızca bu taksiti sil")).assertIsNotSelected()

        compose.onNodeWithText("Seçilen kapsamı sil").performClick()
        assertEquals(InstallmentDeleteScope.ALL_GROUP, confirmedScope)
    }

    @Test
    fun installmentDialog_entireRowIsSelectableBySubtitle() {
        val target = sampleTransaction(installment = InstallmentDisplayModel(1, 3, "1 / 3"))

        compose.setContent {
            FeniqoTheme {
                InstallmentTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Installment(target),
                    isDeleteInProgress = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        // Click via subtitle text inside the row
        compose.onNodeWithText("Seçili taksit ve bu gruptaki sonraki taksitler kaldırılır.").performClick()
        compose.onNode(hasScopeRole("Bu ve sonraki taksitleri sil")).assertIsSelected()
    }

    @Test
    fun installmentDialog_loading_selectionDoesNotChange() {
        val target = sampleTransaction(installment = InstallmentDisplayModel(1, 3, "1 / 3"))

        compose.setContent {
            FeniqoTheme {
                InstallmentTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Installment(target),
                    isDeleteInProgress = true,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        // Attempt to click second option while loading
        compose.onNode(hasScopeRole("Bu ve sonraki taksitleri sil")).performClick()
        // Selection must remain on the default ONLY_THIS
        compose.onNode(hasScopeRole("Yalnızca bu taksiti sil")).assertIsSelected()
        compose.onNode(hasScopeRole("Bu ve sonraki taksitleri sil")).assertIsNotSelected()
    }

    @Test
    fun installmentDialog_loading_doesNotSendDoubleConfirm() {
        var confirmCount = 0
        val target = sampleTransaction(installment = InstallmentDisplayModel(1, 3, "1 / 3"))

        compose.setContent {
            FeniqoTheme {
                InstallmentTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Installment(target),
                    isDeleteInProgress = true,
                    onConfirm = { confirmCount++ },
                    onDismiss = {},
                )
            }
        }

        val confirmButton = compose.onNodeWithContentDescription("Seçilen kapsamı sil")
        confirmButton.assertIsDisplayed()
        confirmButton.performClick()
        assertEquals("onConfirm must not be called when isDeleteInProgress is true", 0, confirmCount)
    }

    @Test
    fun installmentDialog_badgeComesFromModel() {
        val target = sampleTransaction(
            installment = InstallmentDisplayModel(number = 2, total = 6, badgeText = "2 / 6"),
        )

        compose.setContent {
            FeniqoTheme {
                InstallmentTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Installment(target),
                    isDeleteInProgress = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("2 / 6").assertIsDisplayed()
        compose.onNodeWithContentDescription("Taksit 2 / 6").assertIsDisplayed()
    }

    // 3. Theme & Accessibility Tests

    @Test
    fun singleDialog_rendersInLightTheme() {
        val target = sampleTransaction()

        compose.setContent {
            FeniqoTheme(darkTheme = false) {
                SingleTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Single(target),
                    isDeleteInProgress = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("İşlemi sil?").assertIsDisplayed()
        compose.onNodeWithText("Vazgeç").assertIsDisplayed()
        compose.onNodeWithText("İşlemi sil").assertIsDisplayed()
    }

    @Test
    fun installmentDialog_rendersInDarkTheme() {
        val target = sampleTransaction(installment = InstallmentDisplayModel(1, 3, "1 / 3"))

        compose.setContent {
            FeniqoTheme(darkTheme = true) {
                InstallmentTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Installment(target),
                    isDeleteInProgress = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Taksitli işlemi sil").assertIsDisplayed()
        compose.onNodeWithText("Vazgeç").assertIsDisplayed()
        compose.onNodeWithText("Seçilen kapsamı sil").assertIsDisplayed()
    }

    @Test
    fun accessibility_paneTitleAndHeadingExist() {
        val target = sampleTransaction()

        compose.setContent {
            FeniqoTheme {
                SingleTransactionDeleteDialog(
                    dialog = TransactionDeleteDialogState.Single(target),
                    isDeleteInProgress = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        // Pane title check
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "İşlemi sil"))
            .assertIsDisplayed()

        // Heading check on title
        compose.onNodeWithText("İşlemi sil?")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun accessibility_largeFont_actionsAndContentReachable() {
        val target = sampleTransaction(
            categoryName = "Çok Uzun Kategori Adı ve Ev İhtiyaçları",
            installment = InstallmentDisplayModel(1, 12, "1 / 12"),
        )

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                FeniqoTheme {
                    Box(Modifier.requiredSize(width = 320.dp, height = 480.dp)) {
                        InstallmentTransactionDeleteDialog(
                            dialog = TransactionDeleteDialogState.Installment(target),
                            isDeleteInProgress = false,
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Taksitli işlemi sil").assertIsDisplayed()
        compose.onNodeWithText("Seçilen kapsamı sil").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Vazgeç").performScrollTo().assertIsDisplayed()
    }

    private fun hasScopeRole(title: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and
            (hasText(title) or hasAnyDescendant(hasText(title)))

    private fun sampleTransaction(
        categoryName: String = "Eğlence",
        formattedAmount: String = "-₺100,00",
        transactionDate: LocalDate = LocalDate(2026, 9, 14),
        installment: InstallmentDisplayModel? = null,
    ) = TransactionDisplayModel(
        id = EntityId("11111111-1111-4111-8111-111111111111"),
        amount = Money(10_000L, Currency.TRY),
        formattedAmount = formattedAmount,
        type = TransactionType.EXPENSE,
        categoryId = EntityId("22222222-2222-4222-8222-222222222222"),
        categoryName = categoryName,
        categoryColorHex = "#9D7AE2",
        categoryIconKey = "entertainment",
        description = null,
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = transactionDate,
        installment = installment,
        hasReceipt = false,
        note = null,
    )
}

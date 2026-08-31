package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.component.formatDisplayDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionFormUiHelperTest {

    @Test
    fun formatDisplayDate_formatsTurkishMonthsCorrectly() {
        assertEquals("1 Ocak 2026", formatDisplayDate(LocalDate(2026, 1, 1)))
        assertEquals("15 Şubat 2026", formatDisplayDate(LocalDate(2026, 2, 15)))
        assertEquals("23 Ağustos 2026", formatDisplayDate(LocalDate(2026, 8, 23)))
        assertEquals("31 Aralık 2026", formatDisplayDate(LocalDate(2026, 12, 31)))
    }

    @Test
    fun transactionFormFieldError_toDisplayText_returnsTurkishMessages() {
        assertEquals("Tutar boş bırakılamaz.", TransactionFormFieldError.AMOUNT_REQUIRED.toDisplayText())
        assertEquals("Geçerli bir tutar girin.", TransactionFormFieldError.AMOUNT_INVALID.toDisplayText())
        assertEquals("Tutar sıfırdan büyük olmalıdır.", TransactionFormFieldError.AMOUNT_NON_POSITIVE.toDisplayText())
        assertEquals("Tutar izin verilen sınırı aşıyor.", TransactionFormFieldError.AMOUNT_TOO_LARGE.toDisplayText())
        assertEquals("Lütfen bir kategori seçin.", TransactionFormFieldError.CATEGORY_REQUIRED.toDisplayText())
        assertEquals("Bu kategori artık kullanılamıyor. Lütfen başka bir kategori seçin.", TransactionFormFieldError.CATEGORY_UNAVAILABLE.toDisplayText())
        assertEquals("Lütfen bir tarih seçin.", TransactionFormFieldError.DATE_REQUIRED.toDisplayText())
        assertEquals("İşlem tarihi bugünden ileri olamaz.", TransactionFormFieldError.DATE_IN_FUTURE.toDisplayText())
        assertEquals("Açıklama 500 karakterden uzun olamaz.", TransactionFormFieldError.DESCRIPTION_TOO_LONG.toDisplayText())
        assertEquals("Taksit sayısı 2 ile 60 arasında olmalıdır.", TransactionFormFieldError.INSTALLMENT_COUNT_INVALID.toDisplayText())
        assertEquals("Toplam tutar seçilen taksit sayısı için çok küçük.", TransactionFormFieldError.INSTALLMENT_AMOUNT_TOO_SMALL.toDisplayText())
    }

    @Test
    fun isInstallmentOptionAvailable_onlyTrueForNewExpenseWithCreditCard() {
        // 1. Yeni gider + kredi kartı -> true
        val state1 = TransactionFormUiState(
            isEditMode = false,
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT_CARD,
        )
        assertTrue(state1.isInstallmentOptionAvailable)

        // 2. Düzenleme modu -> false
        val state2 = TransactionFormUiState(
            isEditMode = true,
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT_CARD,
        )
        assertFalse(state2.isInstallmentOptionAvailable)

        // 3. Gelir -> false
        val state3 = TransactionFormUiState(
            isEditMode = false,
            type = TransactionType.INCOME,
            paymentMethod = PaymentMethod.CREDIT_CARD,
        )
        assertFalse(state3.isInstallmentOptionAvailable)

        // 4. Nakit -> false
        val state4 = TransactionFormUiState(
            isEditMode = false,
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CASH,
        )
        assertFalse(state4.isInstallmentOptionAvailable)
    }
}

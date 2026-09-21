package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomSplitUiHelperTest {

    private val user1 = EntityId("user_1")
    private val user2 = EntityId("user_2")
    private val user3 = EntityId("user_3")
    private val currency = Currency.TRY

    @Test
    fun parseShare_emptyInput_returnsRequiredError() {
        val (minor, error) = CustomSplitUiHelper.parseShare("", isPayer = false, currency = currency)
        assertNull(minor)
        assertEquals(TransactionFormFieldError.SPLIT_CUSTOM_SHARE_REQUIRED, error)

        val (wsMinor, wsError) = CustomSplitUiHelper.parseShare("   ", isPayer = true, currency = currency)
        assertNull(wsMinor)
        assertEquals(TransactionFormFieldError.SPLIT_CUSTOM_SHARE_REQUIRED, wsError)
    }

    @Test
    fun parseShare_zeroInput_allowedOnlyForPayer() {
        val (payerZero, payerError) = CustomSplitUiHelper.parseShare("0", isPayer = true, currency = currency)
        assertEquals(0L, payerZero)
        assertNull(payerError)

        val (payerZeroFormatted, payerFmtError) = CustomSplitUiHelper.parseShare("0,00", isPayer = true, currency = currency)
        assertEquals(0L, payerZeroFormatted)
        assertNull(payerFmtError)

        val (nonPayerZero, nonPayerError) = CustomSplitUiHelper.parseShare("0", isPayer = false, currency = currency)
        assertEquals(0L, nonPayerZero)
        assertEquals(TransactionFormFieldError.SPLIT_CUSTOM_NON_PAYER_ZERO_SHARE_NOT_ALLOWED, nonPayerError)

        val (nonPayerZeroFormatted, nonPayerFmtError) = CustomSplitUiHelper.parseShare("0,00", isPayer = false, currency = currency)
        assertEquals(0L, nonPayerZeroFormatted)
        assertEquals(TransactionFormFieldError.SPLIT_CUSTOM_NON_PAYER_ZERO_SHARE_NOT_ALLOWED, nonPayerFmtError)
    }

    @Test
    fun parseShare_validPositiveInput_parsesCorrectly() {
        val (result, error) = CustomSplitUiHelper.parseShare("125,50", isPayer = false, currency = currency)
        assertEquals(12550L, result)
        assertNull(error)

        val (dotResult, dotError) = CustomSplitUiHelper.parseShare("125.50", isPayer = true, currency = currency)
        assertEquals(12550L, dotResult)
        assertNull(dotError)
    }

    @Test
    fun parseShare_invalidFormat_returnsInvalidError() {
        val (letters, err1) = CustomSplitUiHelper.parseShare("abc", isPayer = false, currency = currency)
        assertNull(letters)
        assertEquals(TransactionFormFieldError.SPLIT_CUSTOM_SHARE_INVALID, err1)

        val (multipleDots, err2) = CustomSplitUiHelper.parseShare("12.34.56", isPayer = true, currency = currency)
        assertNull(multipleDots)
        assertEquals(TransactionFormFieldError.SPLIT_CUSTOM_SHARE_INVALID, err2)
    }

    @Test
    fun calculateSplitSummary_balanced_under_and_over_distribution() {
        val participants = setOf(user1, user2)

        // 1. Balanced
        val balancedSummary = CustomSplitUiHelper.calculateSplitSummary(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = mapOf(user1 to "40,00", user2 to "60,00"),
        )
        assertEquals(10000L, balancedSummary.totalAmountMinor)
        assertEquals(10000L, balancedSummary.distributedAmountMinor)
        assertEquals(0L, balancedSummary.remainingAmountMinor)
        assertTrue(balancedSummary.isBalanced)
        assertFalse(balancedSummary.hasExcess)
        assertFalse(balancedSummary.isOverflow)

        // 2. Under-distributed
        val underSummary = CustomSplitUiHelper.calculateSplitSummary(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = mapOf(user1 to "40,00", user2 to "30,00"),
        )
        assertEquals(10000L, underSummary.totalAmountMinor)
        assertEquals(7000L, underSummary.distributedAmountMinor)
        assertEquals(3000L, underSummary.remainingAmountMinor)
        assertFalse(underSummary.isBalanced)
        assertFalse(underSummary.hasExcess)

        // 3. Over-distributed (excess)
        val overSummary = CustomSplitUiHelper.calculateSplitSummary(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = mapOf(user1 to "70,00", user2 to "50,00"),
        )
        assertEquals(10000L, overSummary.totalAmountMinor)
        assertEquals(12000L, overSummary.distributedAmountMinor)
        assertEquals(-2000L, overSummary.remainingAmountMinor)
        assertFalse(overSummary.isBalanced)
        assertTrue(overSummary.hasExcess)
    }

    @Test
    fun applyPayerRemainder_computesRemainderAndUpdatesPayerShare() {
        val participants = setOf(user1, user2, user3)
        val shares = mapOf(
            user2 to "30,00",
            user3 to "25,50",
        )

        // user1 is payer, total is 100,00 -> others sum is 55,50 -> user1 gets 44,50
        val updated = CustomSplitUiHelper.applyPayerRemainder(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = shares,
        )
        assertEquals("44,5", updated[user1])
        assertEquals("30,00", updated[user2])
        assertEquals("25,50", updated[user3])
    }

    @Test
    fun applyPayerRemainder_allowsZeroForPayer_whenOthersSumEqualsTotal() {
        val participants = setOf(user1, user2)
        val shares = mapOf(
            user2 to "100,00",
        )

        val updated = CustomSplitUiHelper.applyPayerRemainder(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = shares,
        )
        assertEquals("0", updated[user1])
    }

    @Test
    fun applyPayerRemainder_blocksAction_whenOtherSharesExceedTotalOrInvalid() {
        val participants = setOf(user1, user2)

        // Others exceed total -> remainder is negative -> leaves shares untouched
        val excessShares = mapOf(user2 to "150,00")
        val resultExcess = CustomSplitUiHelper.applyPayerRemainder(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = excessShares,
        )
        assertEquals(excessShares, resultExcess)

        // Other share is blank/missing
        val emptyShares = mapOf<EntityId, String>()
        val resultEmpty = CustomSplitUiHelper.applyPayerRemainder(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = emptyShares,
        )
        assertEquals(emptyShares, resultEmpty)

        // Invalid total amount
        val invalidTotalShares = mapOf(user2 to "50,00")
        val resultInvalidTotal = CustomSplitUiHelper.applyPayerRemainder(
            amountText = "invalid",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = invalidTotalShares,
        )
        assertEquals(invalidTotalShares, resultInvalidTotal)
    }

    @Test
    fun calculateSplitSummary_twoValidSharesExceedingMoneyMaxAmount_marksMoneyMaxExceededWithoutLongOverflow() {
        val participants = setOf(user1, user2)
        // 5.000.000.000.000,00 TRY = 500_000_000_000_000L (her biri Money.MAX_AMOUNT_MINOR olan 922.337.203.685.477'den küçük ve geçerlidir)
        // Ancak ikisinin toplamı 1.000.000.000.000.000L olur ve Money.MAX_AMOUNT_MINOR sınırını aşar.
        val shareText = "5000000000000"
        val summary = CustomSplitUiHelper.calculateSplitSummary(
            amountText = "10000000000000",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = mapOf(user1 to shareText, user2 to shareText),
        )

        assertTrue(summary.isMoneyMaxExceeded, "İki payın toplamı Money.MAX_AMOUNT_MINOR sınırını aştığı için isMoneyMaxExceeded true olmalı")
        assertFalse(summary.isLongOverflow, "Toplam Long sınırını aşmadığı için isLongOverflow false olmalı")
        assertFalse(summary.isBalanced, "Para sınırı aşıldığında isBalanced kesinlikle false olmalı")
        assertEquals(1_000_000_000_000_000L, summary.distributedAmountMinor)
    }

    @Test
    fun calculateSplitSummary_isBalanced_strictlyRequiresAllParticipantsValid() {
        val participants = setOf(user1, user2)

        // 1. user2'nin payı boş bırakılmışken toplam tutar eşleşse dahi isBalanced false olmalı
        val summaryEmptyShare = CustomSplitUiHelper.calculateSplitSummary(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = mapOf(user1 to "100,00", user2 to ""),
        )
        assertFalse(summaryEmptyShare.isBalanced)
        assertTrue(summaryEmptyShare.hasInvalidShares)

        // 2. user2'nin (non-payer) payı 0 iken isBalanced false olmalı
        val summaryNonPayerZero = CustomSplitUiHelper.calculateSplitSummary(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = mapOf(user1 to "100,00", user2 to "0"),
        )
        assertFalse(summaryNonPayerZero.isBalanced)
        assertTrue(summaryNonPayerZero.hasInvalidShares)

        // 3. user1 (payer) 0, user2 100 iken geçerli ve dengeli olmalı
        val summaryPayerZero = CustomSplitUiHelper.calculateSplitSummary(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = mapOf(user1 to "0", user2 to "100,00"),
        )
        assertTrue(summaryPayerZero.isBalanced)
        assertFalse(summaryPayerZero.hasInvalidShares)
    }

    @Test
    fun validateDraftParticipantShares_immediatelyUpdatesZeroShareErrorWhenPayerChanges() {
        val participants = setOf(user1, user2)
        val shares = mapOf(
            user1 to "0",
            user2 to "100,00",
        )

        // Durum A: user1 ödeyen. user1 için 0 ₺ geçerli, user2 için 100 ₺ geçerli. Hata olmamalı.
        val errorsWithUser1AsPayer = CustomSplitUiHelper.validateDraftParticipantShares(
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = shares,
        )
        assertNull(errorsWithUser1AsPayer[user1])
        assertNull(errorsWithUser1AsPayer[user2])

        // Durum B: Ödeyen user2 olarak değiştirildi. Eski ödeyen user1 artık ödeyen değil ve payı 0!
        // Hemen SPLIT_CUSTOM_NON_PAYER_ZERO_SHARE_NOT_ALLOWED üretmeli.
        val errorsWithUser2AsPayer = CustomSplitUiHelper.validateDraftParticipantShares(
            currency = currency,
            payer = user2,
            participants = participants,
            customSharesText = shares,
        )
        assertEquals(TransactionFormFieldError.SPLIT_CUSTOM_NON_PAYER_ZERO_SHARE_NOT_ALLOWED, errorsWithUser2AsPayer[user1])
        assertNull(errorsWithUser2AsPayer[user2])
    }

    @Test
    fun calculateSplitSummary_whenParticipantInactive_disablesIsBalancedAndCanApplyPayerRemainder() {
        val participants = setOf(user1, user2)
        val shares = mapOf(
            user1 to "40,00",
            user2 to "60,00",
        )

        // 1. İki üye de aktifken: dengeli ve üyelik hatası yok
        val summaryBothActive = CustomSplitUiHelper.calculateSplitSummary(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = shares,
            activeMembers = setOf(user1, user2),
        )
        assertTrue(summaryBothActive.isBalanced)
        assertFalse(summaryBothActive.hasInactiveMember)
        assertNull(summaryBothActive.inactiveMemberMessage)

        // 2. Tutarlar ve seçimler birebir aynı, yalnız user2 çalışma alanından ayrıldı
        val summaryParticipantInactive = CustomSplitUiHelper.calculateSplitSummary(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = shares,
            activeMembers = setOf(user1),
        )
        // Başarı rozeti kaybolmalı
        assertFalse(summaryParticipantInactive.isBalanced)
        assertTrue(summaryParticipantInactive.hasInactiveMember)
        assertNotNull(summaryParticipantInactive.inactiveMemberMessage)
        // Kalan eylemi engellenmeli
        assertFalse(summaryParticipantInactive.canApplyPayerRemainder)
    }

    @Test
    fun calculateSplitSummary_whenPayerInactive_disablesIsBalancedAndCanApplyPayerRemainder() {
        val participants = setOf(user1, user2)
        val shares = mapOf(
            user1 to "40,00",
            user2 to "60,00",
        )

        // Tutarlar ve seçimler birebir aynı, yalnız harcamayı ödeyen (user1) çalışma alanından ayrıldı
        val summaryPayerInactive = CustomSplitUiHelper.calculateSplitSummary(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = shares,
            activeMembers = setOf(user2),
        )
        // Başarı rozeti kaybolmalı
        assertFalse(summaryPayerInactive.isBalanced)
        assertTrue(summaryPayerInactive.hasInactiveMember)
        assertNotNull(summaryPayerInactive.inactiveMemberMessage)
        // Kalan eylemi engellenmeli
        assertFalse(summaryPayerInactive.canApplyPayerRemainder)
    }

    @Test
    fun applyPayerRemainder_whenMembershipChanges_blocksRemainderApplication() {
        val participants = setOf(user1, user2)
        val shares = mapOf(
            user1 to "20,00",
            user2 to "50,00",
        )

        // 1. İki üye de aktifken: kalan 30 ₺ ödeyene (user1) aktarılabilmeli -> 50,00 ₺ olmalı
        val updatedBothActive = CustomSplitUiHelper.applyPayerRemainder(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = shares,
            activeMembers = setOf(user1, user2),
        )
        assertEquals("50", updatedBothActive[user1])

        // 2. Yalnızca user2 ayrıldığında: tutarlar aynı olsa da kalan eylemi engellenmeli ve paylar değişmemeli
        val updatedParticipantInactive = CustomSplitUiHelper.applyPayerRemainder(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = shares,
            activeMembers = setOf(user1),
        )
        assertEquals("20,00", updatedParticipantInactive[user1])
        assertEquals(shares, updatedParticipantInactive)

        // 3. Yalnızca user1 (ödeyen) ayrıldığında: paylar değişmemeli
        val updatedPayerInactive = CustomSplitUiHelper.applyPayerRemainder(
            amountText = "100,00",
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = shares,
            activeMembers = setOf(user2),
        )
        assertEquals("20,00", updatedPayerInactive[user1])
        assertEquals(shares, updatedPayerInactive)
    }

    @Test
    fun validateDraftParticipantShares_whenMemberInactive_returnsMemberNotActiveError() {
        val participants = setOf(user1, user2)
        val shares = mapOf(
            user1 to "40,00",
            user2 to "60,00",
        )

        // user2 inaktif
        val errors = CustomSplitUiHelper.validateDraftParticipantShares(
            currency = currency,
            payer = user1,
            participants = participants,
            customSharesText = shares,
            activeMembers = setOf(user1),
        )
        assertEquals(TransactionFormFieldError.SPLIT_CUSTOM_MEMBER_NOT_ACTIVE, errors[user2])
        assertNull(errors[user1])
    }
}

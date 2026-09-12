package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.OcrCandidateConfidence
import com.feniqo.mobile.domain.model.ReceiptOcrResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReceiptOcrParserTest {
    @Test
    fun labeledTurkishReceipt_extractsCandidatesWithoutCreatingTransaction() {
        val result = ReceiptOcrParser.parse(
            recognizedText = """
                FENIQO MARKET
                Tarih: 09.09.2026 Saat: 18:30
                KDV 18,00
                GENEL TOPLAM 1.234,56 TL
            """.trimIndent(),
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals("FENIQO MARKET", draft.merchantName?.value)
        assertEquals(123_456L, draft.total?.value?.amountMinor)
        assertEquals(Currency.TRY, draft.total?.value?.currency)
        assertEquals(OcrCandidateConfidence.HIGH, draft.total?.confidence)
        assertEquals(LocalDate(2026, 9, 9), draft.transactionDate?.value)
    }

    @Test
    fun currencyIsProvidedByFormContext_notInferredFromReceiptSymbol() {
        val result = ReceiptOcrParser.parse("TOTAL 12.50 USD", Currency.EUR)

        val total = assertIs<ReceiptOcrResult.Candidates>(result).draft.total
        assertEquals(Currency.EUR, total?.value?.currency)
        assertEquals(1_250L, total?.value?.amountMinor)
    }

    @Test
    fun invalidDateAndUnlabeledAmount_areNotTrustedAsCandidates() {
        val result = ReceiptOcrParser.parse("TARİH 31.02.2026\nÜRÜN 999,99", Currency.TRY)

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.transactionDate)
        assertNull(draft.total)
    }

    @Test
    fun zeroAndOverflowingTotals_areRejected() {
        val zero = assertIs<ReceiptOcrResult.Candidates>(
            ReceiptOcrParser.parse("MARKET\nTOPLAM 0,00", Currency.TRY),
        )
        val overflow = assertIs<ReceiptOcrResult.Candidates>(
            ReceiptOcrParser.parse("MARKET\nTOPLAM 999999999999999999999,99", Currency.TRY),
        )

        assertNull(zero.draft.total)
        assertNull(overflow.draft.total)
    }

    @Test
    fun oversizedInput_isRejectedBeforeParsing() {
        assertIs<ReceiptOcrResult.InputTooLarge>(
            ReceiptOcrParser.parse("A".repeat(50_001), Currency.TRY),
        )
    }

    @Test
    fun blankInput_hasNoCandidates() {
        assertIs<ReceiptOcrResult.NoCandidates>(ReceiptOcrParser.parse("  \n ", Currency.TRY))
    }
}

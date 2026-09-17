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

    // --- POSITIVE SCENARIOS (K1 - K4) ---

    @Test
    fun positive_multilineStrictNextLineAmount_isExtractedWithMediumConfidence() {
        val result = ReceiptOcrParser.parse(
            recognizedText = """
                ÖRNEK MARKET
                TARİH: 12.09.2026
                TOPLAM
                *154,20
            """.trimIndent(),
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(15_420L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.MEDIUM, draft.total?.confidence)
    }

    @Test
    fun positive_primaryLabelAsciiNormalized_isExtractedWithHighConfidence() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "RESTORAN VE KAFE\nODENECEK TUTAR: 420,00 TL",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(42_000L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.HIGH, draft.total?.confidence)
    }

    @Test
    fun positive_primaryLabelMultiline_isExtractedWithMediumConfidence() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "GENEL TOPLAM\n1.234,56 TL",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(123_456L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.MEDIUM, draft.total?.confidence)
    }

    @Test
    fun positive_singleAllowedSeparatorBridged() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "TOPLAM\n*\n154,20",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(15_420L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.MEDIUM, draft.total?.confidence)
    }

    @Test
    fun positive_unitPriceMultiplication_doesNotDistortTotal() {
        val result = ReceiptOcrParser.parse(
            recognizedText = """
                KAHVE DÜNYASI
                2 ADET X 65,00 = 130,00
                TOPLAM 130,00 TL
            """.trimIndent(),
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(13_000L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.MEDIUM, draft.total?.confidence)
    }

    @Test
    fun positive_itemCountOnTotalLine_isNotPickedAsAmount() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "HIZLI MARKET\nTOPLAM 145,50 TL (2 ADET)",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(14_550L, draft.total?.value?.amountMinor)
    }

    @Test
    fun positive_turkishThousandGrouping_parsedAsExpectedKurusPolicy() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MOBİLYA MAĞAZASI\nGENEL TOPLAM 1.234 TL",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(123_400L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.HIGH, draft.total?.confidence)
    }

    @Test
    fun positive_cardPaymentRepeatDoesNotRaiseMediumConfidence() {
        val result = ReceiptOcrParser.parse(
            recognizedText = """
                MARKET
                TOPLAM 100,00
                KREDİ KARTI 100,00
            """.trimIndent(),
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(10_000L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.MEDIUM, draft.total?.confidence)
    }

    // --- NEGATIVE SCENARIOS & FAIL-CLOSED CHECKS (K5 - K9) ---

    @Test
    fun negative_taxTotal_isNeverPickedAsReceiptTotal() {
        val result = ReceiptOcrParser.parse(
            recognizedText = """
                GIDA PAZARLAMA
                TOPLAM 350,00
                KDV %10 31,82
                TOPLAM KDV 31,82
                NAKİT 400,00
                PARA ÜSTÜ 50,00
            """.trimIndent(),
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(35_000L, draft.total?.value?.amountMinor)
    }

    @Test
    fun negative_subtotalAndDiscountOnly_doesNotInferTotal() {
        val result = ReceiptOcrParser.parse(
            recognizedText = """
                KIRTASİYE LTD.
                ARA TOPLAM 200,00
                İNDİRİM 50,00
                NET 150,00
            """.trimIndent(),
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_conflictingEqualRankTotals_returnsNullTotal() {
        val result = ReceiptOcrParser.parse(
            recognizedText = """
                MAĞAZA A.Ş.
                TOPLAM 100,00
                TOPLAM 250,00
            """.trimIndent(),
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_conflictingPrimaryAndSecondaryTotals_returnsNullTotal() {
        val result = ReceiptOcrParser.parse(
            recognizedText = """
                SÜPERMARKET
                GENEL TOPLAM 100,00
                TOPLAM 80,00
            """.trimIndent(),
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_multipleAmountsOnSameTotalLine_returnsNullTotal() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM 100,00 250,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_negativeAmount_isRejected() {
        val result = ReceiptOcrParser.parse("MARKET\nTOPLAM -150,00", Currency.TRY)

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_brokenOrOverflowToken_isNotTruncatedIntoValidAmount() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM 999999999999999999999,99",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_malformedFormats_areRejected() {
        val malformed1 = ReceiptOcrParser.parse("MARKET\nTOPLAM 1.23.4,56", Currency.TRY)
        val malformed2 = ReceiptOcrParser.parse("MARKET\nTOPLAM 12,3456", Currency.TRY)
        val malformed3 = ReceiptOcrParser.parse("MARKET\nTOPLAM 150,00,00", Currency.TRY)

        assertNull(assertIs<ReceiptOcrResult.Candidates>(malformed1).draft.total)
        assertNull(assertIs<ReceiptOcrResult.Candidates>(malformed2).draft.total)
        assertNull(assertIs<ReceiptOcrResult.Candidates>(malformed3).draft.total)
    }

    @Test
    fun negative_multilineDoesNotSkipProductOrMetadataLine() {
        val result = ReceiptOcrParser.parse(
            recognizedText = """
                BÜFE
                TOPLAM
                1 EKMEK 10,00
                50,00
            """.trimIndent(),
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_multilineDoesNotPickTimeOrReceiptNo() {
        val result = ReceiptOcrParser.parse(
            recognizedText = """
                BÜFE
                TOPLAM
                SAAT: 14:30
                FİŞ NO: 0042
            """.trimIndent(),
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_multilineDoesNotBridgeMultipleSeparatorOrEmptyLines() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM\n\n*\n154,20",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_poisonedAmountOnTotalLine_doesNotFallThroughToNextLine() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM -150,00\n150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_noOtherCandidatesAndNoTotal_returnsNoCandidatesResult() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "TOPLAM 100,00 250,00",
            currency = Currency.TRY,
        )

        assertIs<ReceiptOcrResult.NoCandidates>(result)
    }

    @Test
    fun negative_wordContainingTotal_isNotTreatedAsTotalLabel() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "PETROL OFİSİ\nÜRÜN: OTO TOTAL YAĞ 250,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_totalItemCount_isNotTreatedAsAmount() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "GİYİM MAĞAZASI\nTOPLAM ÜRÜN ADEDİ: 5",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    // --- AUDIT REGRESSION TESTS (CODEX ROUND 2) ---

    @Test
    fun positive_ungroupedDecimalAmount_isExtractedWithHighConfidence() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nGENEL TOPLAM 1234,56",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(123_456L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.HIGH, draft.total?.confidence)
    }

    @Test
    fun positive_ungroupedFiveDigitDecimalAmount_isExtractedWithMediumConfidence() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM 12345,67",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(1_234_567L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.MEDIUM, draft.total?.confidence)
    }

    @Test
    fun negative_zeroAmountOnTotalLine_doesNotFallThroughToNextLine() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM 0\n150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_overflowAmountOnTotalLine_doesNotFallThroughToNextLine() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM 999999999999999999999\n150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun positive_attachedColonAmount_isExtractedCorrectly() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM:150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(15_000L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.MEDIUM, draft.total?.confidence)
    }

    @Test
    fun positive_attachedColonAmountPrimary_isExtractedWithHighConfidence() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nGENEL TOPLAM:150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(15_000L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.HIGH, draft.total?.confidence)
    }

    @Test
    fun positive_attachedColonDoesNotFallThroughToNextLine() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM:150,00\n200,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(15_000L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.MEDIUM, draft.total?.confidence)
    }

    @Test
    fun negative_midSentenceTotalInProductDescription_isNotTreatedAsTotalLabel() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nOTO TOTAL YAĞ 250,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_spacedMinusAmount_isRejected() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM - 150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_unicodeMinusAmount_isRejected() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nGENEL TOPLAM −150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    // --- AUDIT REGRESSION TESTS (CODEX ROUND 3 - DAR DÜZELTME) ---

    @Test
    fun negative_separatedAsciiMinusOnItsOwnLine_isNotTreatedAsAllowedSeparator() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM\n-\n150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_separatedUnicodeMinusOnItsOwnLine_isNotTreatedAsAllowedSeparator() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM\n−\n150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_separatedEnDashOnItsOwnLine_isNotTreatedAsAllowedSeparator() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM\n–\n150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_separatedEmDashOnItsOwnLine_isNotTreatedAsAllowedSeparator() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM\n—\n150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_embeddedLiraSymbol_isRejected() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM 12₺34",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_embeddedEuroSymbol_isRejected() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM 12€34",
            currency = Currency.EUR,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun negative_embeddedDollarSymbol_isRejected() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM 12$34",
            currency = Currency.USD,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }

    @Test
    fun positive_prefixLiraSymbol_isExtracted() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM ₺150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(15_000L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.MEDIUM, draft.total?.confidence)
    }

    @Test
    fun positive_postfixLiraSymbol_isExtracted() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM 150,00₺",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertEquals(15_000L, draft.total?.value?.amountMinor)
        assertEquals(OcrCandidateConfidence.MEDIUM, draft.total?.confidence)
    }

    @Test
    fun negative_brokenCurrencySymbolTokenDoesNotFallThroughToNextLine() {
        val result = ReceiptOcrParser.parse(
            recognizedText = "MARKET\nTOPLAM 12₺34\n150,00",
            currency = Currency.TRY,
        )

        val draft = assertIs<ReceiptOcrResult.Candidates>(result).draft
        assertNull(draft.total)
    }
}

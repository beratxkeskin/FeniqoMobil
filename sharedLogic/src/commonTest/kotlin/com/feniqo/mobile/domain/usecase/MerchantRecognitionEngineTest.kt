package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantAlias
import com.feniqo.mobile.domain.model.MerchantAliasType
import com.feniqo.mobile.domain.model.MerchantConfidenceBand
import com.feniqo.mobile.domain.model.MerchantMatchSource
import com.feniqo.mobile.domain.model.MerchantMatchStatus
import com.feniqo.mobile.domain.model.MerchantRecognitionRequest
import com.feniqo.mobile.domain.model.TransactionClassification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MerchantRecognitionEngineTest {
    private val engine = MerchantRecognitionEngine()

    @Test
    fun normalizer_isDeterministicAndTurkishCompatible() {
        assertEquals(
            "TRENDYOL YEMEK ISTANBUL SUBESI",
            TurkishDescriptionNormalizer.normalize("  trendyol-yemek / İstanbul şubesi  "),
        )
    }

    @Test
    fun exactAlias_usesApprovedSystemConfidence() {
        val result = recognize("Starbucks")
        assertEquals("starbucks", result.merchant?.id)
        assertEquals(95, result.confidence)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, result.source)
        assertEquals(MerchantConfidenceBand.VERIFIED, result.confidenceBand)
        assertTrue(result.canShowMerchantLogo)
    }

    @Test
    fun specializedAlias_winsOverGeneralAlias() {
        assertEquals("trendyol_yemek", recognize("POS TRENDYOL YEMEK ISTANBUL").merchant?.id)
        assertEquals("amazon_prime", recognize("AMAZON PRIME 1234").merchant?.id)
        assertEquals("getiryemek", recognize("GETIRYEMEK SIPARIS").merchant?.id)
    }

    @Test
    fun shortAlias_isLowConfidenceAndLogoIsHidden() {
        val result = recognize("POS THY 1234")
        assertEquals("thy", result.merchant?.id)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, result.status)
        assertEquals(65, result.confidence)
        assertEquals(MerchantConfidenceBand.MEDIUM, result.confidenceBand)
        assertFalse(result.canShowMerchantLogo)
    }

    @Test
    fun equalCandidates_areReportedAsAmbiguous() {
        val catalog = listOf(
            testMerchant("one", "ACME"),
            testMerchant("two", "ACME"),
        )
        val result = MerchantRecognitionEngine(catalog).recognize(MerchantRecognitionRequest("POS ACME 42"))
        assertEquals(MerchantMatchStatus.AMBIGUOUS, result.status)
        assertNull(result.merchant)
        assertEquals(0, result.confidence)
    }

    @Test
    fun candidatesLessThanTenPointsApart_areReportedAsAmbiguous() {
        val catalog = listOf(
            testMerchant("branch", "ACME STORE", MerchantAliasType.BRANCH_OR_POS_PATTERN),
            testMerchant("brand", "ACME STORE", MerchantAliasType.STRONG_BRAND),
        )
        val result = MerchantRecognitionEngine(catalog).recognize(MerchantRecognitionRequest("POS ACME STORE 42"))
        assertEquals(MerchantMatchStatus.AMBIGUOUS, result.status)
        assertNull(result.merchant)
    }

    @Test
    fun tenPointLead_isDecisive() {
        val catalog = listOf(
            testMerchant("service", "ACME STORE", MerchantAliasType.BRAND_AND_SERVICE),
            testMerchant("brand", "ACME STORE", MerchantAliasType.STRONG_BRAND),
        )
        val result = MerchantRecognitionEngine(catalog).recognize(MerchantRecognitionRequest("POS ACME STORE 42"))
        assertEquals("service", result.merchant?.id)
        assertEquals(90, result.confidence)
    }

    @Test
    fun negativeAlias_removesOtherwiseMatchingCandidate() {
        val result = recognize("MIGROS HEMEN SIPARIS")
        assertEquals(MerchantMatchStatus.NO_MATCH, result.status)
        assertNull(result.merchant)
    }

    @Test
    fun refund_isClassifiedButMerchantCanStillMatch() {
        val result = recognize("NETFLIX IADE")
        assertEquals(TransactionClassification.REFUND, result.transactionClassification)
        assertEquals("netflix", result.merchant?.id)
    }

    @Test
    fun personalTransfersAndSystemMovements_areExcluded() {
        listOf(
            "FAST ALI VELI" to TransactionClassification.PERSONAL_TRANSFER,
            "MAAS ODEMESI ACME" to TransactionClassification.SALARY,
            "KREDI KARTI ODEMESI" to TransactionClassification.CREDIT_CARD_PAYMENT,
            "HESAPLAR ARASI VIRMAN" to TransactionClassification.INTERNAL_TRANSFER,
            "BIRIKIM HESABINA AKTARIM" to TransactionClassification.INVESTMENT_TRANSFER,
            "ATM NAKIT CEKIM STARBUCKS" to TransactionClassification.CASH_WITHDRAWAL,
            "ATM NAKIT YATIRMA" to TransactionClassification.CASH_DEPOSIT,
            "BASLANGIC BAKIYESI DUZELTMESI" to TransactionClassification.BALANCE_ADJUSTMENT,
        ).forEach { (description, classification) ->
            val result = recognize(description)
            assertEquals(MerchantMatchStatus.EXCLUDED_TRANSACTION, result.status)
            assertEquals(classification, result.transactionClassification)
            assertNull(result.merchant)
        }
    }

    @Test
    fun verifiedCorrection_precedesSystemPrediction() {
        val result = engine.recognize(MerchantRecognitionRequest("POS MIGROS", verifiedMerchantId = "shell"))
        assertEquals("shell", result.merchant?.id)
        assertEquals(MerchantMatchSource.USER_VERIFIED, result.source)
        assertEquals(100, result.confidence)
    }

    @Test
    fun verifiedSources_followPersonalWorkspaceBankPriority() {
        val personal = engine.recognize(
            MerchantRecognitionRequest(
                rawDescription = "POS MIGROS",
                verifiedMerchantId = "shell",
                workspaceVerifiedMerchantId = "spotify",
                bankVerifiedMerchantId = "netflix",
            ),
        )
        assertEquals("shell", personal.merchant?.id)
        assertEquals(MerchantMatchSource.USER_VERIFIED, personal.source)

        val workspace = engine.recognize(
            MerchantRecognitionRequest(
                rawDescription = "POS MIGROS",
                workspaceVerifiedMerchantId = "spotify",
                bankVerifiedMerchantId = "netflix",
            ),
        )
        assertEquals("spotify", workspace.merchant?.id)
        assertEquals(MerchantMatchSource.WORKSPACE_VERIFIED, workspace.source)

        val bank = engine.recognize(
            MerchantRecognitionRequest(rawDescription = "POS MIGROS", bankVerifiedMerchantId = "netflix"),
        )
        assertEquals("netflix", bank.merchant?.id)
        assertEquals(98, bank.confidence)
        assertEquals(MerchantMatchSource.BANK_VERIFIED_ID, bank.source)
    }

    @Test
    fun unknownDescription_returnsNoMatchAndPreservesRawValue() {
        val raw = "  TANIMSIZ ISLEM #42  "
        val result = recognize(raw)
        assertEquals(MerchantMatchStatus.NO_MATCH, result.status)
        assertNull(result.merchant)
        assertEquals(raw, result.rawDescription)
    }

    private fun recognize(description: String) =
        engine.recognize(MerchantRecognitionRequest(description))

    private fun testMerchant(
        id: String,
        alias: String,
        type: MerchantAliasType = MerchantAliasType.STRONG_BRAND,
    ) = Merchant(
        id = id,
        displayName = id,
        aliases = listOf(MerchantAlias(alias, type = type)),
        categoryIconKey = CategoryIcon("shopping"),
    )
}

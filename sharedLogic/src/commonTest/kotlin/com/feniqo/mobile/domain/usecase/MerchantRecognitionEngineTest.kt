package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.data.remote.logo.MerchantLogoDomainAllowlist
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.ExpenseCategoryVisualCatalog
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantAlias
import com.feniqo.mobile.domain.model.MerchantAliasType
import com.feniqo.mobile.domain.model.MerchantConfidenceBand
import com.feniqo.mobile.domain.model.MerchantMatchSource
import com.feniqo.mobile.domain.model.MerchantMatchStatus
import com.feniqo.mobile.domain.model.MerchantRecognitionInputSource
import com.feniqo.mobile.domain.model.MerchantRecognitionRequest
import com.feniqo.mobile.domain.model.TransactionClassification
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import com.feniqo.mobile.domain.model.UserMerchantDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun normalizer_convertsPlusSignToSemanticPlusToken() {
        assertEquals(
            "DISNEY PLUS",
            TurkishDescriptionNormalizer.normalize("Disney+"),
        )
        assertEquals(
            "DISNEY PLUS",
            TurkishDescriptionNormalizer.normalize("Disney +"),
        )
        assertEquals(
            "POS DISNEY PLUS 1234",
            TurkishDescriptionNormalizer.normalize("POS DISNEY+ 1234"),
        )
    }

    @Test
    fun userEnteredDescription_netflixFee_matchesNetflixAndCanShowLogo() {
        val result =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "Netflix ücreti",
                    inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
                ),
            )
        assertEquals("netflix", result.merchant?.id)
        assertEquals(80, result.confidence)
        assertEquals(MerchantMatchSource.STRONG_BRAND, result.source)
        assertEquals(MerchantConfidenceBand.HIGH, result.confidenceBand)
        assertEquals(MerchantMatchStatus.MATCHED, result.status)
        assertTrue(result.canShowMerchantLogo)
        assertEquals("Netflix ücreti", result.rawDescription)
        assertEquals(MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION, result.inputSource)
    }

    @Test
    fun userEnteredDescription_exactShortBrandBim_isExactAndHighConfidence() {
        val result =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "BİM",
                    inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
                ),
            )
        assertEquals("bim", result.merchant?.id)
        assertEquals(95, result.confidence)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, result.source)
        assertEquals(MerchantConfidenceBand.VERIFIED, result.confidenceBand)
        assertEquals(MerchantMatchStatus.MATCHED, result.status)
        assertTrue(result.canShowMerchantLogo)
        assertEquals("BİM", result.rawDescription)
        assertEquals(MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION, result.inputSource)
    }

    @Test
    fun bankDescription_posBim_isShortAlias65AndLogoHidden() {
        val result =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "POS BIM 1234",
                    inputSource = MerchantRecognitionInputSource.BANK_DESCRIPTION,
                ),
            )
        assertEquals("bim", result.merchant?.id)
        assertEquals(65, result.confidence)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, result.source)
        assertEquals(MerchantConfidenceBand.MEDIUM, result.confidenceBand)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, result.status)
        assertFalse(result.canShowMerchantLogo)
        assertEquals("POS BIM 1234", result.rawDescription)
        assertEquals(MerchantRecognitionInputSource.BANK_DESCRIPTION, result.inputSource)
    }

    @Test
    fun bankDescription_exactShortBrandBim_remainsShortAliasAndLogoHidden() {
        val result =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "BIM",
                    inputSource = MerchantRecognitionInputSource.BANK_DESCRIPTION,
                ),
            )
        assertEquals("bim", result.merchant?.id)
        assertEquals(65, result.confidence)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, result.source)
        assertFalse(result.canShowMerchantLogo)
    }

    @Test
    fun exactAlias_starbucks_usesApprovedSystemConfidence() {
        val result = recognizeUser("Starbucks")
        assertEquals("starbucks", result.merchant?.id)
        assertEquals(95, result.confidence)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, result.source)
        assertEquals(MerchantConfidenceBand.VERIFIED, result.confidenceBand)
        assertTrue(result.canShowMerchantLogo)
    }

    @Test
    fun disneyPlus_matchingAndExclusions_areAccurate() {
        val userPlusSign = recognizeUser("Disney+")
        assertEquals("disney_plus", userPlusSign.merchant?.id)
        assertEquals(95, userPlusSign.confidence)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, userPlusSign.source)
        assertTrue(userPlusSign.canShowMerchantLogo)

        val userPlusWord = recognizeUser("Disney Plus")
        assertEquals("disney_plus", userPlusWord.merchant?.id)
        assertEquals(95, userPlusWord.confidence)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, userPlusWord.source)
        assertTrue(userPlusWord.canShowMerchantLogo)

        val bankPlus = recognizeBank("POS DISNEY+ 1234")
        assertEquals("disney_plus", bankPlus.merchant?.id)
        assertEquals(90, bankPlus.confidence)
        assertTrue(bankPlus.canShowMerchantLogo)

        val bankPlusWord = recognizeBank("DISNEY PLUS")
        assertEquals("disney_plus", bankPlusWord.merchant?.id)

        val userDisneyAlone = recognizeUser("Disney")
        assertEquals(MerchantMatchStatus.NO_MATCH, userDisneyAlone.status)
        assertNull(userDisneyAlone.merchant)

        val userDisneyStore = recognizeUser("Disney Store")
        assertEquals(MerchantMatchStatus.NO_MATCH, userDisneyStore.status)
        assertNull(userDisneyStore.merchant)
    }

    @Test
    fun amazonPrime_distinguishedFromGeneralAmazon() {
        val amazonMerchant = StarterMerchantCatalog.merchants.first { it.id == "amazon" }
        assertEquals("Amazon", amazonMerchant.displayName)
        assertEquals("shopping", amazonMerchant.categoryIconKey.key)
        assertEquals("amazon", amazonMerchant.verifiedLogoKey)

        val primeMerchant = StarterMerchantCatalog.merchants.first { it.id == "amazon_prime" }
        assertEquals("Amazon Prime", primeMerchant.displayName)
        assertEquals("subscriptions", primeMerchant.categoryIconKey.key)
        assertEquals("amazon", primeMerchant.verifiedLogoKey)

        val primeResult = recognizeBank("AMAZON PRIME 1234")
        assertEquals("amazon_prime", primeResult.merchant?.id)
        assertEquals("Amazon Prime", primeResult.merchant?.displayName)
        assertEquals(90, primeResult.confidence)
        assertTrue(primeResult.canShowMerchantLogo)

        val primeVideoResult = recognizeBank("PRIME VIDEO 5555")
        assertEquals("amazon_prime", primeVideoResult.merchant?.id)
        assertEquals("Amazon Prime", primeVideoResult.merchant?.displayName)

        val amazonResult = recognizeBank("AMAZON COM TR 1234")
        assertEquals("amazon", amazonResult.merchant?.id)
        assertEquals("Amazon", amazonResult.merchant?.displayName)
        assertEquals(80, amazonResult.confidence)
        assertTrue(amazonResult.canShowMerchantLogo)

        val amazonDotComResult = recognizeBank("AMAZON.COM.TR 9999")
        assertEquals("amazon", amazonDotComResult.merchant?.id)
        assertEquals("Amazon", amazonDotComResult.merchant?.displayName)

        val amazonTrResult = recognizeBank("AMAZON TURKIYE 5678")
        assertEquals("amazon", amazonTrResult.merchant?.id)
        assertEquals("Amazon", amazonTrResult.merchant?.displayName)

        val userPrime = recognizeUser("Amazon Prime")
        assertEquals("amazon_prime", userPrime.merchant?.id)
        assertEquals("Amazon Prime", userPrime.merchant?.displayName)

        val userAmazon = recognizeUser("Amazon")
        assertEquals("amazon", userAmazon.merchant?.id)
        assertEquals("Amazon", userAmazon.merchant?.displayName)
    }

    @Test
    fun trendyolYemek_distinguishedFromGeneralTrendyol() {
        val yemekResult = recognizeBank("POS TRENDYOL YEMEK ISTANBUL")
        assertEquals("trendyol_yemek", yemekResult.merchant?.id)
        assertEquals(90, yemekResult.confidence)
        assertTrue(yemekResult.canShowMerchantLogo)

        val generalResult = recognizeBank("POS TRENDYOL ALISVERIS")
        assertEquals("trendyol", generalResult.merchant?.id)
        assertEquals(80, generalResult.confidence)
        assertTrue(generalResult.canShowMerchantLogo)

        assertEquals("trendyol_yemek", recognizeUser("Trendyol Yemek").merchant?.id)
        assertEquals("trendyol", recognizeUser("Trendyol").merchant?.id)
    }

    @Test
    fun getirYemek_distinguishedFromGeneralGetir() {
        val yemekResult = recognizeBank("GETIRYEMEK SIPARIS")
        assertEquals("getiryemek", yemekResult.merchant?.id)
        assertEquals(90, yemekResult.confidence)
        assertTrue(yemekResult.canShowMerchantLogo)

        val groceriesResult = recognizeBank("GETIR SIPARIS")
        assertEquals("getir", groceriesResult.merchant?.id)
        assertEquals(80, groceriesResult.confidence)
        assertTrue(groceriesResult.canShowMerchantLogo)

        assertEquals("getiryemek", recognizeUser("GetirYemek").merchant?.id)
        assertEquals("getir", recognizeUser("Getir").merchant?.id)
    }

    @Test
    fun migrosYemekAndSanalMarket_distinguishedFromGeneralMigros() {
        val yemekResult = recognizeBank("POS MIGROS YEMEK 1234")
        assertEquals("migros_yemek", yemekResult.merchant?.id)
        assertEquals(90, yemekResult.confidence)
        assertTrue(yemekResult.canShowMerchantLogo)

        val sanalMarketResult = recognizeBank("MIGROS SANAL MARKET 1234")
        assertEquals("migros_sanal_market", sanalMarketResult.merchant?.id)
        assertEquals(90, sanalMarketResult.confidence)
        assertTrue(sanalMarketResult.canShowMerchantLogo)

        val generalMigrosResult = recognizeBank("POS MIGROS 1234")
        assertEquals("migros", generalMigrosResult.merchant?.id)
        assertEquals(80, generalMigrosResult.confidence)
        assertTrue(generalMigrosResult.canShowMerchantLogo)

        assertEquals("migros_yemek", recognizeUser("Migros Yemek").merchant?.id)
        assertEquals("migros_sanal_market", recognizeUser("Migros Sanal Market").merchant?.id)
        assertEquals("migros", recognizeUser("Migros").merchant?.id)
    }

    @Test
    fun historicalBluTvAlias_mapsToMaxMerchantId() {
        val userBluTv = recognizeUser("BluTV")
        assertEquals("max", userBluTv.merchant?.id)
        assertEquals(95, userBluTv.confidence)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, userBluTv.source)

        val bankBluTv = recognizeBank("POS BLUTV 1234")
        assertEquals("max", bankBluTv.merchant?.id)
        assertEquals(80, bankBluTv.confidence)
        assertTrue(bankBluTv.canShowMerchantLogo)

        val hboMax = recognizeUser("HBO Max")
        assertEquals("max", hboMax.merchant?.id)
        assertEquals("HBO Max", hboMax.merchant?.displayName)
        assertEquals("max", hboMax.merchant?.verifiedLogoKey)

        assertEquals("max", recognizeUser("HBO MAX").merchant?.id)
        assertEquals("max", recognizeUser("MAX").merchant?.id)
        assertEquals("max", recognizeUser("MAX HBO").merchant?.id)
        assertEquals("max", recognizeUser("BLU TV").merchant?.id)
    }

    @Test
    fun groceriesExpansion_positiveRecognitionForNewMerchants() {
        val cases =
            listOf(
                "BIZIM TOPTAN" to "bizim_toptan",
                "METRO GROSSMARKET" to "metro_turkiye",
                "ISTEGELSIN" to "istegelsin",
                "ONUR MARKET" to "onur_market",
                "HAPPY CENTER" to "happy_center",
                "HAKMAR EXPRESS" to "hakmar",
                "KIM MARKET" to "kim_market",
                "CAGRI MARKET" to "cagri_market",
                "OZKURUSLAR MARKET" to "ozkuruslar",
                "SEC MARKET" to "sec_market",
            )
        cases.forEach { (rawDescription, expectedMerchantId) ->
            val result = recognizeUser(rawDescription)
            assertEquals(
                expectedMerchantId,
                result.merchant?.id,
                "Description '$rawDescription' should resolve to merchant '$expectedMerchantId'.",
            )
            assertEquals(MerchantMatchStatus.MATCHED, result.status)
            assertTrue(result.confidence >= 75)
        }
    }

    @Test
    fun groceriesExpansion_falsePositiveAndConflictAvoidance() {
        // "metro ile işe gittim" -> metro_turkiye eşleşmemeli
        val metroCommute = recognizeUser("metro ile işe gittim")
        assertNull(metroCommute.merchant)
        assertEquals(MerchantMatchStatus.NO_MATCH, metroCommute.status)

        // "çağrı merkezi ödemesi" -> cagri_market eşleşmemeli
        val callCenter = recognizeUser("çağrı merkezi ödemesi")
        assertNull(callCenter.merchant)
        assertEquals(MerchantMatchStatus.NO_MATCH, callCenter.status)

        // Tek başına "onur" -> onur_market eşleşmemeli
        val onurAlone = recognizeUser("onur")
        assertNull(onurAlone.merchant)
        assertEquals(MerchantMatchStatus.NO_MATCH, onurAlone.status)

        // Tek başına "kim" -> kim_market eşleşmemeli
        val kimAlone = recognizeUser("kim")
        assertNull(kimAlone.merchant)
        assertEquals(MerchantMatchStatus.NO_MATCH, kimAlone.status)

        // Tek başına "seç" -> sec_market eşleşmemeli
        val secAlone = recognizeUser("seç")
        assertNull(secAlone.merchant)
        assertEquals(MerchantMatchStatus.NO_MATCH, secAlone.status)

        // Genel "market alışverişi" hâlâ merchant eşleşmesi üretmemeli
        val marketAlisverisi = recognizeUser("market alışverişi")
        assertNull(marketAlisverisi.merchant)
        assertEquals(MerchantMatchStatus.NO_MATCH, marketAlisverisi.status)

        // Mevcut Migros/Migros Sanal Market ayrımı korunmalı
        assertEquals("migros", recognizeUser("Migros").merchant?.id)
        assertEquals("migros_sanal_market", recognizeUser("Migros Sanal Market").merchant?.id)

        // Getir/GetirYemek ayrımı korunmalı
        assertEquals("getir", recognizeUser("Getir").merchant?.id)
        assertEquals("getiryemek", recognizeUser("GetirYemek").merchant?.id)

        // Yemeksepeti/Yemeksepeti Market ayrımı korunmalı
        assertEquals("yemeksepeti", recognizeUser("Yemeksepeti").merchant?.id)
        assertEquals("yemeksepeti_market", recognizeUser("Yemeksepeti Market").merchant?.id)
    }

    @Test
    fun genericMarketAlisverisi_producesNoMatch() {
        val userResult = recognizeUser("market alışverişi")
        assertEquals(MerchantMatchStatus.NO_MATCH, userResult.status)
        assertNull(userResult.merchant)
        assertEquals(0, userResult.confidence)

        val bankResult = recognizeBank("POS MARKET ALISVERISI 42")
        assertEquals(MerchantMatchStatus.NO_MATCH, bankResult.status)
        assertNull(bankResult.merchant)
    }

    @Test
    fun genericPremiumUyelik_producesNoMatch() {
        val userResult = recognizeUser("premium üyelik")
        assertEquals(MerchantMatchStatus.NO_MATCH, userResult.status)
        assertNull(userResult.merchant)
        assertEquals(0, userResult.confidence)

        val bankResult = recognizeBank("PREMIUM UYELIK ODEMESI")
        assertEquals(MerchantMatchStatus.NO_MATCH, bankResult.status)
        assertNull(bankResult.merchant)
    }

    @Test
    fun standaloneDisallowedTokens_doNotMatchAnyMerchant() {
        listOf("market", "premium", "apple", "shop", "store", "yemek").forEach { genericWord ->
            val userResult = recognizeUser(genericWord)
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                userResult.status,
                "Generic token '$genericWord' should not match any merchant as user input.",
            )
            assertNull(userResult.merchant)

            val bankResult = recognizeBank(genericWord)
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                bankResult.status,
                "Generic token '$genericWord' should not match any merchant as bank input.",
            )
            assertNull(bankResult.merchant)
        }
    }

    @Test
    fun ambiguousCandidates_doNotSelectAutomaticMerchant() {
        val catalog =
            listOf(
                testMerchant("branch", "ACME STORE", MerchantAliasType.BRANCH_OR_POS_PATTERN),
                testMerchant("brand", "ACME STORE", MerchantAliasType.STRONG_BRAND),
            )
        val result =
            MerchantRecognitionEngine(catalog).recognize(
                MerchantRecognitionRequest(
                    rawDescription = "POS ACME STORE 42",
                    inputSource = MerchantRecognitionInputSource.BANK_DESCRIPTION,
                ),
            )
        assertEquals(MerchantMatchStatus.AMBIGUOUS, result.status)
        assertNull(result.merchant)
        assertEquals(0, result.confidence)
    }

    @Test
    fun decisiveScoreGapOfTenPoints_selectsHigherConfidenceMerchant() {
        val catalog =
            listOf(
                testMerchant("service", "ACME STORE", MerchantAliasType.BRAND_AND_SERVICE),
                testMerchant("brand", "ACME STORE", MerchantAliasType.STRONG_BRAND),
            )
        val result =
            MerchantRecognitionEngine(catalog).recognize(
                MerchantRecognitionRequest(
                    rawDescription = "POS ACME STORE 42",
                    inputSource = MerchantRecognitionInputSource.BANK_DESCRIPTION,
                ),
            )
        assertEquals("service", result.merchant?.id)
        assertEquals(90, result.confidence)
    }

    @Test
    fun negativeAlias_removesOtherwiseMatchingCandidate() {
        val result = recognizeBank("MIGROS HEMEN SIPARIS")
        assertEquals(MerchantMatchStatus.NO_MATCH, result.status)
        assertNull(result.merchant)
    }

    @Test
    fun refund_isClassifiedButMerchantCanStillMatch() {
        val result = recognizeBank("NETFLIX IADE")
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
            val result = recognizeBank(description)
            assertEquals(MerchantMatchStatus.EXCLUDED_TRANSACTION, result.status)
            assertEquals(classification, result.transactionClassification)
            assertNull(result.merchant)
        }
    }

    @Test
    fun userCorrection_precedesSystemPrediction() {
        val result =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "POS MIGROS",
                    inputSource = MerchantRecognitionInputSource.BANK_DESCRIPTION,
                    personalDecision = UserMerchantDecision.Assign("shell"),
                ),
            )
        assertEquals("shell", result.merchant?.id)
        assertEquals(MerchantMatchSource.USER_VERIFIED, result.source)
        assertEquals(100, result.confidence)
        assertEquals(MerchantConfidenceBand.VERIFIED, result.confidenceBand)
    }

    @Test
    fun verifiedSources_followPersonalWorkspaceBankPriority() {
        val personal =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "POS MIGROS",
                    inputSource = MerchantRecognitionInputSource.BANK_DESCRIPTION,
                    personalDecision = UserMerchantDecision.Assign("shell"),
                    workspaceVerifiedMerchantId = "spotify",
                    bankVerifiedMerchantId = "netflix",
                ),
            )
        assertEquals("shell", personal.merchant?.id)
        assertEquals(MerchantMatchSource.USER_VERIFIED, personal.source)

        val workspace =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "POS MIGROS",
                    inputSource = MerchantRecognitionInputSource.BANK_DESCRIPTION,
                    workspaceVerifiedMerchantId = "spotify",
                    bankVerifiedMerchantId = "netflix",
                ),
            )
        assertEquals("spotify", workspace.merchant?.id)
        assertEquals(MerchantMatchSource.WORKSPACE_VERIFIED, workspace.source)

        val bank =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "POS MIGROS",
                    inputSource = MerchantRecognitionInputSource.BANK_DESCRIPTION,
                    bankVerifiedMerchantId = "netflix",
                ),
            )
        assertEquals("netflix", bank.merchant?.id)
        assertEquals(98, bank.confidence)
        assertEquals(MerchantMatchSource.BANK_VERIFIED_ID, bank.source)
    }

    @Test
    fun personalBlock_preventsCatalogMatchAndSetsBlockedByUserStatus() {
        val result =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "Netflix",
                    inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
                    personalDecision = UserMerchantDecision.Block,
                ),
            )
        assertNull(result.merchant)
        assertEquals(0, result.confidence)
        assertEquals(MerchantMatchSource.USER_VERIFIED, result.source)
        assertEquals(MerchantMatchStatus.BLOCKED_BY_USER, result.status)
        assertFalse(result.canShowMerchantLogo)
        assertEquals("Netflix", result.rawDescription)
    }

    @Test
    fun personalBlock_precedesWorkspaceAndBankVerification() {
        val result =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "Netflix",
                    inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
                    personalDecision = UserMerchantDecision.Block,
                    workspaceVerifiedMerchantId = "spotify",
                    bankVerifiedMerchantId = "netflix",
                ),
            )
        assertNull(result.merchant)
        assertEquals(0, result.confidence)
        assertEquals(MerchantMatchSource.USER_VERIFIED, result.source)
        assertEquals(MerchantMatchStatus.BLOCKED_BY_USER, result.status)
        assertFalse(result.canShowMerchantLogo)
    }

    @Test
    fun unknownAssignMerchantId_failsClosedWithInvalidVerification() {
        val result =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "Netflix",
                    inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
                    personalDecision = UserMerchantDecision.Assign("non_existent_merchant_id"),
                ),
            )
        assertNull(result.merchant)
        assertEquals(0, result.confidence)
        assertNull(result.source)
        assertEquals(MerchantMatchStatus.INVALID_VERIFICATION, result.status)
        assertFalse(result.canShowMerchantLogo)
        assertEquals("Netflix", result.rawDescription)
    }

    @Test
    fun excludedTransaction_withAssignOrBlock_remainsExcludedTransaction() {
        val assignOnSalary =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "Maaş Ödemesi",
                    inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
                    personalDecision = UserMerchantDecision.Assign("netflix"),
                ),
            )
        assertEquals(MerchantMatchStatus.EXCLUDED_TRANSACTION, assignOnSalary.status)
        assertEquals(TransactionClassification.SALARY, assignOnSalary.transactionClassification)
        assertNull(assignOnSalary.merchant)
        assertEquals(0, assignOnSalary.confidence)

        val blockOnTransfer =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = "FAST Transfer",
                    inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
                    personalDecision = UserMerchantDecision.Block,
                ),
            )
        assertEquals(MerchantMatchStatus.EXCLUDED_TRANSACTION, blockOnTransfer.status)
        assertEquals(TransactionClassification.PERSONAL_TRANSFER, blockOnTransfer.transactionClassification)
        assertNull(blockOnTransfer.merchant)
        assertEquals(0, blockOnTransfer.confidence)
    }

    @Test
    fun assignDecision_withBlankMerchantId_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            UserMerchantDecision.Assign("")
        }
        assertFailsWith<IllegalArgumentException> {
            UserMerchantDecision.Assign("   ")
        }
    }

    @Test
    fun rawDescriptionAndInputSource_arePreservedByteForByteInResult() {
        val raw = "  TANIMSIZ ISLEM #42  "
        val userResult =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = raw,
                    inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
                ),
            )
        assertEquals(MerchantMatchStatus.NO_MATCH, userResult.status)
        assertNull(userResult.merchant)
        assertEquals(raw, userResult.rawDescription)
        assertEquals(MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION, userResult.inputSource)

        val bankResult =
            engine.recognize(
                MerchantRecognitionRequest(
                    rawDescription = raw,
                    inputSource = MerchantRecognitionInputSource.BANK_DESCRIPTION,
                ),
            )
        assertEquals(MerchantMatchStatus.NO_MATCH, bankResult.status)
        assertNull(bankResult.merchant)
        assertEquals(raw, bankResult.rawDescription)
        assertEquals(MerchantRecognitionInputSource.BANK_DESCRIPTION, bankResult.inputSource)
    }

    @Test
    fun starterCatalog_merchantIdsAreUniqueAndLowercaseSnakeCase() {
        val ids = StarterMerchantCatalog.merchants.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "Starter merchant IDs must be unique.")
        val snakeCasePattern = Regex("^[a-z0-9]+(_[a-z0-9]+)*$")
        StarterMerchantCatalog.merchants.forEach { merchant ->
            assertTrue(
                snakeCasePattern.matches(merchant.id),
                "Merchant ID '${merchant.id}' must be lowercase snake_case.",
            )
        }
    }

    @Test
    fun starterCatalog_allAliasesAreNonEmptyWhenNormalized() {
        StarterMerchantCatalog.merchants.forEach { merchant ->
            merchant.aliases.forEach { alias ->
                val normalized = TurkishDescriptionNormalizer.normalize(alias.value)
                assertTrue(
                    normalized.isNotBlank(),
                    "Merchant '${merchant.id}' has alias '${alias.value}' which normalizes to empty.",
                )
            }
        }
    }

    @Test
    fun starterCatalog_noAliasMatchesDisallowedGenericTokens() {
        StarterMerchantCatalog.merchants.forEach { merchant ->
            merchant.aliases.forEach { alias ->
                val normalized = TurkishDescriptionNormalizer.normalize(alias.value)
                assertFalse(
                    DISALLOWED_STANDALONE_GENERIC_TOKENS.contains(normalized),
                    "Merchant '${merchant.id}' cannot have disallowed standalone generic token '$normalized' as alias.",
                )
            }
        }
    }

    @Test
    fun starterCatalog_noMerchantHasDuplicateNormalizedAliases() {
        StarterMerchantCatalog.merchants.forEach { merchant ->
            val normalizedAliases =
                merchant.aliases.map {
                    TurkishDescriptionNormalizer.normalize(it.value)
                }
            val duplicates =
                normalizedAliases
                    .groupingBy { it }
                    .eachCount()
                    .filter { it.value > 1 }
                    .keys
            assertTrue(
                duplicates.isEmpty(),
                "Merchant '${merchant.id}' has duplicate normalized aliases: $duplicates",
            )
        }
    }

    @Test
    fun starterCatalog_verifiedLogoKeyOnlyPresentForApprovedMerchants() {
        val approvedKeys =
            setOf(
                "migros",
                "carrefoursa",
                "a101",
                "bim",
                "sok",
                "netflix",
                "spotify",
                "disney_plus",
                "trendyol",
                "hepsiburada",
                "amazon",
                "yemeksepeti",
                "turkcell",
                "vodafone",
                "petrol_ofisi",
                "opet",
                "shell",
                "thy",
                "pegasus",
                "uber",
                "obilet",
                "enuygun",
                "macrocenter",
                "file_market",
                "tarim_kredi",
                "getir",
                "bizim_toptan",
                "metro_turkiye",
                "starbucks",
                "mcdonalds",
                "burger_king",
                "kfc",
                "popeyes",
                "dominos",
                "little_caesars",
                "pizza_lazza",
                "tavuk_dunyasi",
                "baydoner",
                "kofteci_yusuf",
                "kahve_dunyasi",
                "mado",
                "espressolab",
                "simit_sarayi",
                "bigchefs",
                "max",
                "exxen",
                "gain",
                "mubi",
                "tod",
                "tabii",
                "apple_services",
                "adobe",
                "canva",
                "s_sport_plus",
                "tv_plus",
                "tivibu",
                "d_smart_go",
                "deezer",
                "fizy",
                "muud",
                "storytel",
                "audible",
                "dropbox",
                "n11",
                "ciceksepeti",
                "pazarama",
                "boyner",
                "lc_waikiki",
                "defacto",
                "teknosa",
                "mediamarkt",
                "flo",
                "koton",
                "mavi",
                "zara",
                "hm",
                "pull_and_bear",
                "bershka",
                "stradivarius",
                "beymen",
                "vakko",
                "ikea",
                "koctas",
                "bauhaus",
                "vatan_bilgisayar",
                "ebebek",
                "gratis",
                "watsons",
                "rossmann",
                "sephora",
                "eve_shop",
                "flormar",
                "golden_rose",
                "yves_rocher",
                "the_body_shop",
                "kiko_milano",
                "mac_cosmetics",
                "clinique",
                "avon",
                "oriflame",
                "farmasi",
                // 17 yeni doğrulanmış ulaşım ve seyahat merchant'ı
                "istanbulkart",
                "tcdd",
                "bitaksi",
                "marti",
                "ispark",
                "metro_turizm",
                "pamukkale_turizm",
                "varan_turizm",
                "ido",
                "ajet",
                "sunexpress",
                "corendon",
                "booking",
                "airbnb",
                "ets_tur",
                "jolly_tur",
                "setur",
                "paribu_cineverse",
                "biletix",
                "steam",
                "biletinial",
                "bubilet",
                "passo",
                "mobilet",
                "biletino",
                "bugece",
                "epic_games",
                "riot_games",
                "battle_net",
                "ubisoft_store",
                "cinemarine",
                "vialand",
                "apsiyon",
                "yonetimcell",
                "armut",
                "senyonet",
                "mrusta",
                "pronet",
                "kale_alarm",
                "acibadem",
                "memorial",
                "medical_park",
                "liv_hospital",
                "medicana",
                "florence_nightingale",
                "anadolu_saglik",
                "amerikan_hastanesi",
                "dunyagoz",
                "medipol",
                "biruni_hastanesi",
                "guven_hastanesi",
                "lokman_hekim",
                "baskent_hastanesi",
                "synevo",
                "duzen_laboratuvari",
                "dentgroup",
                "hospitadent",
                "atasun_optik",
                "udemy",
                "coursera",
                "duolingo",
                "cambly",
                "kunduz",
                "tonguc_akademi",
                "okulistik",
                "morpa_kampus",
                "raunt",
                "busuu",
                "preply",
                "italki",
                "skillshare",
                "derslig",
                "benim_hocam",
                "pegem_akademi",
                "english_time",
                "bilge_adam",
                "wall_street_english",
                "amerikankultur",
                "dr",
                "kitapyurdu",
                "bkm_kitap",
                "idefix",
                "petlebi",
                "civil",
                "temizmama",
                "toyzz_shop",
                "juen_pet_market",
                "joker_baby",
                "armagan_oyuncak",
                "petzz_shop",
                "markamama",
                "petburada",
                "petihtiyac",
                "kolaymama",
                "panco",
                "bg_store",
                "minycenter",
                "vetamerikan",
                "kizilay",
                "losev",
                "tema",
                "tev",
                "mehmetcik_vakfi",
                "akut",
                "acev",
                "tog",
                "gib",
                "sgk",
            )
        val sharedTwelveSubscriptionKeys =
            mapOf(
                "youtube_premium" to "youtube",
                "amazon_prime" to "amazon",
                "google_one" to "google",
                "microsoft_365" to "microsoft",
                "playstation_plus" to "playstation",
                "xbox_game_pass" to "xbox",
                "linkedin_premium" to "linkedin",
                "nintendo_switch_online" to "nintendo",
                "ea_play" to "ea",
                "hepsiburada_premium" to "hepsiburada",
                "money_gold" to "migros",
                "ypro" to "yemeksepeti",
                "playstation_store" to "playstation",
                "xbox_store" to "xbox",
            )
        StarterMerchantCatalog.merchants.forEach { merchant ->
            when {
                merchant.id in approvedKeys -> {
                    assertEquals(
                        merchant.id,
                        merchant.verifiedLogoKey,
                        "Approved direct merchant '${merchant.id}' must carry stable logo key equal to its id.",
                    )
                }
                merchant.id in sharedTwelveSubscriptionKeys -> {
                    val expectedKey = sharedTwelveSubscriptionKeys[merchant.id]
                    assertEquals(
                        expectedKey,
                        merchant.verifiedLogoKey,
                        "Shared subscription merchant '${merchant.id}' must carry verified roof logo key '$expectedKey'.",
                    )
                }
                else -> {
                    assertNull(
                        merchant.verifiedLogoKey,
                        "Merchant '${merchant.id}' cannot have unverified logo key before real assets exist.",
                    )
                }
            }
        }
        assertEquals(223, StarterMerchantCatalog.merchants.count { it.verifiedLogoKey != null })
        assertEquals(37, StarterMerchantCatalog.merchants.count { it.verifiedLogoKey == null })
        assertEquals("getir", StarterMerchantCatalog.merchants.first { it.id == "getir" }.verifiedLogoKey)
        assertEquals("youtube", StarterMerchantCatalog.merchants.first { it.id == "youtube_premium" }.verifiedLogoKey)
        assertEquals("amazon", StarterMerchantCatalog.merchants.first { it.id == "amazon_prime" }.verifiedLogoKey)
        assertEquals("migros", StarterMerchantCatalog.merchants.first { it.id == "money_gold" }.verifiedLogoKey)
        assertEquals("yemeksepeti", StarterMerchantCatalog.merchants.first { it.id == "ypro" }.verifiedLogoKey)
        assertEquals("istanbulkart", StarterMerchantCatalog.merchants.first { it.id == "istanbulkart" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "migros_sanal_market" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "yemeksepeti_market" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "trendyol_yemek" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "getiryemek" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "migros_yemek" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "turk_telekom" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "kamil_koc" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "budo" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "getir_arac" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "tiktak" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "tatilbudur" }.verifiedLogoKey)

        // 12 hizmetin stable ID, displayName ve category değerlerinin değişmediğini doğrula
        val expectedTwelveDetails =
            mapOf(
                "youtube_premium" to ("YouTube Premium" to "subscriptions"),
                "amazon_prime" to ("Amazon Prime" to "subscriptions"),
                "google_one" to ("Google One" to "subscriptions"),
                "microsoft_365" to ("Microsoft 365" to "subscriptions"),
                "playstation_plus" to ("PlayStation Plus" to "subscriptions"),
                "xbox_game_pass" to ("Xbox Game Pass" to "subscriptions"),
                "linkedin_premium" to ("LinkedIn Premium" to "subscriptions"),
                "nintendo_switch_online" to ("Nintendo Switch Online" to "subscriptions"),
                "ea_play" to ("EA Play" to "subscriptions"),
                "hepsiburada_premium" to ("Hepsiburada Premium" to "subscriptions"),
                "money_gold" to ("Money Gold" to "subscriptions"),
                "ypro" to ("Ypro" to "subscriptions"),
            )
        for ((id, details) in expectedTwelveDetails) {
            val merchant = StarterMerchantCatalog.merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Hizmet merchant '$id' katalogda bulunmalıdır")
            assertEquals(details.first, merchant.displayName, "DisplayName değişmemelidir: $id")
            assertEquals(details.second, merchant.categoryIconKey.key, "CategoryIconKey değişmemelidir: $id")
        }
    }

    @Test
    fun starterCatalog_allMerchantsUseCanonicalExpenseCategoryKeys() {
        StarterMerchantCatalog.merchants.forEach { merchant ->
            assertNotNull(
                ExpenseCategoryVisualCatalog.find(merchant.categoryIconKey),
                "${merchant.displayName} is using non-canonical category key '${merchant.categoryIconKey.key}'.",
            )
        }
    }

    private fun recognizeUser(description: String) =
        engine.recognize(
            MerchantRecognitionRequest(
                rawDescription = description,
                inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
            ),
        )

    private fun recognizeBank(description: String) =
        engine.recognize(
            MerchantRecognitionRequest(
                rawDescription = description,
                inputSource = MerchantRecognitionInputSource.BANK_DESCRIPTION,
            ),
        )

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

    @Test
    fun transportationAndTravel_catalogSizeAndVersionContract() {
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)
        assertEquals(260, merchants.map { it.id }.distinct().size)
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        assertTrue(merchants.all { it.catalogVersion == 26 })
    }

    @Test
    fun foodDining_fifteenNewMerchants_positiveRecognition() {
        // 1. mcdonalds
        val mcd1 = recognizeUser("McDonald's")
        assertEquals("mcdonalds", mcd1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mcd1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, mcd1.source)
        assertEquals(95, mcd1.confidence)

        val mcd2 = recognizeUser("MCDONALDS")
        assertEquals("mcdonalds", mcd2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mcd2.status)

        // 2. burger_king
        val bk = recognizeUser("Burger King")
        assertEquals("burger_king", bk.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bk.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, bk.source)
        assertEquals(95, bk.confidence)

        // 3. kfc (user exact)
        val kfc = recognizeUser("KFC")
        assertEquals("kfc", kfc.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, kfc.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, kfc.source)
        assertEquals(95, kfc.confidence)

        // 4. popeyes
        val pop = recognizeUser("Popeyes")
        assertEquals("popeyes", pop.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, pop.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, pop.source)
        assertEquals(95, pop.confidence)

        // 5. dominos
        val dom1 = recognizeUser("Dominos Pizza")
        assertEquals("dominos", dom1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, dom1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, dom1.source)
        assertEquals(95, dom1.confidence)

        val dom2 = recognizeUser("Domino's Pizza")
        assertEquals("dominos", dom2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, dom2.status)
        assertTrue(dom2.confidence >= 75)

        // 6. little_caesars
        val lc = recognizeUser("Little Caesars")
        assertEquals("little_caesars", lc.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, lc.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, lc.source)
        assertEquals(95, lc.confidence)

        // 7. pizza_lazza
        val pl1 = recognizeUser("Pizza Lazza")
        assertEquals("pizza_lazza", pl1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, pl1.status)
        val pl2 = recognizeUser("PizzaLazza")
        assertEquals("pizza_lazza", pl2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, pl2.status)

        // 8. tavuk_dunyasi
        val td = recognizeUser("Tavuk Dünyası")
        assertEquals("tavuk_dunyasi", td.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, td.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, td.source)
        assertEquals(95, td.confidence)

        // 9. baydoner
        val bd1 = recognizeUser("Baydöner")
        assertEquals("baydoner", bd1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bd1.status)
        val bd2 = recognizeUser("Bay Döner")
        assertEquals("baydoner", bd2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bd2.status)

        // 10. kofteci_yusuf
        val ky = recognizeUser("Köfteci Yusuf")
        assertEquals("kofteci_yusuf", ky.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ky.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ky.source)
        assertEquals(95, ky.confidence)

        // 11. kahve_dunyasi
        val kd = recognizeUser("Kahve Dünyası")
        assertEquals("kahve_dunyasi", kd.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, kd.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, kd.source)
        assertEquals(95, kd.confidence)

        // 12. mado (user exact)
        val mado = recognizeUser("Mado")
        assertEquals("mado", mado.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mado.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, mado.source)
        assertEquals(95, mado.confidence)

        // 13. espressolab
        val el1 = recognizeUser("Espressolab")
        assertEquals("espressolab", el1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, el1.status)
        val el2 = recognizeUser("Espresso Lab")
        assertEquals("espressolab", el2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, el2.status)

        // 14. simit_sarayi
        val ss = recognizeUser("Simit Sarayı")
        assertEquals("simit_sarayi", ss.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ss.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ss.source)
        assertEquals(95, ss.confidence)

        // 15. bigchefs
        val bc1 = recognizeUser("BigChefs")
        assertEquals("bigchefs", bc1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bc1.status)
        val bc2 = recognizeUser("Big Chefs")
        assertEquals("bigchefs", bc2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bc2.status)
    }

    @Test
    fun foodDining_apostropheAndServiceVariants() {
        // McDonald's apostrof normalizasyonu ile MCDONALD S alias'ına tam eşleşir ve EXACT_ALIAS 95 puan alır
        val mcd = recognizeUser("McDonald's")
        assertEquals("mcdonalds", mcd.merchant?.id)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, mcd.source)
        assertEquals(95, mcd.confidence)

        // Domino's tam kullanıcı girdisi DOMINO S alias'ına tam eşleşir ve EXACT_ALIAS 95 puan alır
        val domApostrophe = recognizeUser("Domino's")
        assertEquals("dominos", domApostrophe.merchant?.id)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, domApostrophe.source)
        assertEquals(95, domApostrophe.confidence)

        // Dominos Pizza Kadıköy girdisinde DOMINOS PIZZA BRAND_AND_SERVICE tipinde kalıp 90 puan alır
        val domService = recognizeUser("Dominos Pizza Kadıköy")
        assertEquals("dominos", domService.merchant?.id)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, domService.source)
        assertEquals(90, domService.confidence)

        // Domino's Pizza girdisinde DOMINO S alias'ı üzerinden STRONG_BRAND 80 puan ile MATCHED üretilir
        val domFullApostrophe = recognizeUser("Domino's Pizza")
        assertEquals("dominos", domFullApostrophe.merchant?.id)
        assertEquals(MerchantMatchSource.STRONG_BRAND, domFullApostrophe.source)
        assertEquals(80, domFullApostrophe.confidence)
        assertEquals(MerchantMatchStatus.MATCHED, domFullApostrophe.status)
    }

    @Test
    fun foodDining_genericPhrases_produceNoMatch() {
        val genericPhrases =
            listOf(
                "burger menü",
                "pizza siparişi",
                "kahve aldım",
                "tavuk yemeği",
                "döner siparişi",
                "simit aldım",
            )
        genericPhrases.forEach { phrase ->
            val res = recognizeUser(phrase)
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                res.status,
                "Genel ifade '$phrase' hiçbir merchant eşleşmesi üretmemelidir.",
            )
            assertNull(res.merchant)
        }
    }

    @Test
    fun foodDining_bareWords_produceNoMatch() {
        val bareWords = listOf("Yusuf", "King", "Dünya", "Saray")
        bareWords.forEach { word ->
            val res = recognizeUser(word)
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                res.status,
                "Yalın sözcük '$word' hiçbir merchant eşleşmesi üretmemelidir.",
            )
            assertNull(res.merchant)
        }
    }

    @Test
    fun foodDining_shortAliasContract_kfcAndMado() {
        // 1. KFC
        val kfcUserExact = recognizeUser("KFC")
        assertEquals(MerchantMatchStatus.MATCHED, kfcUserExact.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, kfcUserExact.source)
        assertEquals(95, kfcUserExact.confidence)

        val kfcBankExact = recognizeBank("KFC")
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, kfcBankExact.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, kfcBankExact.source)
        assertEquals(65, kfcBankExact.confidence)

        val kfcBankLong = recognizeBank("KFC KADIKOY SUBESI POS")
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, kfcBankLong.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, kfcBankLong.source)
        assertEquals(65, kfcBankLong.confidence)

        // Resolver seviyesinde güven < 75 olduğu için TransactionMerchantInfo üretilmez
        assertNull(TransactionMerchantAttributionResolver.resolve("KFC KADIKOY SUBESI POS"))

        // 2. MADO
        val madoUserExact = recognizeUser("Mado")
        assertEquals(MerchantMatchStatus.MATCHED, madoUserExact.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, madoUserExact.source)
        assertEquals(95, madoUserExact.confidence)

        val madoBankExact = recognizeBank("MADO")
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, madoBankExact.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, madoBankExact.source)
        assertEquals(65, madoBankExact.confidence)

        val madoBankLong = recognizeBank("MADO DONDURMA KADIKOY POS")
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, madoBankLong.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, madoBankLong.source)
        assertEquals(65, madoBankLong.confidence)

        assertNull(TransactionMerchantAttributionResolver.resolve("MADO DONDURMA KADIKOY POS"))
    }

    @Test
    fun foodDining_existingSubServiceDistinctions_preserved() {
        val ys = recognizeUser("Yemeksepeti")
        assertEquals("yemeksepeti", ys.merchant?.id)
        val ysm = recognizeUser("Yemeksepeti Market")
        assertEquals("yemeksepeti_market", ysm.merchant?.id)

        val getir = recognizeUser("Getir")
        assertEquals("getir", getir.merchant?.id)
        val getiryemek = recognizeUser("GetirYemek")
        assertEquals("getiryemek", getiryemek.merchant?.id)

        val migros = recognizeUser("Migros")
        assertEquals("migros", migros.merchant?.id)
        val migrosYemek = recognizeUser("Migros Yemek")
        assertEquals("migros_yemek", migrosYemek.merchant?.id)
        val migrosSanal = recognizeUser("Migros Sanal Market")
        assertEquals("migros_sanal_market", migrosSanal.merchant?.id)
    }

    @Test
    fun shopping_fifteenNewMerchants_positiveRecognition() {
        // 1. flo
        val floUser = recognizeUser("FLO")
        assertEquals("flo", floUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, floUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, floUser.source)
        assertEquals(95, floUser.confidence)

        val floLegal = recognizeUser("FLO Mağazacılık")
        assertEquals("flo", floLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, floLegal.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, floLegal.source)
        assertEquals(95, floLegal.confidence)

        val floBankLegal = recognizeBank("POS FLO MAGAZACILIK ISTANBUL")
        assertEquals("flo", floBankLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, floBankLegal.status)
        assertEquals(MerchantMatchSource.LEGAL_NAME, floBankLegal.source)
        assertEquals(92, floBankLegal.confidence)

        // 2. koton
        val koton = recognizeUser("Koton")
        assertEquals("koton", koton.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, koton.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, koton.source)
        assertEquals(95, koton.confidence)

        val kotonLegal = recognizeUser("KOTON MAGAZACILIK")
        assertEquals("koton", kotonLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, kotonLegal.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, kotonLegal.source)
        assertEquals(95, kotonLegal.confidence)

        val kotonBankLegal = recognizeBank("POS KOTON MAGAZACILIK ISTANBUL")
        assertEquals("koton", kotonBankLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, kotonBankLegal.status)
        assertEquals(MerchantMatchSource.LEGAL_NAME, kotonBankLegal.source)
        assertEquals(92, kotonBankLegal.confidence)

        // 3. mavi
        val maviGiyim = recognizeUser("Mavi Giyim")
        assertEquals("mavi", maviGiyim.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, maviGiyim.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, maviGiyim.source)
        assertEquals(95, maviGiyim.confidence)

        val maviJeans = recognizeUser("MAVI JEANS")
        assertEquals("mavi", maviJeans.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, maviJeans.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, maviJeans.source)
        assertEquals(95, maviJeans.confidence)

        val maviBank = recognizeBank("POS MAVI GIYIM KADIKOY")
        assertEquals("mavi", maviBank.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, maviBank.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, maviBank.source)
        assertEquals(90, maviBank.confidence)

        // 4. zara
        val zaraUser = recognizeUser("Zara")
        assertEquals("zara", zaraUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, zaraUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, zaraUser.source)
        assertEquals(95, zaraUser.confidence)

        val zaraGiyim = recognizeUser("Zara Giyim")
        assertEquals("zara", zaraGiyim.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, zaraGiyim.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, zaraGiyim.source)
        assertEquals(95, zaraGiyim.confidence)

        val zaraBank = recognizeBank("POS ZARA GIYIM KADIKOY")
        assertEquals("zara", zaraBank.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, zaraBank.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, zaraBank.source)
        assertEquals(90, zaraBank.confidence)

        // 5. hm
        val hmLegal = recognizeUser("H&M Mağazacılık")
        assertEquals("hm", hmLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, hmLegal.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, hmLegal.source)
        assertEquals(95, hmLegal.confidence)

        val hmBrand = recognizeUser("H and M")
        assertEquals("hm", hmBrand.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, hmBrand.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, hmBrand.source)
        assertEquals(95, hmBrand.confidence)

        val hmHennes = recognizeUser("Hennes Mauritz")
        assertEquals("hm", hmHennes.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, hmHennes.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, hmHennes.source)
        assertEquals(95, hmHennes.confidence)

        val hmBankLegal = recognizeBank("POS H M MAGAZACILIK ISTANBUL")
        assertEquals("hm", hmBankLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, hmBankLegal.status)
        assertEquals(MerchantMatchSource.LEGAL_NAME, hmBankLegal.source)
        assertEquals(92, hmBankLegal.confidence)

        // 6. pull_and_bear
        val pbUser = recognizeUser("Pull&Bear")
        assertEquals("pull_and_bear", pbUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, pbUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, pbUser.source)
        assertEquals(95, pbUser.confidence)

        val pbBrand = recognizeUser("Pull and Bear")
        assertEquals("pull_and_bear", pbBrand.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, pbBrand.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, pbBrand.source)
        assertEquals(95, pbBrand.confidence)

        val pbConcat = recognizeUser("PullBear")
        assertEquals("pull_and_bear", pbConcat.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, pbConcat.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, pbConcat.source)
        assertEquals(95, pbConcat.confidence)

        val pbBank = recognizeBank("POS PULL AND BEAR ISTANBUL")
        assertEquals("pull_and_bear", pbBank.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, pbBank.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, pbBank.source)
        assertEquals(90, pbBank.confidence)

        // 7. bershka
        val bershkaUser = recognizeUser("Bershka")
        assertEquals("bershka", bershkaUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bershkaUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, bershkaUser.source)
        assertEquals(95, bershkaUser.confidence)

        val bershkaLegal = recognizeUser("Bershka Tekstil")
        assertEquals("bershka", bershkaLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bershkaLegal.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, bershkaLegal.source)
        assertEquals(95, bershkaLegal.confidence)

        val bershkaBankLegal = recognizeBank("POS BERSHKA TEKSTIL ISTANBUL")
        assertEquals("bershka", bershkaBankLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bershkaBankLegal.status)
        assertEquals(MerchantMatchSource.LEGAL_NAME, bershkaBankLegal.source)
        assertEquals(92, bershkaBankLegal.confidence)

        // 8. stradivarius
        val stradivarius = recognizeUser("Stradivarius")
        assertEquals("stradivarius", stradivarius.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, stradivarius.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, stradivarius.source)
        assertEquals(95, stradivarius.confidence)

        // 9. beymen
        val beymenUser = recognizeUser("Beymen")
        assertEquals("beymen", beymenUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, beymenUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, beymenUser.source)
        assertEquals(95, beymenUser.confidence)

        val beymenLegal = recognizeUser("Beymen Mağazacılık")
        assertEquals("beymen", beymenLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, beymenLegal.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, beymenLegal.source)
        assertEquals(95, beymenLegal.confidence)

        val beymenBankLegal = recognizeBank("POS BEYMEN MAGAZACILIK ISTANBUL")
        assertEquals("beymen", beymenBankLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, beymenBankLegal.status)
        assertEquals(MerchantMatchSource.LEGAL_NAME, beymenBankLegal.source)
        assertEquals(92, beymenBankLegal.confidence)

        // 10. vakko
        val vakkoUser = recognizeUser("Vakko")
        assertEquals("vakko", vakkoUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, vakkoUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, vakkoUser.source)
        assertEquals(95, vakkoUser.confidence)

        val vakkoLegal = recognizeUser("Vakko Tekstil")
        assertEquals("vakko", vakkoLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, vakkoLegal.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, vakkoLegal.source)
        assertEquals(95, vakkoLegal.confidence)

        val vakkoBankLegal = recognizeBank("POS VAKKO TEKSTIL ISTANBUL")
        assertEquals("vakko", vakkoBankLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, vakkoBankLegal.status)
        assertEquals(MerchantMatchSource.LEGAL_NAME, vakkoBankLegal.source)
        assertEquals(92, vakkoBankLegal.confidence)

        // 11. ikea
        val ikeaUser = recognizeUser("IKEA")
        assertEquals("ikea", ikeaUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ikeaUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ikeaUser.source)
        assertEquals(95, ikeaUser.confidence)

        val ikeaBrand = recognizeUser("IKEA Mobilya")
        assertEquals("ikea", ikeaBrand.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ikeaBrand.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ikeaBrand.source)
        assertEquals(95, ikeaBrand.confidence)

        val ikeaBank = recognizeBank("POS IKEA MOBILYA UMRANIYE")
        assertEquals("ikea", ikeaBank.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ikeaBank.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, ikeaBank.source)
        assertEquals(90, ikeaBank.confidence)

        // 12. koctas
        val koctasUser = recognizeUser("Koçtaş")
        assertEquals("koctas", koctasUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, koctasUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, koctasUser.source)
        assertEquals(95, koctasUser.confidence)

        val koctasBrand = recognizeUser("Koçtaş Yapı Market")
        assertEquals("koctas", koctasBrand.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, koctasBrand.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, koctasBrand.source)
        assertEquals(95, koctasBrand.confidence)

        val koctasBank = recognizeBank("POS KOCTAS YAPI MARKET ANKARA")
        assertEquals("koctas", koctasBank.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, koctasBank.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, koctasBank.source)
        assertEquals(90, koctasBank.confidence)

        // 13. bauhaus
        val bauhausUser = recognizeUser("Bauhaus")
        assertEquals("bauhaus", bauhausUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bauhausUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, bauhausUser.source)
        assertEquals(95, bauhausUser.confidence)

        val bauhausBrand = recognizeUser("Bauhaus Yapı Market")
        assertEquals("bauhaus", bauhausBrand.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bauhausBrand.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, bauhausBrand.source)
        assertEquals(95, bauhausBrand.confidence)

        val bauhausBank = recognizeBank("POS BAUHAUS YAPI MARKET ANKARA")
        assertEquals("bauhaus", bauhausBank.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bauhausBank.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, bauhausBank.source)
        assertEquals(90, bauhausBank.confidence)

        // 14. vatan_bilgisayar
        val vatanUser = recognizeUser("Vatan Bilgisayar")
        assertEquals("vatan_bilgisayar", vatanUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, vatanUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, vatanUser.source)
        assertEquals(95, vatanUser.confidence)

        val vatanBrand = recognizeUser("Vatan Bilg")
        assertEquals("vatan_bilgisayar", vatanBrand.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, vatanBrand.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, vatanBrand.source)
        assertEquals(95, vatanBrand.confidence)

        val vatanBank = recognizeBank("POS VATAN BILG KADIKOY")
        assertEquals("vatan_bilgisayar", vatanBank.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, vatanBank.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, vatanBank.source)
        assertEquals(90, vatanBank.confidence)

        // 15. ebebek
        val ebebekUser = recognizeUser("ebebek")
        assertEquals("ebebek", ebebekUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ebebekUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ebebekUser.source)
        assertEquals(95, ebebekUser.confidence)

        val ebebekSpaced = recognizeUser("e bebek")
        assertEquals("ebebek", ebebekSpaced.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ebebekSpaced.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ebebekSpaced.source)
        assertEquals(95, ebebekSpaced.confidence)

        val ebebekLegal = recognizeUser("Ebebek Mağazacılık")
        assertEquals("ebebek", ebebekLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ebebekLegal.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ebebekLegal.source)
        assertEquals(95, ebebekLegal.confidence)

        val ebebekBankLegal = recognizeBank("POS EBEBEK MAGAZACILIK ISTANBUL")
        assertEquals("ebebek", ebebekBankLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ebebekBankLegal.status)
        assertEquals(MerchantMatchSource.LEGAL_NAME, ebebekBankLegal.source)
        assertEquals(92, ebebekBankLegal.confidence)
    }

    @Test
    fun shopping_shortAliasContract_floZaraIkea() {
        // FLO
        val floUser = recognizeUser("FLO")
        assertEquals(MerchantMatchStatus.MATCHED, floUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, floUser.source)
        assertEquals(95, floUser.confidence)

        val floBank = recognizeBank("FLO")
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, floBank.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, floBank.source)
        assertEquals(65, floBank.confidence)

        val floBankLong = recognizeBank("FLO AYAKKABI ANKARA POS")
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, floBankLong.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, floBankLong.source)
        assertEquals(65, floBankLong.confidence)

        assertNull(TransactionMerchantAttributionResolver.resolve("FLO AYAKKABI ANKARA POS"))

        val floUnrelatedSentence = "yeni yazilim flo kontrolu sagliyor"
        val floUnrelatedResult = recognizeUser(floUnrelatedSentence)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, floUnrelatedResult.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, floUnrelatedResult.source)
        assertEquals(65, floUnrelatedResult.confidence)
        assertNull(TransactionMerchantAttributionResolver.resolve(floUnrelatedSentence))

        // ZARA
        val zaraUser = recognizeUser("Zara")
        assertEquals(MerchantMatchStatus.MATCHED, zaraUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, zaraUser.source)
        assertEquals(95, zaraUser.confidence)

        val zaraBank = recognizeBank("ZARA")
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, zaraBank.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, zaraBank.source)
        assertEquals(65, zaraBank.confidence)

        val zaraBankLong = recognizeBank("ZARA ISTANBUL POS")
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, zaraBankLong.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, zaraBankLong.source)
        assertEquals(65, zaraBankLong.confidence)

        assertNull(TransactionMerchantAttributionResolver.resolve("ZARA ISTANBUL POS"))

        val zaraUnrelatedSentence = "sivas zara ilcesinde koy ziyareti"
        val zaraUnrelatedResult = recognizeUser(zaraUnrelatedSentence)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, zaraUnrelatedResult.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, zaraUnrelatedResult.source)
        assertEquals(65, zaraUnrelatedResult.confidence)
        assertNull(TransactionMerchantAttributionResolver.resolve(zaraUnrelatedSentence))

        // IKEA
        val ikeaUser = recognizeUser("IKEA")
        assertEquals(MerchantMatchStatus.MATCHED, ikeaUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ikeaUser.source)
        assertEquals(95, ikeaUser.confidence)

        val ikeaBank = recognizeBank("IKEA")
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, ikeaBank.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, ikeaBank.source)
        assertEquals(65, ikeaBank.confidence)

        val ikeaBankLong = recognizeBank("IKEA UMRANIYE POS")
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, ikeaBankLong.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, ikeaBankLong.source)
        assertEquals(65, ikeaBankLong.confidence)

        assertNull(TransactionMerchantAttributionResolver.resolve("IKEA UMRANIYE POS"))

        val ikeaUnrelatedSentence = "ders notlarinda ikea ornegi incelendi"
        val ikeaUnrelatedResult = recognizeUser(ikeaUnrelatedSentence)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, ikeaUnrelatedResult.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, ikeaUnrelatedResult.source)
        assertEquals(65, ikeaUnrelatedResult.confidence)
        assertNull(TransactionMerchantAttributionResolver.resolve(ikeaUnrelatedSentence))
    }

    @Test
    fun shopping_safeDistinctions_and_falsePositivePrevention() {
        // Mavi: Standalone "mavi" or phrases like "mavi gömlek aldım" must NOT match
        val maviSentence = recognizeUser("mavi gömlek aldım")
        assertEquals(MerchantMatchStatus.NO_MATCH, maviSentence.status)
        assertNull(maviSentence.merchant)

        val maviStandalone = recognizeUser("mavi")
        assertEquals(MerchantMatchStatus.NO_MATCH, maviStandalone.status)
        assertNull(maviStandalone.merchant)

        assertNull(TransactionMerchantAttributionResolver.resolve("mavi gömlek aldım"))
        assertNull(TransactionMerchantAttributionResolver.resolve("mavi"))

        // Vatan: Standalone "vatan" or phrases like "vatan sağ olsun" must NOT match
        val vatanSentence = recognizeUser("vatan sağ olsun")
        assertEquals(MerchantMatchStatus.NO_MATCH, vatanSentence.status)
        assertNull(vatanSentence.merchant)

        val vatanStandalone = recognizeUser("vatan")
        assertEquals(MerchantMatchStatus.NO_MATCH, vatanStandalone.status)
        assertNull(vatanStandalone.merchant)

        assertNull(TransactionMerchantAttributionResolver.resolve("vatan sağ olsun"))
        assertNull(TransactionMerchantAttributionResolver.resolve("vatan"))

        // HM / H M: Standalone short acronyms without brand context must NOT match
        val hmStandalone = recognizeUser("HM")
        assertEquals(MerchantMatchStatus.NO_MATCH, hmStandalone.status)
        assertNull(hmStandalone.merchant)

        val hmSpaced = recognizeUser("H M")
        assertEquals(MerchantMatchStatus.NO_MATCH, hmSpaced.status)
        assertNull(hmSpaced.merchant)

        // Generic disallowed tokens
        val genericWords = listOf("mağaza", "giyim", "mobilya", "bilgisayar", "market")
        genericWords.forEach { word ->
            val result = recognizeUser(word)
            assertEquals(MerchantMatchStatus.NO_MATCH, result.status, "Generic word '$word' must not match any merchant")
            assertNull(result.merchant)
        }
    }

    @Test
    fun personalCare_fifteenNewMerchants_positiveRecognition() {
        // 1. gratis
        val gratisUser = recognizeUser("Gratis")
        assertEquals("gratis", gratisUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, gratisUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, gratisUser.source)
        assertEquals(95, gratisUser.confidence)
        val gratisUpper = recognizeUser("GRATIS")
        assertEquals("gratis", gratisUpper.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, gratisUpper.status)

        // 2. watsons
        val watsons = recognizeUser("Watsons")
        assertEquals("watsons", watsons.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, watsons.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, watsons.source)
        assertEquals(95, watsons.confidence)

        // 3. rossmann
        val rossmann = recognizeUser("Rossmann")
        assertEquals("rossmann", rossmann.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, rossmann.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, rossmann.source)
        assertEquals(95, rossmann.confidence)

        // 4. sephora
        val sephora = recognizeUser("Sephora")
        assertEquals("sephora", sephora.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, sephora.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, sephora.source)
        assertEquals(95, sephora.confidence)

        // 5. eve_shop
        val eveShop = recognizeUser("EVE SHOP")
        assertEquals("eve_shop", eveShop.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, eveShop.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, eveShop.source)
        assertEquals(95, eveShop.confidence)

        // 6. flormar
        val flormar = recognizeUser("Flormar")
        assertEquals("flormar", flormar.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, flormar.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, flormar.source)
        assertEquals(95, flormar.confidence)

        // 7. golden_rose
        val goldenRose = recognizeUser("Golden Rose")
        assertEquals("golden_rose", goldenRose.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, goldenRose.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, goldenRose.source)
        assertEquals(95, goldenRose.confidence)

        // 8. yves_rocher
        val yvesRocher = recognizeUser("Yves Rocher")
        assertEquals("yves_rocher", yvesRocher.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, yvesRocher.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, yvesRocher.source)
        assertEquals(95, yvesRocher.confidence)

        // 9. the_body_shop
        val theBodyShop = recognizeUser("The Body Shop")
        assertEquals("the_body_shop", theBodyShop.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, theBodyShop.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, theBodyShop.source)
        assertEquals(95, theBodyShop.confidence)
        val bodyShopBank = recognizeBank("POS THE BODY SHOP KADIKOY")
        assertEquals("the_body_shop", bodyShopBank.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bodyShopBank.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, bodyShopBank.source)
        assertEquals(90, bodyShopBank.confidence)

        // 10. kiko_milano
        val kiko = recognizeUser("KIKO Milano")
        assertEquals("kiko_milano", kiko.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, kiko.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, kiko.source)
        assertEquals(95, kiko.confidence)

        // 11. mac_cosmetics
        val macCosmetics = recognizeUser("MAC Cosmetics")
        assertEquals("mac_cosmetics", macCosmetics.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, macCosmetics.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, macCosmetics.source)
        assertEquals(95, macCosmetics.confidence)

        // 12. clinique
        val clinique = recognizeUser("Clinique")
        assertEquals("clinique", clinique.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, clinique.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, clinique.source)
        assertEquals(95, clinique.confidence)

        // 13. avon
        val avonUser = recognizeUser("Avon")
        assertEquals("avon", avonUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, avonUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, avonUser.source)
        assertEquals(95, avonUser.confidence)
        val avonKozmetik = recognizeBank("POS AVON KOZMETIK LEVENT")
        assertEquals("avon", avonKozmetik.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, avonKozmetik.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, avonKozmetik.source)
        assertEquals(90, avonKozmetik.confidence)

        // 14. oriflame
        val oriflame = recognizeUser("Oriflame")
        assertEquals("oriflame", oriflame.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, oriflame.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, oriflame.source)
        assertEquals(95, oriflame.confidence)

        // 15. farmasi
        val farmasi = recognizeUser("Farmasi")
        assertEquals("farmasi", farmasi.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, farmasi.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, farmasi.source)
        assertEquals(95, farmasi.confidence)
    }

    @Test
    fun personalCare_falsePositivePrevention_andGenericTokens() {
        val falsePositivePhrases =
            listOf(
                "eve geldim",
                "mac adresi",
                "body geliştirme antrenmanı",
                "bakım yaptırdım",
                "kozmetik mağazası",
                "klinik randevusu",
            )

        falsePositivePhrases.forEach { phrase ->
            val res = recognizeUser(phrase)
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                res.status,
                "Gündelik / genel ifade '$phrase' hiçbir merchant ile eşleşmemelidir.",
            )
            assertNull(res.merchant)
            assertNull(TransactionMerchantAttributionResolver.resolve(phrase))
        }

        val genericTokens = listOf("body", "shop", "care", "bakım", "kozmetik", "mağaza")
        genericTokens.forEach { token ->
            val res = recognizeUser(token)
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                res.status,
                "Yalın genel kelime '$token' hiçbir merchant ile eşleşmemelidir.",
            )
            assertNull(res.merchant)
            assertNull(TransactionMerchantAttributionResolver.resolve(token))
        }
    }

    @Test
    fun personalCare_shortAliasContract_avon() {
        // 1. Avon tam kullanıcı girdisi (EXACT_ALIAS 95)
        val avonUser = recognizeUser("Avon")
        assertEquals("avon", avonUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, avonUser.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, avonUser.source)
        assertEquals(95, avonUser.confidence)

        // 2. Yalın banka açıklaması (SHORT_ALIAS 65)
        val avonBank = recognizeBank("AVON")
        assertEquals("avon", avonBank.merchant?.id)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, avonBank.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, avonBank.source)
        assertEquals(65, avonBank.confidence)

        // 3. Uzun banka açıklaması alt dize (SHORT_ALIAS 65)
        val avonBankLong = recognizeBank("AVON SUBESI POS")
        assertEquals("avon", avonBankLong.merchant?.id)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, avonBankLong.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, avonBankLong.source)
        assertEquals(65, avonBankLong.confidence)
        assertNull(TransactionMerchantAttributionResolver.resolve("AVON SUBESI POS"))

        // 4. İlgisiz uzun cümle kalıcı attribution üretmemeli
        val unrelatedSentence = "ders calisirken avon nehri hakkinda makale okudum"
        val unrelatedResult = recognizeUser(unrelatedSentence)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, unrelatedResult.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, unrelatedResult.source)
        assertEquals(65, unrelatedResult.confidence)
        assertNull(TransactionMerchantAttributionResolver.resolve(unrelatedSentence))
    }

    @Test
    fun utilities_fifteenNewMerchants_positiveRecognition() {
        // 1. turknet
        val turknet = recognizeUser("TurkNet")
        assertEquals("turknet", turknet.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, turknet.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, turknet.source)
        assertEquals(95, turknet.confidence)

        // 2. millenicom
        val millenicom = recognizeUser("Millenicom")
        assertEquals("millenicom", millenicom.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, millenicom.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, millenicom.source)
        assertEquals(95, millenicom.confidence)

        // 3. netspeed
        val netspeed = recognizeUser("Netspeed")
        assertEquals("netspeed", netspeed.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, netspeed.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, netspeed.source)
        assertEquals(95, netspeed.confidence)

        // 4. turksat_kablo
        val turksatKablo = recognizeUser("Türksat Kablo")
        assertEquals("turksat_kablo", turksatKablo.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, turksatKablo.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, turksatKablo.source)
        assertEquals(95, turksatKablo.confidence)

        val kablonet = recognizeUser("Kablonet")
        assertEquals("turksat_kablo", kablonet.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, kablonet.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, kablonet.source)
        assertEquals(95, kablonet.confidence)

        // 5. comnet
        val comnet = recognizeUser("Comnet")
        assertEquals("comnet", comnet.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, comnet.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, comnet.source)
        assertEquals(95, comnet.confidence)

        // 6. enerjisa
        val enerjisa = recognizeUser("Enerjisa")
        assertEquals("enerjisa", enerjisa.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, enerjisa.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, enerjisa.source)
        assertEquals(95, enerjisa.confidence)

        // 7. ck_enerji
        val ckEnerji = recognizeUser("CK Enerji")
        assertEquals("ck_enerji", ckEnerji.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ckEnerji.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ckEnerji.source)
        assertEquals(95, ckEnerji.confidence)

        // 8. gediz_elektrik
        val gedizElektrik = recognizeUser("Gediz Elektrik")
        assertEquals("gediz_elektrik", gedizElektrik.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, gedizElektrik.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, gedizElektrik.source)
        assertEquals(95, gedizElektrik.confidence)

        // 9. aydem_enerji
        val aydemEnerji = recognizeUser("Aydem Enerji")
        assertEquals("aydem_enerji", aydemEnerji.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, aydemEnerji.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, aydemEnerji.source)
        assertEquals(95, aydemEnerji.confidence)

        // 10. iski
        val iskiSu = recognizeUser("İSKİ Su")
        assertEquals("iski", iskiSu.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, iskiSu.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, iskiSu.source)
        assertEquals(95, iskiSu.confidence)

        val iskiKurum = recognizeBank("POS ISTANBUL SU VE KANALIZASYON IDARESI")
        assertEquals("iski", iskiKurum.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, iskiKurum.status)

        // 11. aski_ankara
        val askiSu = recognizeUser("ASKİ Su")
        assertEquals("aski_ankara", askiSu.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, askiSu.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, askiSu.source)
        assertEquals(95, askiSu.confidence)

        val askiKurum = recognizeBank("ANKARA SU VE KANALIZASYON POS")
        assertEquals("aski_ankara", askiKurum.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, askiKurum.status)

        // 12. izsu
        val izsuSu = recognizeUser("İZSU Su")
        assertEquals("izsu", izsuSu.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, izsuSu.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, izsuSu.source)
        assertEquals(95, izsuSu.confidence)

        val izsuKurum = recognizeBank("IZMIR SU VE KANALIZASYON FATURA")
        assertEquals("izsu", izsuKurum.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, izsuKurum.status)

        // 13. igdas
        val igdasDogalgaz = recognizeUser("İGDAŞ Doğalgaz")
        assertEquals("igdas", igdasDogalgaz.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, igdasDogalgaz.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, igdasDogalgaz.source)
        assertEquals(95, igdasDogalgaz.confidence)

        val igdasKurum = recognizeBank("ISTANBUL GAZ DAGITIM A.S.")
        assertEquals("igdas", igdasKurum.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, igdasKurum.status)

        // 14. baskentgaz
        val baskentgaz = recognizeUser("Başkentgaz")
        assertEquals("baskentgaz", baskentgaz.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, baskentgaz.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, baskentgaz.source)
        assertEquals(95, baskentgaz.confidence)

        // 15. enerya
        val enerya = recognizeUser("Enerya Doğalgaz")
        assertEquals("enerya", enerya.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, enerya.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, enerya.source)
        assertEquals(95, enerya.confidence)
    }

    @Test
    fun utilities_falsePositivePrevention_andGenericTokens() {
        val falsePositivePhrases =
            listOf(
                "askı satın aldım",
                "enerji içeceği aldım",
                "Gediz Nehri gezisi",
                "başkent gezisi",
                "İstanbul su şişesi",
                "Ankara su siparişi",
                "kablo satın aldım",
                "internet faturası",
            )

        falsePositivePhrases.forEach { phrase ->
            val res = recognizeUser(phrase)
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                res.status,
                "Gündelik / genel ifade '$phrase' hiçbir utilities merchant ile eşleşmemelidir.",
            )
            assertNull(res.merchant)
            assertNull(TransactionMerchantAttributionResolver.resolve(phrase))
        }

        val genericTokens =
            listOf(
                "ASKI",
                "SU",
                "GAZ",
                "ENERJI",
                "ELEKTRIK",
                "NET",
                "KABLO",
                "ISTANBUL",
                "ANKARA",
                "IZMIR",
                "GEDIZ",
                "BASKENT",
            )
        genericTokens.forEach { token ->
            val res = recognizeUser(token)
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                res.status,
                "Yalın genel kelime '$token' hiçbir merchant ile eşleşmemelidir.",
            )
            assertNull(res.merchant)
            assertNull(TransactionMerchantAttributionResolver.resolve(token))
        }
    }

    @Test
    fun transportationAndTravel_twentyNewMerchants_positiveRecognition() {
        // --- TRANSPORTATION (10) ---
        // 1. istanbulkart
        val ik1 = recognizeUser("İstanbulkart")
        assertEquals("istanbulkart", ik1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ik1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ik1.source)
        assertEquals(95, ik1.confidence)

        val ik2 = recognizeBank("POS BELBIM ELEKTRONIK PARA")
        assertEquals("istanbulkart", ik2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ik2.status)

        // 2. ispark
        val isp1 = recognizeUser("İSPARK")
        assertEquals("ispark", isp1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, isp1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, isp1.source)
        assertEquals(95, isp1.confidence)

        val isp2 = recognizeUser("İSTANBUL OTOPARK")
        assertEquals("ispark", isp2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, isp2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, isp2.source)
        assertEquals(95, isp2.confidence)

        // 3. tiktak (exact match)
        val tt1 = recognizeUser("TikTak")
        assertEquals("tiktak", tt1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, tt1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, tt1.source)
        assertEquals(95, tt1.confidence)

        val tt2 = recognizeUser("TIKTAK ARAC")
        assertEquals("tiktak", tt2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, tt2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, tt2.source)
        assertEquals(95, tt2.confidence)

        // 4. getir_arac
        val ga1 = recognizeUser("GetirAraç")
        assertEquals("getir_arac", ga1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ga1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ga1.source)
        assertEquals(95, ga1.confidence)

        val ga2 = recognizeUser("MOOV BY GETIR")
        assertEquals("getir_arac", ga2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ga2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ga2.source)
        assertEquals(95, ga2.confidence)

        // 5. kamil_koc
        val kk1 = recognizeUser("Kamil Koç")
        assertEquals("kamil_koc", kk1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, kk1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, kk1.source)
        assertEquals(95, kk1.confidence)

        val kk2 = recognizeUser("KAMIL KOC OTOBUS")
        assertEquals("kamil_koc", kk2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, kk2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, kk2.source)
        assertEquals(95, kk2.confidence)

        // 6. metro_turizm
        val mt1 = recognizeUser("Metro Turizm")
        assertEquals("metro_turizm", mt1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mt1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, mt1.source)
        assertEquals(95, mt1.confidence)

        val mt2 = recognizeBank("METRO TURIZM TASIMACILIK POS")
        assertEquals("metro_turizm", mt2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mt2.status)

        // 7. pamukkale_turizm
        val pt1 = recognizeUser("Pamukkale Turizm")
        assertEquals("pamukkale_turizm", pt1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, pt1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, pt1.source)
        assertEquals(95, pt1.confidence)

        val pt2 = recognizeUser("PAMUKKALE SEYAHAT")
        assertEquals("pamukkale_turizm", pt2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, pt2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, pt2.source)
        assertEquals(95, pt2.confidence)

        // 8. varan_turizm
        val vt1 = recognizeUser("Varan Turizm")
        assertEquals("varan_turizm", vt1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, vt1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, vt1.source)
        assertEquals(95, vt1.confidence)

        val vt2 = recognizeUser("VARAN SEYAHAT")
        assertEquals("varan_turizm", vt2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, vt2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, vt2.source)
        assertEquals(95, vt2.confidence)

        // 9. ido
        val ido1 = recognizeUser("İDO")
        assertEquals("ido", ido1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ido1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ido1.source)
        assertEquals(95, ido1.confidence)

        val ido2 = recognizeUser("IDO DENIZ OTOBUSLERI")
        assertEquals("ido", ido2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ido2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ido2.source)
        assertEquals(95, ido2.confidence)

        // 10. budo
        val budo1 = recognizeUser("BUDO")
        assertEquals("budo", budo1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, budo1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, budo1.source)
        assertEquals(95, budo1.confidence)

        val budo2 = recognizeBank("BURSA DENIZ OTOBUSLERI POS")
        assertEquals("budo", budo2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, budo2.status)

        // --- TRAVEL (10) ---
        // 11. ajet
        val aj1 = recognizeUser("AJet")
        assertEquals("ajet", aj1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, aj1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, aj1.source)
        assertEquals(95, aj1.confidence)

        val aj2 = recognizeUser("ANADOLUJET")
        assertEquals("ajet", aj2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, aj2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, aj2.source)
        assertEquals(95, aj2.confidence)

        // 12. corendon
        val cr1 = recognizeUser("Corendon")
        assertEquals("corendon", cr1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, cr1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, cr1.source)
        assertEquals(95, cr1.confidence)

        val cr2 = recognizeUser("Corendon Airlines")
        assertEquals("corendon", cr2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, cr2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, cr2.source)
        assertEquals(95, cr2.confidence)

        // 13. obilet
        val ob1 = recognizeUser("obilet")
        assertEquals("obilet", ob1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ob1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ob1.source)
        assertEquals(95, ob1.confidence)

        val ob2 = recognizeBank("OBILET BILISIM POS")
        assertEquals("obilet", ob2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ob2.status)

        // 14. enuygun
        val en1 = recognizeUser("Enuygun")
        assertEquals("enuygun", en1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, en1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, en1.source)
        assertEquals(95, en1.confidence)

        val en2 = recognizeBank("WINGIE ENUYGUN POS")
        assertEquals("enuygun", en2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, en2.status)

        // 15. ets_tur
        val ets1 = recognizeUser("Etstur")
        assertEquals("ets_tur", ets1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ets1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ets1.source)
        assertEquals(95, ets1.confidence)

        val ets2 = recognizeUser("ETS TUR")
        assertEquals("ets_tur", ets2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ets2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ets2.source)
        assertEquals(95, ets2.confidence)

        // 16. jolly_tur
        val jt1 = recognizeUser("Jolly Tur")
        assertEquals("jolly_tur", jt1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, jt1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, jt1.source)
        assertEquals(95, jt1.confidence)

        val jt2 = recognizeUser("Jolly")
        assertEquals("jolly_tur", jt2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, jt2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, jt2.source)
        assertEquals(95, jt2.confidence)

        // 17. tatilbudur
        val tb1 = recognizeUser("TatilBudur")
        assertEquals("tatilbudur", tb1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, tb1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, tb1.source)
        assertEquals(95, tb1.confidence)

        val tb2 = recognizeUser("TATILBUDUR SEYAHAT")
        assertEquals("tatilbudur", tb2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, tb2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, tb2.source)
        assertEquals(95, tb2.confidence)

        // 18. setur
        val st1 = recognizeUser("Setur")
        assertEquals("setur", st1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, st1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, st1.source)
        assertEquals(95, st1.confidence)

        val st2 = recognizeUser("SETUR TURIZM")
        assertEquals("setur", st2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, st2.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, st2.source)
        assertEquals(95, st2.confidence)

        // 19. airbnb
        val ab1 = recognizeUser("Airbnb")
        assertEquals("airbnb", ab1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ab1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, ab1.source)
        assertEquals(95, ab1.confidence)

        val ab2 = recognizeBank("AIRBNB IRELAND POS")
        assertEquals("airbnb", ab2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ab2.status)

        // 20. booking
        val bk1 = recognizeUser("Booking.com")
        assertEquals("booking", bk1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bk1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, bk1.source)
        assertEquals(95, bk1.confidence)

        val bk2 = recognizeBank("BOOKING BV POS")
        assertEquals("booking", bk2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bk2.status)
    }

    @Test
    fun transportationAndTravel_martiAndTiktakShortRegressions() {
        // 1. MARTI USER_ENTERED exact -> 95 EXACT_ALIAS / MATCHED & resolver non-null
        val martiExact = recognizeUser("MARTI")
        assertEquals("marti", martiExact.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, martiExact.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, martiExact.source)
        assertEquals(95, martiExact.confidence)
        val martiInfo = TransactionMerchantAttributionResolver.resolve("MARTI")
        assertNotNull(martiInfo)
        assertEquals("marti", martiInfo.merchantId.value)
        assertEquals(95, martiInfo.confidence)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, martiInfo.matchSource)
        assertEquals(26, martiInfo.catalogVersion)

        // 2. MARTI USER_ENTERED sentence -> 65 SHORT_ALIAS / LOW_CONFIDENCE & resolver null
        val martiSentence = recognizeUser("martı gördüm")
        assertEquals("marti", martiSentence.merchant?.id)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, martiSentence.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, martiSentence.source)
        assertEquals(65, martiSentence.confidence)
        assertFalse(martiSentence.canShowMerchantLogo)
        assertNull(TransactionMerchantAttributionResolver.resolve("martı gördüm"))

        // 3. MARTI TAG exact -> 95 EXACT_ALIAS; MARTI TAG sentence -> 90 BRAND_AND_SERVICE
        val martiTagExact = recognizeUser("MARTI TAG")
        assertEquals("marti", martiTagExact.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, martiTagExact.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, martiTagExact.source)
        assertEquals(95, martiTagExact.confidence)

        val martiTagSentence = recognizeUser("MARTI TAG YOLCULUK")
        assertEquals("marti", martiTagSentence.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, martiTagSentence.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, martiTagSentence.source)
        assertEquals(90, martiTagSentence.confidence)
        assertNotNull(TransactionMerchantAttributionResolver.resolve("MARTI TAG YOLCULUK"))

        // 4. MARTI SCOOTER exact -> 95 EXACT_ALIAS; MARTI SCOOTER sentence -> 90 BRAND_AND_SERVICE
        val martiScooterExact = recognizeUser("MARTI SCOOTER")
        assertEquals("marti", martiScooterExact.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, martiScooterExact.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, martiScooterExact.source)
        assertEquals(95, martiScooterExact.confidence)

        val martiScooterSentence = recognizeUser("MARTI SCOOTER SURUSU")
        assertEquals("marti", martiScooterSentence.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, martiScooterSentence.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, martiScooterSentence.source)
        assertEquals(90, martiScooterSentence.confidence)
        assertNotNull(TransactionMerchantAttributionResolver.resolve("MARTI SCOOTER SURUSU"))

        // 5. MARTI BANK_DESCRIPTION -> 65 SHORT_ALIAS / LOW_CONFIDENCE
        val martiBank = recognizeBank("MARTI")
        assertEquals("marti", martiBank.merchant?.id)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, martiBank.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, martiBank.source)
        assertEquals(65, martiBank.confidence)

        val martiBankExtra = recognizeBank("MARTI ELEKTRONIK")
        assertEquals("marti", martiBankExtra.merchant?.id)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, martiBankExtra.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, martiBankExtra.source)
        assertEquals(65, martiBankExtra.confidence)

        // 6. TIKTAK USER_ENTERED exact -> 95 EXACT_ALIAS / MATCHED & resolver non-null
        val tiktakExact = recognizeUser("TIKTAK")
        assertEquals("tiktak", tiktakExact.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, tiktakExact.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, tiktakExact.source)
        assertEquals(95, tiktakExact.confidence)
        val tiktakInfo = TransactionMerchantAttributionResolver.resolve("TIKTAK")
        assertNotNull(tiktakInfo)
        assertEquals("tiktak", tiktakInfo.merchantId.value)
        assertEquals(95, tiktakInfo.confidence)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, tiktakInfo.matchSource)
        assertEquals(26, tiktakInfo.catalogVersion)

        // 7. TIKTAK USER_ENTERED sentence -> 65 SHORT_ALIAS / LOW_CONFIDENCE & resolver null
        val tiktakSentence = recognizeUser("tiktak sesi duydum")
        assertEquals("tiktak", tiktakSentence.merchant?.id)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, tiktakSentence.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, tiktakSentence.source)
        assertEquals(65, tiktakSentence.confidence)
        assertFalse(tiktakSentence.canShowMerchantLogo)
        assertNull(TransactionMerchantAttributionResolver.resolve("tiktak sesi duydum"))

        // 8. TIKTAK ARAC KIRALAMA sentence -> 90 BRAND_AND_SERVICE / MATCHED & resolver non-null
        val tiktakKiralama = recognizeUser("TIKTAK ARAC KIRALAMA")
        assertEquals("tiktak", tiktakKiralama.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, tiktakKiralama.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, tiktakKiralama.source)
        assertEquals(90, tiktakKiralama.confidence)
        assertNotNull(TransactionMerchantAttributionResolver.resolve("TIKTAK ARAC KIRALAMA"))

        // 9. TIKTAK BANK_DESCRIPTION -> 65 SHORT_ALIAS / LOW_CONFIDENCE
        val tiktakBank = recognizeBank("TIKTAK")
        assertEquals("tiktak", tiktakBank.merchant?.id)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, tiktakBank.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, tiktakBank.source)
        assertEquals(65, tiktakBank.confidence)
    }

    @Test
    fun transportationAndTravel_metroTurkiye_and_metroTurizm_separation() {
        // 1. Çıplak "metro ile işe gittim" -> NO_MATCH (katalogda çıplak METRO yok)
        val bareMetro = recognizeUser("metro ile işe gittim")
        assertEquals(MerchantMatchStatus.NO_MATCH, bareMetro.status)
        assertNull(bareMetro.merchant)
        assertNull(TransactionMerchantAttributionResolver.resolve("metro ile işe gittim"))

        // 2. "METRO GROSSMARKET" exact -> 95 EXACT_ALIAS metro_turkiye
        val grossmarketExact = recognizeUser("METRO GROSSMARKET")
        assertEquals("metro_turkiye", grossmarketExact.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, grossmarketExact.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, grossmarketExact.source)
        assertEquals(95, grossmarketExact.confidence)

        // 3. "METRO TURKIYE" exact -> 95 EXACT_ALIAS metro_turkiye
        val turkiyeExact = recognizeUser("METRO TURKIYE")
        assertEquals("metro_turkiye", turkiyeExact.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, turkiyeExact.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, turkiyeExact.source)
        assertEquals(95, turkiyeExact.confidence)

        // 4. "METRO GROSSMARKET ALISVERIS" sentence -> 80 STRONG_BRAND metro_turkiye
        val grossmarketSentence = recognizeUser("METRO GROSSMARKET ALISVERIS")
        assertEquals("metro_turkiye", grossmarketSentence.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, grossmarketSentence.status)
        assertEquals(MerchantMatchSource.STRONG_BRAND, grossmarketSentence.source)
        assertEquals(80, grossmarketSentence.confidence)

        // 5. "METRO TURIZM" exact -> 95 EXACT_ALIAS metro_turizm
        val turizmExact = recognizeUser("METRO TURIZM")
        assertEquals("metro_turizm", turizmExact.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, turizmExact.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, turizmExact.source)
        assertEquals(95, turizmExact.confidence)

        // 6. "METRO TURIZM BILET" sentence -> 90 BRAND_AND_SERVICE metro_turizm
        val turizmSentence = recognizeUser("METRO TURIZM BILET")
        assertEquals("metro_turizm", turizmSentence.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, turizmSentence.status)
        assertEquals(MerchantMatchSource.BRAND_AND_SERVICE, turizmSentence.source)
        assertEquals(90, turizmSentence.confidence)

        // 7. İki merchant arasında AMBIGUOUS oluşmaz
        assertFalse(grossmarketExact.status == MerchantMatchStatus.AMBIGUOUS)
        assertFalse(turizmExact.status == MerchantMatchStatus.AMBIGUOUS)
    }

    @Test
    fun transportationAndTravel_jollyTur_negativeAlias_jollyJokerPrevention() {
        // "JOLLY JOKER" girdisi jolly_tur üretmemelidir (negative alias JOKER)
        val joker = recognizeUser("JOLLY JOKER")
        assertEquals(MerchantMatchStatus.NO_MATCH, joker.status)
        assertNull(joker.merchant)
        assertNull(TransactionMerchantAttributionResolver.resolve("JOLLY JOKER"))

        val jokerBank = recognizeBank("POS JOLLY JOKER ISTANBUL")
        assertEquals(MerchantMatchStatus.NO_MATCH, jokerBank.status)
        assertNull(jokerBank.merchant)

        // Normal Jolly Tur pozitif eşleşir
        val jt1 = recognizeUser("Jolly Tur")
        assertEquals("jolly_tur", jt1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, jt1.status)

        val jt2 = recognizeUser("Jolly")
        assertEquals("jolly_tur", jt2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, jt2.status)
    }

    @Test
    fun transportationAndTravel_varanTurizm_verbFalsePositivePrevention() {
        // "sonuca varan adımlar" ifadesi varan_turizm üretmemelidir (çıplak VARAN alias'ı yok)
        val verb = recognizeUser("sonuca varan adımlar")
        assertEquals(MerchantMatchStatus.NO_MATCH, verb.status)
        assertNull(verb.merchant)
        assertNull(TransactionMerchantAttributionResolver.resolve("sonuca varan adımlar"))

        // VARAN TURIZM pozitif eşleşir
        val vt = recognizeUser("VARAN TURIZM")
        assertEquals("varan_turizm", vt.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, vt.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, vt.source)
        assertEquals(95, vt.confidence)
    }

    @Test
    fun transportationAndTravel_falsePositivePrevention_andGenericTokens() {
        // 1. GETIR tek başına getir_arac üretmemelidir (getir üretmeli veya getir_arac üretmemelidir)
        val getirResult = recognizeUser("GETIR")
        if (getirResult.merchant != null) {
            assertEquals("getir", getirResult.merchant?.id)
        }

        // 2. EN UYGUN veya UYGUN tek başına enuygun üretmemelidir
        val enUygun = recognizeUser("EN UYGUN")
        assertEquals(MerchantMatchStatus.NO_MATCH, enUygun.status)
        assertNull(enUygun.merchant)
        assertNull(TransactionMerchantAttributionResolver.resolve("EN UYGUN"))

        val uygun = recognizeUser("UYGUN")
        assertEquals(MerchantMatchStatus.NO_MATCH, uygun.status)
        assertNull(uygun.merchant)
        assertNull(TransactionMerchantAttributionResolver.resolve("UYGUN"))

        // 3. Pamukkale travertenleri / Pamukkale universitesi pamukkale_turizm üretmemelidir
        val pamukkale1 = recognizeUser("Pamukkale travertenleri")
        assertEquals(MerchantMatchStatus.NO_MATCH, pamukkale1.status)
        assertNull(pamukkale1.merchant)
        assertNull(TransactionMerchantAttributionResolver.resolve("Pamukkale travertenleri"))

        val pamukkale2 = recognizeUser("Pamukkale universitesi")
        assertEquals(MerchantMatchStatus.NO_MATCH, pamukkale2.status)
        assertNull(pamukkale2.merchant)
        assertNull(TransactionMerchantAttributionResolver.resolve("Pamukkale universitesi"))

        // 4. Jenerik tokenlar NO_MATCH üretmelidir
        val genericTokens =
            listOf(
                "BILET",
                "TUR",
                "OTEL",
                "HOTEL",
                "TAKSI",
                "OTOBUS",
                "METRO",
                "UCAK",
            )
        genericTokens.forEach { token ->
            val res = recognizeUser(token)
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                res.status,
                "Jenerik kelime '$token' NO_MATCH üretmelidir.",
            )
            assertNull(res.merchant)
            assertNull(TransactionMerchantAttributionResolver.resolve(token))
        }
    }

    @Test
    fun catalogV26_attributionsCarryVersionTwentySixContract() {
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        assertTrue(merchants.all { it.catalogVersion == 26 }, "All 260 merchants must default to catalogVersion 26")

        val tk = TransactionMerchantAttributionResolver.resolve("TARIM KREDI")
        assertNotNull(tk)
        assertEquals("tarim_kredi", tk.merchantId.value)
        assertEquals(26, tk.catalogVersion)

        val getir = TransactionMerchantAttributionResolver.resolve("GETIR")
        assertNotNull(getir)
        assertEquals("getir", getir.merchantId.value)
        assertEquals(26, getir.catalogVersion)

        val netflix = TransactionMerchantAttributionResolver.resolve("NETFLIX")
        assertNotNull(netflix)
        assertEquals("netflix", netflix.merchantId.value)
        assertEquals(26, netflix.catalogVersion)
    }

    @Test
    fun subscriptionsCatalogIntegrity_currentV18Contract() {
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)
        assertEquals(260, merchants.map { it.id }.distinct().size)

        val directTenFromNewSixteen =
            listOf(
                "s_sport_plus",
                "tv_plus",
                "tivibu",
                "d_smart_go",
                "deezer",
                "fizy",
                "muud",
                "storytel",
                "audible",
                "dropbox",
            )

        directTenFromNewSixteen.forEach { id ->
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "$id katalogda bulunmalıdır")
            assertEquals("subscriptions", merchant.categoryIconKey.key, "$id category subscriptions olmalıdır")
            assertEquals(id, merchant.verifiedLogoKey, "$id verifiedLogoKey kendi id'si olmalıdır")
        }

        val sharedTwelveSubscriptionKeys =
            mapOf(
                "youtube_premium" to "youtube",
                "amazon_prime" to "amazon",
                "google_one" to "google",
                "microsoft_365" to "microsoft",
                "playstation_plus" to "playstation",
                "xbox_game_pass" to "xbox",
                "linkedin_premium" to "linkedin",
                "nintendo_switch_online" to "nintendo",
                "ea_play" to "ea",
                "hepsiburada_premium" to "hepsiburada",
                "money_gold" to "migros",
                "ypro" to "yemeksepeti",
            )

        sharedTwelveSubscriptionKeys.forEach { (id, expectedKey) ->
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "$id katalogda bulunmalıdır")
            assertEquals("subscriptions", merchant.categoryIconKey.key, "$id category subscriptions olmalıdır")
            assertEquals(expectedKey, merchant.verifiedLogoKey, "$id verifiedLogoKey çatı anahtarı $expectedKey olmalıdır")
        }

        // Toplam doğrulanmış logo sayısı 223, fallback sayısı 37 ve allowlist 217 oldu
        assertEquals(223, merchants.count { it.verifiedLogoKey != null })
        assertEquals(37, merchants.count { it.verifiedLogoKey == null })
        assertEquals(217, MerchantLogoDomainAllowlist.size())
    }

    @Test
    fun shoppingCatalogIntegrity_currentV18Contract() {
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)

        val shoppingMerchants = merchants.filter { it.categoryIconKey.key == "shopping" }
        assertEquals(31, shoppingMerchants.size, "Toplam 31 alışveriş merchant'ı olmalıdır")
        assertEquals(
            31,
            shoppingMerchants.count { it.verifiedLogoKey != null },
            "31 alışveriş merchant'ının tamamı verifiedLogoKey taşımalıdır",
        )
        assertEquals(
            0,
            shoppingMerchants.count { it.verifiedLogoKey == null },
            "Alışveriş kategorisinde fallback merchant sayısı 0 olmalıdır",
        )

        val newTwentyThreeShoppingKeys =
            listOf(
                "n11",
                "ciceksepeti",
                "pazarama",
                "boyner",
                "lc_waikiki",
                "defacto",
                "teknosa",
                "mediamarkt",
                "flo",
                "koton",
                "mavi",
                "zara",
                "hm",
                "pull_and_bear",
                "bershka",
                "stradivarius",
                "beymen",
                "vakko",
                "ikea",
                "koctas",
                "bauhaus",
                "vatan_bilgisayar",
                "ebebek",
            )

        newTwentyThreeShoppingKeys.forEach { id ->
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Alışveriş merchant'ı '$id' katalogda bulunmalıdır")
            assertEquals("shopping", merchant.categoryIconKey.key, "Kategori 'shopping' olmalıdır")
            assertEquals(id, merchant.verifiedLogoKey, "verifiedLogoKey kendi stable ID'si '$id' olmalıdır")
        }
    }

    @Test
    fun personalCareCatalogIntegrity_currentV18Contract() {
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)

        val personalCareMerchants = merchants.filter { it.categoryIconKey.key == "personal_care" }
        assertEquals(15, personalCareMerchants.size, "Toplam 15 kişisel bakım merchant'ı olmalıdır")
        assertEquals(
            15,
            personalCareMerchants.count { it.verifiedLogoKey != null },
            "15 kişisel bakım merchant'ının tamamı verifiedLogoKey taşımalıdır",
        )
        assertEquals(
            0,
            personalCareMerchants.count { it.verifiedLogoKey == null },
            "Kişisel bakım kategorisinde fallback merchant sayısı 0 olmalıdır",
        )

        val personalCareKeys =
            listOf(
                "gratis",
                "watsons",
                "rossmann",
                "sephora",
                "eve_shop",
                "flormar",
                "golden_rose",
                "yves_rocher",
                "the_body_shop",
                "kiko_milano",
                "mac_cosmetics",
                "clinique",
                "avon",
                "oriflame",
                "farmasi",
            )

        personalCareKeys.forEach { id ->
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Kişisel bakım merchant'ı '$id' katalogda bulunmalıdır")
            assertEquals("personal_care", merchant.categoryIconKey.key, "Kategori 'personal_care' olmalıdır")
            assertEquals(id, merchant.verifiedLogoKey, "verifiedLogoKey kendi stable ID'si '$id' olmalıdır")
        }
    }

    @Test
    fun transportationAndTravelCatalogIntegrity_v16Contract() {
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)

        val transportationMerchants = merchants.filter { it.categoryIconKey.key == "transportation" }
        assertEquals(14, transportationMerchants.size, "Toplam 14 ulaşım merchant'ı olmalıdır")
        assertEquals(10, transportationMerchants.count { it.verifiedLogoKey != null }, "Ulaşım kategorisinde 10 doğrulanmış logo olmalıdır")
        assertEquals(4, transportationMerchants.count { it.verifiedLogoKey == null }, "Ulaşım kategorisinde 4 fallback merchant olmalıdır")

        val travelMerchants = merchants.filter { it.categoryIconKey.key == "travel" }
        assertEquals(13, travelMerchants.size, "Toplam 13 seyahat merchant'ı olmalıdır")
        assertEquals(12, travelMerchants.count { it.verifiedLogoKey != null }, "Seyahat kategorisinde 12 doğrulanmış logo olmalıdır")
        assertEquals(1, travelMerchants.count { it.verifiedLogoKey == null }, "Seyahat kategorisinde 1 fallback merchant olmalıdır")

        val combinedPool = transportationMerchants + travelMerchants
        assertEquals(27, combinedPool.size, "Ulaşım ve seyahat birleşik havuzunda 27 merchant olmalıdır")
        assertEquals(22, combinedPool.count { it.verifiedLogoKey != null }, "Birleşik havuzda 22 doğrulanmış logo olmalıdır")
        assertEquals(5, combinedPool.count { it.verifiedLogoKey == null }, "Birleşik havuzda 5 fallback merchant olmalıdır")

        val newSeventeenKeys =
            listOf(
                "istanbulkart",
                "tcdd",
                "bitaksi",
                "marti",
                "ispark",
                "metro_turizm",
                "pamukkale_turizm",
                "varan_turizm",
                "ido",
                "ajet",
                "sunexpress",
                "corendon",
                "booking",
                "airbnb",
                "ets_tur",
                "jolly_tur",
                "setur",
            )

        newSeventeenKeys.forEach { id ->
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Ulaşım/Seyahat merchant'ı '$id' katalogda bulunmalıdır")
            assertEquals(id, merchant.verifiedLogoKey, "verifiedLogoKey kendi stable ID'si '$id' olmalıdır")
        }

        val deliberateFallbacks = listOf("kamil_koc", "budo", "getir_arac", "tiktak", "tatilbudur")
        deliberateFallbacks.forEach { id ->
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Fallback merchant '$id' katalogda bulunmalıdır")
            assertNull(merchant.verifiedLogoKey, "Fallback merchant '$id' verifiedLogoKey taşımamalıdır")
        }
    }

    @Test
    fun subscriptionsParentServiceSeparationContract() {
        // Hepsiburada vs Hepsiburada Premium
        val hbShopping = recognizeUser("Hepsiburada siparişi")
        assertEquals("hepsiburada", hbShopping.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, hbShopping.status)

        val hbPremium = recognizeUser("Hepsiburada Premium üyeliği")
        assertEquals("hepsiburada_premium", hbPremium.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, hbPremium.status)

        // Yemeksepeti vs Ypro
        val ysFood = recognizeUser("Yemeksepeti lahmacun")
        assertEquals("yemeksepeti", ysFood.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ysFood.status)

        val ysYpro = recognizeUser("Yemeksepeti Ypro")
        assertEquals("ypro", ysYpro.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ysYpro.status)

        // Migros vs Money Gold
        val migrosSanal = recognizeUser("Migros Sanal Market alışverişi")
        assertEquals("migros_sanal_market", migrosSanal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, migrosSanal.status)

        val migrosGold = recognizeUser("Migros Money Gold üyeliği")
        assertEquals("money_gold", migrosGold.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, migrosGold.status)
    }

    @Test
    fun subscriptionsTelekomParentSeparationContract() {
        // 1. TURKCELL TV PLUS -> tv_plus
        val tvPlus = recognizeBank("TURKCELL TV PLUS")
        assertEquals("tv_plus", tvPlus.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, tvPlus.status)

        // 2. TURKCELL FIZY -> fizy
        val fizy = recognizeBank("TURKCELL FIZY")
        assertEquals("fizy", fizy.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, fizy.status)

        // 3. TURK TELEKOM MUUD -> muud
        val muud = recognizeBank("TURK TELEKOM MUUD")
        assertEquals("muud", muud.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, muud.status)

        // 4. TTNET TIVIBU -> tivibu
        val tivibuTtnet = recognizeBank("TTNET TIVIBU")
        assertEquals("tivibu", tivibuTtnet.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, tivibuTtnet.status)

        // 5. TURK TELEKOM TIVIBU -> tivibu
        val tivibuTt = recognizeBank("TURK TELEKOM TIVIBU")
        assertEquals("tivibu", tivibuTt.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, tivibuTt.status)
    }

    @Test
    fun subscriptionsShortAliasSourceBehaviorContract() {
        // USER_ENTERED_DESCRIPTION tam eşleşme: 95 / MATCHED
        val yproUser = recognizeUser("Ypro")
        assertEquals("ypro", yproUser.merchant?.id)
        assertEquals(95, yproUser.confidence)
        assertEquals(MerchantMatchStatus.MATCHED, yproUser.status)

        val fizyUser = recognizeUser("Fizy")
        assertEquals("fizy", fizyUser.merchant?.id)
        assertEquals(95, fizyUser.confidence)
        assertEquals(MerchantMatchStatus.MATCHED, fizyUser.status)

        val muudUser = recognizeUser("Muud")
        assertEquals("muud", muudUser.merchant?.id)
        assertEquals(95, muudUser.confidence)
        assertEquals(MerchantMatchStatus.MATCHED, muudUser.status)

        // RAW_BANK_DESCRIPTION birleşik hizmet: 90 / MATCHED
        val yproBankCombined = recognizeBank("POS YEMEKSEPETI YPRO")
        assertEquals("ypro", yproBankCombined.merchant?.id)
        assertEquals(90, yproBankCombined.confidence)
        assertEquals(MerchantMatchStatus.MATCHED, yproBankCombined.status)

        val fizyBankCombined = recognizeBank("POS TURKCELL FIZY")
        assertEquals("fizy", fizyBankCombined.merchant?.id)
        assertEquals(90, fizyBankCombined.confidence)
        assertEquals(MerchantMatchStatus.MATCHED, fizyBankCombined.status)

        val muudBankCombined = recognizeBank("POS TURK TELEKOM MUUD")
        assertEquals("muud", muudBankCombined.merchant?.id)
        assertEquals(90, muudBankCombined.confidence)
        assertEquals(MerchantMatchStatus.MATCHED, muudBankCombined.status)

        // RAW_BANK_DESCRIPTION içinde yalnız kısa token: 65 / LOW_CONFIDENCE
        val yproBankToken = recognizeBank("POS YPRO ISTANBUL")
        assertEquals(65, yproBankToken.confidence)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, yproBankToken.status)

        val fizyBankToken = recognizeBank("BK FIZY ABONELIK")
        assertEquals(65, fizyBankToken.confidence)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, fizyBankToken.status)

        val muudBankToken = recognizeBank("POS MUUD ODEMESI")
        assertEquals(65, muudBankToken.confidence)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, muudBankToken.status)
    }

    @Test
    fun subscriptionsFalsePositiveProtectionContract() {
        // Negatif senaryolar: ilgili abonelik merchant'ına MATCHED olmamalı
        val nintendoStore = recognizeUser("Nintendo mağaza alışverişi")
        assertNull(nintendoStore.merchant, "Nintendo mağaza alışverişi nintendo_switch_online ile eşleşmemeli")

        val nintendoGame = recognizeUser("Nintendo oyun satın alma")
        assertNull(nintendoGame.merchant, "Nintendo oyun alımı nintendo_switch_online ile eşleşmemeli")

        val linkedinCorp = recognizeUser("LinkedIn kurumsal fatura")
        assertNull(linkedinCorp.merchant, "LinkedIn kurumsal fatura linkedin_premium ile eşleşmemeli")

        val linkedinJob = recognizeUser("LinkedIn iş ilanı")
        assertNull(linkedinJob.merchant, "LinkedIn iş ilanı linkedin_premium ile eşleşmemeli")

        val eaGame1 = recognizeUser("Electronic Arts oyun satın alma")
        assertNull(eaGame1.merchant, "Electronic Arts oyun alımı ea_play ile eşleşmemeli")

        val eaGame2 = recognizeUser("EA oyun satın alma")
        assertNull(eaGame2.merchant, "EA oyun alımı ea_play ile eşleşmemeli")

        val dsmartSat = recognizeUser("D Smart uydu")
        assertNull(dsmartSat.merchant, "D Smart uydu d_smart_go ile eşleşmemeli")

        val dsmartNet = recognizeUser("D Smart internet")
        assertNull(dsmartNet.merchant, "D Smart internet d_smart_go ile eşleşmemeli")

        // Pozitif ürün-özgül girdiler MATCHED olmalı
        val nso = recognizeUser("Nintendo Switch Online")
        assertEquals("nintendo_switch_online", nso.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, nso.status)

        val liPrem = recognizeUser("LinkedIn Premium")
        assertEquals("linkedin_premium", liPrem.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, liPrem.status)

        val eaPlay = recognizeUser("EA Play üyeliği")
        assertEquals("ea_play", eaPlay.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, eaPlay.status)

        val dsmartGo = recognizeUser("D Smart GO")
        assertEquals("d_smart_go", dsmartGo.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, dsmartGo.status)
    }

    @Test
    fun entertainmentCatalogIntegrity_v18Contract() {
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)

        val entertainmentMerchants = merchants.filter { it.categoryIconKey.key == "entertainment" }
        assertEquals(17, entertainmentMerchants.size, "Toplam 17 eğlence merchant'ı olmalıdır")
        assertEquals(
            17,
            entertainmentMerchants.count { it.verifiedLogoKey != null },
            "Eğlence kategorisinde uzak logo sayısı 17 olmalıdır",
        )
        assertEquals(
            0,
            entertainmentMerchants.count { it.verifiedLogoKey == null },
            "Eğlence kategorisinde fallback merchant sayısı 0 olmalıdır",
        )

        val entertainmentKeys =
            listOf(
                "paribu_cineverse",
                "biletix",
                "steam",
                "biletinial",
                "bubilet",
                "passo",
                "mobilet",
                "biletino",
                "bugece",
                "epic_games",
                "playstation_store",
                "xbox_store",
                "riot_games",
                "battle_net",
                "ubisoft_store",
                "cinemarine",
                "vialand",
            )
        entertainmentKeys.forEach { id ->
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Eğlence merchant'ı '$id' katalogda bulunmalıdır")
            assertEquals("entertainment", merchant.categoryIconKey.key, "Kategori 'entertainment' olmalıdır")
            val expectedLogoKey =
                when (id) {
                    "playstation_store" -> "playstation"
                    "xbox_store" -> "xbox"
                    else -> id
                }
            assertEquals(expectedLogoKey, merchant.verifiedLogoKey, "verifiedLogoKey '$expectedLogoKey' olmalıdır")
            assertNotNull(MerchantLogoDomainAllowlist.resolveDomain(merchant.verifiedLogoKey), "verifiedLogoKey allowlist'te çözülmelidir")
        }
    }

    @Test
    fun entertainmentMerchantRecognition_positiveAndNegativeContract() {
        // Sentetik pozitifler — Mevcut çekirdek 3
        val paribu1 = recognizeUser("PARIBU CINEVERSE KANYON")
        assertEquals("paribu_cineverse", paribu1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, paribu1.status)
        assertTrue(paribu1.confidence >= 75)

        val paribu2 = recognizeUser("CINEVERSE AKASYA")
        assertEquals("paribu_cineverse", paribu2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, paribu2.status)
        assertTrue(paribu2.confidence >= 75)

        val paribu3 = recognizeBank("CGV MARS SINEMA ISTANBUL")
        assertEquals("paribu_cineverse", paribu3.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, paribu3.status)

        val paribu4 = recognizeUser("CINEMAXIMUM ANKARA ANKMOLL")
        assertEquals("paribu_cineverse", paribu4.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, paribu4.status)

        val biletix1 = recognizeUser("BILETIX ISTANBUL")
        assertEquals("biletix", biletix1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, biletix1.status)
        assertTrue(biletix1.confidence >= 75)

        val biletix2 = recognizeBank("BILETIX BILET DAGITIM A.S.")
        assertEquals("biletix", biletix2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, biletix2.status)

        val steam1 = recognizeUser("VALVE STEAM GAMES")
        assertEquals("steam", steam1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, steam1.status)
        assertTrue(steam1.confidence >= 75)

        val steam2 = recognizeBank("STEAMPOWERED.COM ORDER")
        assertEquals("steam", steam2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, steam2.status)
        assertTrue(steam2.confidence >= 75)

        val steam3 = recognizeBank("VALVE SOFTWARE BELLEVUE")
        assertEquals("steam", steam3.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, steam3.status)

        // Sentetik pozitifler — Yeni 14 aday
        val biletinial1 = recognizeUser("BILETINIAL SINEMA BILETI")
        assertEquals("biletinial", biletinial1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, biletinial1.status)
        assertTrue(biletinial1.confidence >= 75)

        val bubilet1 = recognizeUser("BUBILET KONSER BILETI")
        assertEquals("bubilet", bubilet1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bubilet1.status)
        assertTrue(bubilet1.confidence >= 75)

        val bubilet2 = recognizeBank("BUBILET BILET BASIM DAGITIM")
        assertEquals("bubilet", bubilet2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bubilet2.status)

        val passo1 = recognizeUser("PASSO MAC BILETI")
        assertEquals("passo", passo1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, passo1.status)
        assertTrue(passo1.confidence >= 75)

        val passo2 = recognizeBank("PASSO DIJITAL HIZMETLER A.S.")
        assertEquals("passo", passo2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, passo2.status)

        val mobilet1 = recognizeUser("MOBILET TIYATRO BILETI")
        assertEquals("mobilet", mobilet1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mobilet1.status)
        assertTrue(mobilet1.confidence >= 75)

        val mobilet2 = recognizeBank("EVENT MASTER MOBILET")
        assertEquals("mobilet", mobilet2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mobilet2.status)

        val biletino1 = recognizeUser("BILETINO FESTIVAL GIRIS")
        assertEquals("biletino", biletino1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, biletino1.status)
        assertTrue(biletino1.confidence >= 75)

        val bugece1 = recognizeUser("BUGECE ETKINLIK")
        assertEquals("bugece", bugece1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bugece1.status)
        assertTrue(bugece1.confidence >= 75)

        val epic1 = recognizeUser("EPIC GAMES STORE OYUN")
        assertEquals("epic_games", epic1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, epic1.status)
        assertTrue(epic1.confidence >= 75)

        val psStore1 = recognizeUser("PLAYSTATION STORE OYUN ALIMI")
        assertEquals("playstation_store", psStore1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, psStore1.status)
        assertTrue(psStore1.confidence >= 75)

        val xboxStore1 = recognizeUser("XBOX STORE INDIRIMLI OYUN")
        assertEquals("xbox_store", xboxStore1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, xboxStore1.status)
        assertTrue(xboxStore1.confidence >= 75)

        val riot1 = recognizeUser("RIOT GAMES VALORANT PUANI")
        assertEquals("riot_games", riot1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, riot1.status)
        assertTrue(riot1.confidence >= 75)

        val bnet1 = recognizeUser("BATTLE NET DIABLO IV")
        assertEquals("battle_net", bnet1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bnet1.status)
        assertTrue(bnet1.confidence >= 75)

        val bnet2 = recognizeBank("BLIZZARD ENTERTAINMENT SAS")
        assertEquals("battle_net", bnet2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bnet2.status)

        val ubisoft1 = recognizeUser("UBISOFT STORE ASSASSINS CREED")
        assertEquals("ubisoft_store", ubisoft1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ubisoft1.status)
        assertTrue(ubisoft1.confidence >= 75)

        val ubisoft2 = recognizeUser("UBISOFT CONNECT SUBSCRIPTION")
        assertEquals("ubisoft_store", ubisoft2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, ubisoft2.status)

        val cinemarine1 = recognizeUser("CINEMARINE BODRUM OASIS")
        assertEquals("cinemarine", cinemarine1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, cinemarine1.status)
        assertTrue(cinemarine1.confidence >= 75)

        val vialand1 = recognizeUser("VIALAND TEMA PARK GIRIS")
        assertEquals("vialand", vialand1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, vialand1.status)
        assertTrue(vialand1.confidence >= 75)

        val vialand2 = recognizeUser("ISFANBUL TEMAPARK")
        assertEquals("vialand", vialand2.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, vialand2.status)

        // Sentetik negatifler / yanlış eşleşme koruması
        val steamNeg = recognizeUser("STEAM CLEANING OTO YIKAMA")
        assertNull(steamNeg.merchant, "STEAM CLEANING OTO YIKAMA steam ile eşleşmemelidir")

        val paribuNeg = recognizeBank("PARIBU TEKNOLOJI A.S.")
        assertNull(paribuNeg.merchant, "PARIBU TEKNOLOJI A.S. paribu_cineverse ile eşleşmemelidir")

        val passoligNeg = recognizeUser("PASSOLIG KART YILLIK AIDATI")
        assertNull(passoligNeg.merchant, "PASSOLIG kart/ödeme passo ile eşleşmemelidir")

        val psPlus = recognizeUser("PLAYSTATION PLUS UYELIK")
        assertEquals("playstation_plus", psPlus.merchant?.id, "PLAYSTATION PLUS playstation_plus ile eşleşmelidir")
        assertTrue(psPlus.merchant?.id != "playstation_store", "PLAYSTATION PLUS playstation_store olmamalıdır")

        val xboxGp = recognizeUser("XBOX GAME PASS ULTIMATE")
        assertEquals("xbox_game_pass", xboxGp.merchant?.id, "XBOX GAME PASS xbox_game_pass ile eşleşmelidir")
        assertTrue(xboxGp.merchant?.id != "xbox_store", "XBOX GAME PASS xbox_store olmamalıdır")

        val eaPlay = recognizeUser("EA PLAY UYELIK")
        assertEquals("ea_play", eaPlay.merchant?.id, "EA PLAY ea_play olarak kalmalıdır")

        val epicHotelNeg = recognizeUser("EPIC HOTEL REZERVASYON")
        assertNull(epicHotelNeg.merchant, "EPIC HOTEL epic_games ile eşleşmemelidir")

        val genericBiletNeg = recognizeUser("GENEL BILET ALIMI")
        assertNull(genericBiletNeg.merchant, "Genel BILET ifadesi hiçbir bilet merchant'ı ile eşleşmemelidir")

        val mobiletNeg = recognizeUser("MOBILE BANKACILIK UCRETI")
        assertNull(mobiletNeg.merchant, "Mobilet dışı 'mobile' metni mobilet olmamalıdır")

        val eKentNeg = recognizeBank("ANKARAKART E-KENT GECIS SISTEMLERI DOLUM")
        assertNull(eKentNeg.merchant, "ANKARAKART E-KENT GECIS SISTEMLERI DOLUM eğlence merchant'larına bağlanmamalıdır")

        val sehirTiyatrolariNeg = recognizeUser("ISTANBUL SEHIR TIYATROLARI BILET")
        assertNull(sehirTiyatrolariNeg.merchant, "ISTANBUL SEHIR TIYATROLARI BILET yeni kayıtlara bağlanmamalıdır")

        // Biletix / Biletinial / Biletino ayrımı
        val biletixDistinct = recognizeUser("BILETIX BILET")
        assertEquals("biletix", biletixDistinct.merchant?.id)
        val biletinialDistinct = recognizeUser("BILETINIAL BILET")
        assertEquals("biletinial", biletinialDistinct.merchant?.id)
        val biletinoDistinct = recognizeUser("BILETINO BILET")
        assertEquals("biletino", biletinoDistinct.merchant?.id)
    }

    @Test
    fun housingCatalogIntegrity_v19Contract() {
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)

        val housingMerchants = merchants.filter { it.categoryIconKey.key == "housing" }
        assertEquals(7, housingMerchants.size, "Toplam 7 konut/ev merchant'ı olmalıdır")
        assertEquals(
            7,
            housingMerchants.count { it.verifiedLogoKey != null },
            "Konut kategorisinde uzak logo sayısı 7 olmalıdır",
        )
        assertEquals(
            0,
            housingMerchants.count { it.verifiedLogoKey == null },
            "Konut kategorisinde fallback merchant sayısı 0 olmalıdır",
        )

        val housingKeys =
            listOf(
                "apsiyon" to "apsiyon",
                "yonetimcell" to "yonetimcell",
                "armut" to "armut",
                "senyonet" to "senyonet",
                "mrusta" to "mrusta",
                "pronet" to "pronet",
                "kale_alarm" to "kale_alarm",
            )

        housingKeys.forEach { (id, expectedLogoKey) ->
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Konut merchant'ı '$id' katalogda bulunmalıdır")
            assertEquals("housing", merchant.categoryIconKey.key, "Kategori 'housing' olmalıdır")
            assertEquals(expectedLogoKey, merchant.verifiedLogoKey, "verifiedLogoKey '$expectedLogoKey' olmalıdır")
        }

        // Reddedilen adayların katalogda olmadığını doğrula
        val rejectedCandidates = listOf("ustabilir", "biusta", "mutlubiev")
        rejectedCandidates.forEach { id ->
            assertNull(merchants.firstOrNull { it.id == id }, "Reddedilen aday '$id' katalogda bulunmamalıdır")
        }
    }

    @Test
    fun housingPositiveAliasRecognitionContract() {
        // 1. Apsiyon
        val aps1 = recognizeUser("Apsiyon")
        assertEquals("apsiyon", aps1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, aps1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, aps1.source)
        assertEquals(95, aps1.confidence)
        val aps1Info = TransactionMerchantAttributionResolver.resolve("Apsiyon")
        assertNotNull(aps1Info)
        assertEquals("apsiyon", aps1Info.merchantId.value)
        assertEquals(26, aps1Info.catalogVersion)

        val apsBilisim = recognizeUser("Apsiyon Bilişim")
        assertEquals("apsiyon", apsBilisim.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, apsBilisim.status)

        val apsAidat = recognizeUser("Apsiyon Aidat")
        assertEquals("apsiyon", apsAidat.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, apsAidat.status)

        val apsDomain = recognizeUser("apsiyon.com")
        assertEquals("apsiyon", apsDomain.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, apsDomain.status)

        // 2. Yönetimcell
        val yon1 = recognizeUser("Yönetimcell")
        assertEquals("yonetimcell", yon1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, yon1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, yon1.source)
        val yon1Info = TransactionMerchantAttributionResolver.resolve("Yönetimcell")
        assertNotNull(yon1Info)
        assertEquals("yonetimcell", yon1Info.merchantId.value)
        assertEquals(26, yon1Info.catalogVersion)

        val yonCell = recognizeUser("Yönetim Cell")
        assertEquals("yonetimcell", yonCell.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, yonCell.status)

        val yonDomain = recognizeUser("yonetimcell.com")
        assertEquals("yonetimcell", yonDomain.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, yonDomain.status)

        // 3. Armut
        val armDomain = recognizeUser("armut.com")
        assertEquals("armut", armDomain.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, armDomain.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, armDomain.source)
        val armDomainInfo = TransactionMerchantAttributionResolver.resolve("armut.com")
        assertNotNull(armDomainInfo)
        assertEquals("armut", armDomainInfo.merchantId.value)
        assertEquals(26, armDomainInfo.catalogVersion)

        val armTekn = recognizeUser("Armut Teknoloji")
        assertEquals("armut", armTekn.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, armTekn.status)

        val armHizmet = recognizeUser("Armut Hizmet")
        assertEquals("armut", armHizmet.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, armHizmet.status)

        val armTemizlik = recognizeUser("Armut Temizlik")
        assertEquals("armut", armTemizlik.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, armTemizlik.status)

        val armTadilat = recognizeUser("Armut Tadilat")
        assertEquals("armut", armTadilat.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, armTadilat.status)

        // 4. Senyonet
        val sen1 = recognizeUser("Senyonet")
        assertEquals("senyonet", sen1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, sen1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, sen1.source)
        val sen1Info = TransactionMerchantAttributionResolver.resolve("Senyonet")
        assertNotNull(sen1Info)
        assertEquals("senyonet", sen1Info.merchantId.value)
        assertEquals(26, sen1Info.catalogVersion)

        val senYonet = recognizeUser("Sen Yönet")
        assertEquals("senyonet", senYonet.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, senYonet.status)

        val senDomain = recognizeUser("senyonet.net")
        assertEquals("senyonet", senDomain.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, senDomain.status)

        // 5. Mr Usta
        val mru1 = recognizeUser("Mr Usta")
        assertEquals("mrusta", mru1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mru1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, mru1.source)
        val mru1Info = TransactionMerchantAttributionResolver.resolve("Mr Usta")
        assertNotNull(mru1Info)
        assertEquals("mrusta", mru1Info.merchantId.value)
        assertEquals(26, mru1Info.catalogVersion)

        val mruDot = recognizeUser("Mr. Usta")
        assertEquals("mrusta", mruDot.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mruDot.status)

        val mruJoined = recognizeUser("MrUsta")
        assertEquals("mrusta", mruJoined.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mruJoined.status)

        val mruDomain = recognizeUser("mrusta.com.tr")
        assertEquals("mrusta", mruDomain.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mruDomain.status)

        // 6. Pronet
        val pro1 = recognizeUser("Pronet")
        assertEquals("pronet", pro1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, pro1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, pro1.source)
        val pro1Info = TransactionMerchantAttributionResolver.resolve("Pronet")
        assertNotNull(pro1Info)
        assertEquals("pronet", pro1Info.merchantId.value)
        assertEquals(26, pro1Info.catalogVersion)

        val proGuvenlik = recognizeUser("Pronet Güvenlik")
        assertEquals("pronet", proGuvenlik.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, proGuvenlik.status)

        val proAlarm = recognizeUser("Pronet Alarm")
        assertEquals("pronet", proAlarm.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, proAlarm.status)

        val proDomain = recognizeUser("pronet.com.tr")
        assertEquals("pronet", proDomain.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, proDomain.status)

        // 7. Kale Alarm
        val kale1 = recognizeUser("Kale Alarm")
        assertEquals("kale_alarm", kale1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, kale1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, kale1.source)
        val kale1Info = TransactionMerchantAttributionResolver.resolve("Kale Alarm")
        assertNotNull(kale1Info)
        assertEquals("kale_alarm", kale1Info.merchantId.value)
        assertEquals(26, kale1Info.catalogVersion)

        val kaleGuvenlik = recognizeUser("Kale Güvenlik")
        assertEquals("kale_alarm", kaleGuvenlik.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, kaleGuvenlik.status)

        val kaleDomain = recognizeUser("kalealarm.com.tr")
        assertEquals("kale_alarm", kaleDomain.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, kaleDomain.status)
    }

    @Test
    fun housingNegativeAliasRecognitionContract() {
        val genericDescriptions =
            listOf(
                "Kira",
                "Ev kirası",
                "Apartman aidatı",
                "Site aidatı",
                "Yönetim gideri",
                "Ev temizliği",
                "Usta ödemesi",
                "Boya ustası",
                "Tesisat ustası",
                "Armut 1 kg",
                "Sarı armut",
                "Manav armut",
                "Kale kapı",
                "Kale kilit",
                "Ev alarmı",
                "Güvenlik gideri",
            )

        for (desc in genericDescriptions) {
            val userRes = recognizeUser(desc)
            assertNull(userRes.merchant, "Genel açıklama '$desc' bir merchant ile eşleşmemelidir")
            val bankRes = recognizeBank(desc)
            assertNull(bankRes.merchant, "Genel banka açıklaması '$desc' bir merchant ile eşleşmemelidir")
            val resolved = TransactionMerchantAttributionResolver.resolve(desc)
            assertNull(resolved, "Genel açıklama '$desc' TransactionMerchantAttributionResolver tarafından çözülmemelidir")
        }
    }

    @Test
    fun healthcareCatalogIntegrity_v20Contract() {
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)
        assertTrue(merchants.all { it.catalogVersion == 26 }, "Tüm merchant'lar v26 olmalıdır")

        val healthcareMerchants = merchants.filter { it.categoryIconKey.key == "healthcare" }
        assertEquals(18, healthcareMerchants.size, "Toplam 18 sağlık merchant'ı olmalıdır (Atasun shopping kategorisindedir)")
        assertEquals(
            18,
            healthcareMerchants.count { it.verifiedLogoKey != null },
            "Sağlık kategorisinde uzak logo sayısı 18 olmalıdır",
        )
        assertEquals(
            0,
            healthcareMerchants.count { it.verifiedLogoKey == null },
            "Sağlık kategorisinde fallback merchant sayısı 0 olmalıdır",
        )

        val healthcareKeys =
            listOf(
                "acibadem" to "acibadem",
                "memorial" to "memorial",
                "medical_park" to "medical_park",
                "liv_hospital" to "liv_hospital",
                "medicana" to "medicana",
                "florence_nightingale" to "florence_nightingale",
                "anadolu_saglik" to "anadolu_saglik",
                "amerikan_hastanesi" to "amerikan_hastanesi",
                "dunyagoz" to "dunyagoz",
                "medipol" to "medipol",
                "biruni_hastanesi" to "biruni_hastanesi",
                "guven_hastanesi" to "guven_hastanesi",
                "lokman_hekim" to "lokman_hekim",
                "baskent_hastanesi" to "baskent_hastanesi",
                "synevo" to "synevo",
                "duzen_laboratuvari" to "duzen_laboratuvari",
                "dentgroup" to "dentgroup",
                "hospitadent" to "hospitadent",
            )

        healthcareKeys.forEach { (id, expectedLogoKey) ->
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Sağlık merchant'ı '$id' katalogda bulunmalıdır")
            assertEquals("healthcare", merchant.categoryIconKey.key, "Kategori 'healthcare' olmalıdır")
            assertEquals(expectedLogoKey, merchant.verifiedLogoKey, "verifiedLogoKey '$expectedLogoKey' olmalıdır")
        }

        // Atasun Optik shopping kategorisinde ancak doğrulanmış logo anahtarı ile katalogda bulunmalıdır
        val atasun = merchants.firstOrNull { it.id == "atasun_optik" }
        assertNotNull(atasun, "Atasun Optik katalogda bulunmalıdır")
        assertEquals("shopping", atasun.categoryIconKey.key, "Atasun Optik categoryIconKey 'shopping' olmalıdır")
        assertEquals("atasun_optik", atasun.verifiedLogoKey, "Atasun Optik verifiedLogoKey 'atasun_optik' olmalıdır")

        // Bloke edilen adayların katalogda olmadığını doğrula
        val excludedCandidates =
            listOf(
                "bayindir",
                "amplifon",
                "maxtone",
            )
        excludedCandidates.forEach { id ->
            assertNull(merchants.firstOrNull { it.id == id }, "Dışlanan/bloke aday '$id' katalogda bulunmamalıdır")
        }
    }

    @Test
    fun healthcarePositiveAliasRecognitionContract() {
        // 1. Acıbadem
        val aci1 = recognizeUser("Acıbadem")
        assertEquals("acibadem", aci1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, aci1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, aci1.source)
        assertEquals(95, aci1.confidence)
        val aci1Info = TransactionMerchantAttributionResolver.resolve("Acıbadem")
        assertNotNull(aci1Info)
        assertEquals("acibadem", aci1Info.merchantId.value)
        assertEquals(26, aci1Info.catalogVersion)

        val aciHastane = recognizeUser("Acıbadem Hastanesi")
        assertEquals("acibadem", aciHastane.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, aciHastane.status)

        val aciSaglik = recognizeUser("Acıbadem Sağlık")
        assertEquals("acibadem", aciSaglik.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, aciSaglik.status)

        // 2. Memorial
        val mem1 = recognizeUser("Memorial")
        assertEquals("memorial", mem1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, mem1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, mem1.source)
        val mem1Info = TransactionMerchantAttributionResolver.resolve("Memorial")
        assertNotNull(mem1Info)
        assertEquals("memorial", mem1Info.merchantId.value)
        assertEquals(26, mem1Info.catalogVersion)

        val memHastane = recognizeUser("Memorial Hastanesi")
        assertEquals("memorial", memHastane.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, memHastane.status)

        val memSaglik = recognizeUser("Memorial Sağlık")
        assertEquals("memorial", memSaglik.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, memSaglik.status)

        // 3. Medical Park
        val med1 = recognizeUser("Medical Park")
        assertEquals("medical_park", med1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, med1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, med1.source)
        val med1Info = TransactionMerchantAttributionResolver.resolve("Medical Park")
        assertNotNull(med1Info)
        assertEquals("medical_park", med1Info.merchantId.value)
        assertEquals(26, med1Info.catalogVersion)

        val medJoined = recognizeUser("MEDICALPARK")
        assertEquals("medical_park", medJoined.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, medJoined.status)

        val medVm = recognizeUser("VM Medical Park")
        assertEquals("medical_park", medVm.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, medVm.status)

        // 4. Liv Hospital
        val liv1 = recognizeUser("Liv Hospital")
        assertEquals("liv_hospital", liv1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, liv1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, liv1.source)
        val liv1Info = TransactionMerchantAttributionResolver.resolve("Liv Hospital")
        assertNotNull(liv1Info)
        assertEquals("liv_hospital", liv1Info.merchantId.value)
        assertEquals(26, liv1Info.catalogVersion)

        val livUlus = recognizeUser("LIV ULUS")
        assertEquals("liv_hospital", livUlus.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, livUlus.status)

        val livVadi = recognizeUser("LIV VADI")
        assertEquals("liv_hospital", livVadi.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, livVadi.status)

        val livAnkara = recognizeUser("LIV ANKARA")
        assertEquals("liv_hospital", livAnkara.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, livAnkara.status)

        val livMlpLegal = recognizeUser("MLP SAGLIK HIZMETLERI LIV HOSPITAL")
        assertEquals("liv_hospital", livMlpLegal.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, livMlpLegal.status)

        // 5. Medicana
        val medc1 = recognizeUser("Medicana")
        assertEquals("medicana", medc1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, medc1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, medc1.source)
        val medc1Info = TransactionMerchantAttributionResolver.resolve("Medicana")
        assertNotNull(medc1Info)
        assertEquals("medicana", medc1Info.merchantId.value)
        assertEquals(26, medc1Info.catalogVersion)

        val medcHastane = recognizeUser("Medicana Hastanesi")
        assertEquals("medicana", medcHastane.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, medcHastane.status)

        val medcSaglik = recognizeUser("Medicana Sağlık")
        assertEquals("medicana", medcSaglik.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, medcSaglik.status)

        // 6. Florence Nightingale
        val fn1 = recognizeUser("Florence Nightingale")
        assertEquals("florence_nightingale", fn1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, fn1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, fn1.source)
        val fn1Info = TransactionMerchantAttributionResolver.resolve("Florence Nightingale")
        assertNotNull(fn1Info)
        assertEquals("florence_nightingale", fn1Info.merchantId.value)
        assertEquals(26, fn1Info.catalogVersion)

        val fnHastane = recognizeUser("FLORENCE HASTANESI")
        assertEquals("florence_nightingale", fnHastane.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, fnHastane.status)

        val fnIstanbul = recognizeUser("ISTANBUL FLORENCE NIGHTINGALE")
        assertEquals("florence_nightingale", fnIstanbul.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, fnIstanbul.status)

        // 7. Anadolu Sağlık Merkezi
        val as1 = recognizeUser("Anadolu Sağlık Merkezi")
        assertEquals("anadolu_saglik", as1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, as1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, as1.source)
        val as1Info = TransactionMerchantAttributionResolver.resolve("Anadolu Sağlık Merkezi")
        assertNotNull(as1Info)
        assertEquals("anadolu_saglik", as1Info.merchantId.value)
        assertEquals(26, as1Info.catalogVersion)

        val asSaglik = recognizeUser("Anadolu Sağlık")
        assertEquals("anadolu_saglik", asSaglik.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, asSaglik.status)

        // 8. Amerikan Hastanesi
        val am1 = recognizeUser("Amerikan Hastanesi")
        assertEquals("amerikan_hastanesi", am1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, am1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, am1.source)
        val am1Info = TransactionMerchantAttributionResolver.resolve("Amerikan Hastanesi")
        assertNotNull(am1Info)
        assertEquals("amerikan_hastanesi", am1Info.merchantId.value)
        assertEquals(26, am1Info.catalogVersion)

        val amVkv = recognizeUser("VKV AMERIKAN HASTANESI")
        assertEquals("amerikan_hastanesi", amVkv.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, amVkv.status)

        val amKoc = recognizeUser("KOC AMERIKAN HASTANESI")
        assertEquals("amerikan_hastanesi", amKoc.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, amKoc.status)

        // 9. Dünyagöz
        val dg1 = recognizeUser("Dünyagöz")
        assertEquals("dunyagoz", dg1.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, dg1.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, dg1.source)
        val dg1Info = TransactionMerchantAttributionResolver.resolve("Dünyagöz")
        assertNotNull(dg1Info)
        assertEquals("dunyagoz", dg1Info.merchantId.value)
        assertEquals(26, dg1Info.catalogVersion)

        val dgSpaced = recognizeUser("DUNYA GOZ")
        assertEquals("dunyagoz", dgSpaced.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, dgSpaced.status)

        val dgHastane = recognizeUser("DUNYAGOZ HASTANESI")
        assertEquals("dunyagoz", dgHastane.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, dgHastane.status)
    }

    @Test
    fun healthcareNegativeAliasRecognitionContract() {
        val genericDescriptions =
            listOf(
                "Hastane",
                "Sağlık",
                "Klinik",
                "Acil",
                "Laboratuvar",
                "Diş",
                "Göz",
                "Optik",
                "Eczane",
                "Doktor",
                "ACIBADEM UNIVERSITESI",
                "ACIBADEM MAHALLESI",
                "ACIBADEM CAD",
                "MEMORIAL PARK",
                "MEMORIAL CUP",
                "PARK MEDIKAL",
                "MEDICAL CIHAZLAR",
                "MERKEZ PARK OTOPARK",
                "LIV TV",
                "LIV YAYINCILIK",
                "MEDIKAL",
                "MEDITERRANEAN",
                "FLORENCE TOUR",
                "FLORENCE HOTEL",
                "FLORENCE TRAVEL",
                "ANADOLU UNIVERSITESI",
                "ANADOLU SIGORTA",
                "ANADOLU EFES",
                "ANADOLU ISUZU",
                "ANADOLU HASTANESI",
                "AMERIKAN KULTUR",
                "AMERIKAN DIL KURSU",
                "AMERIKAN PAZARI",
                "AMERIKAN KAPI",
                "DUNYA GAZETESI",
                "DUNYA DONER",
                "GOZ DOKTORU",
            )

        for (desc in genericDescriptions) {
            val userRes = recognizeUser(desc)
            val bankRes = recognizeBank(desc)
            if (desc == "AMERIKAN KULTUR") {
                // Amerikan Kültür J2 eğitim merchant'ıdır; Amerikan Hastanesi ile eşleşmediği ve doğru ID'ye gittiği doğrulanır
                assertEquals("amerikankultur", userRes.merchant?.id)
                assertTrue(userRes.merchant?.id != "amerikan_hastanesi")
                assertEquals("amerikankultur", bankRes.merchant?.id)
                assertTrue(bankRes.merchant?.id != "amerikan_hastanesi")
                val resolved = TransactionMerchantAttributionResolver.resolve(desc)
                assertEquals("amerikankultur", resolved?.merchantId?.value)
                continue
            }
            assertNull(userRes.merchant, "Genel veya negatif açıklama '$desc' bir merchant ile eşleşmemelidir")
            assertNull(bankRes.merchant, "Genel veya negatif banka açıklaması '$desc' bir merchant ile eşleşmemelidir")
            val resolved = TransactionMerchantAttributionResolver.resolve(desc)
            assertNull(resolved, "Genel veya negatif açıklama '$desc' TransactionMerchantAttributionResolver tarafından çözülmemelidir")
        }
    }

    @Test
    fun healthcareMlpDistinctionContract() {
        // Çıplak MLP ifadeleri fail-closed NO_MATCH olmalıdır
        val bareMlpCases =
            listOf(
                "MLP SAGLIK",
                "MLP SAGLIK HIZMETLERI",
                "MLP SAGLIK HIZMETLERI ANONIM SIRKETI",
                "POS MLP SAGLIK",
                "MLP SAGLIK HIZMETLERI ODEME",
            )

        for (desc in bareMlpCases) {
            val userRes = recognizeUser(desc)
            assertNull(userRes.merchant, "Çıplak MLP ifadesi '$desc' hiçbir merchant ile eşleşmemelidir")
            val bankRes = recognizeBank(desc)
            assertNull(bankRes.merchant, "Çıplak MLP banka ifadesi '$desc' hiçbir merchant ile eşleşmemelidir")
            val resolved = TransactionMerchantAttributionResolver.resolve(desc)
            assertNull(resolved, "Çıplak MLP ifadesi '$desc' resolve edilmemelidir")
        }

        // Açık niteleyici taşıyanlar doğru merchant ile eşleşmelidir
        val livMlp = recognizeBank("MLP SAGLIK HIZMETLERI LIV HOSPITAL")
        assertEquals("liv_hospital", livMlp.merchant?.id)

        val vmBank = recognizeBank("POS VM MEDICAL PARK PENDIK")
        assertEquals("medical_park", vmBank.merchant?.id)
    }

    @Test
    fun healthcarePositiveNegativeConsistencyContract() {
        // StarterMerchantCatalog içindeki tüm merchant'lar için pozitif-negatif tutarlılık kontrolü
        for (m in StarterMerchantCatalog.merchants) {
            val normalizedNegatives = m.negativeAliases.map { TurkishDescriptionNormalizer.normalize(it.value) }
            for (alias in m.aliases) {
                val normalizedPositive = TurkishDescriptionNormalizer.normalize(alias.value)
                for (neg in normalizedNegatives) {
                    assertFalse(
                        normalizedPositive.contains(neg),
                        "Tutarsızlık: Merchant '${m.id}' için pozitif alias '$normalizedPositive', negatif alias '$neg' ifadesini içeremez!",
                    )
                }
            }
        }
    }

    @Test
    fun healthcareExtendedPositiveExampleRegressionContract() {
        val testCases =
            listOf(
                "ACIBADEM HASTANESI KADIKOY" to "acibadem",
                "ACIBADEM SAGLIK MASLAK" to "acibadem",
                "MEMORIAL SISLI HASTANESI" to "memorial",
                "MEMORIAL SAGLIK GRUBU" to "memorial",
                "MEDICAL PARK GOZTEPE" to "medical_park",
                "VM MEDICAL PARK PENDIK" to "medical_park",
                "LIV HOSPITAL ULUS" to "liv_hospital",
                "MLP SAGLIK HIZMETLERI LIV HOSPITAL" to "liv_hospital",
                "MEDICANA CAMLICA" to "medicana",
                "MEDICANA SAGLIK GRUBU" to "medicana",
                "FLORENCE NIGHTINGALE SISLI" to "florence_nightingale",
                "ISTANBUL FLORENCE NIGHTINGALE" to "florence_nightingale",
                "ANADOLU SAGLIK MERKEZI GEBZE" to "anadolu_saglik",
                "ANADOLU SAGLIK ATASEHIR" to "anadolu_saglik",
                "AMERIKAN HASTANESI NISANTASI" to "amerikan_hastanesi",
                "VKV AMERIKAN HASTANESI" to "amerikan_hastanesi",
                "DUNYAGOZ ATAKOY" to "dunyagoz",
                "DUNYA GOZ HASTANESI ETILER" to "dunyagoz",
            )

        for ((description, expectedMerchantId) in testCases) {
            val userRes = recognizeUser(description)
            assertNotNull(userRes.merchant, "User açıklaması '$description' için merchant null olmamalıdır")
            assertEquals(
                expectedMerchantId,
                userRes.merchant?.id,
                "User açıklaması '$description' -> '$expectedMerchantId' olarak tanınmalıdır",
            )
            assertEquals(
                MerchantMatchStatus.MATCHED,
                userRes.status,
                "User açıklaması '$description' MATCHED olmalıdır",
            )

            val bankRes = recognizeBank(description)
            assertNotNull(bankRes.merchant, "Banka açıklaması '$description' için merchant null olmamalıdır")
            assertEquals(
                expectedMerchantId,
                bankRes.merchant?.id,
                "Banka açıklaması '$description' -> '$expectedMerchantId' olarak tanınmalıdır",
            )
            assertEquals(
                MerchantMatchStatus.MATCHED,
                bankRes.status,
                "Banka açıklaması '$description' MATCHED olmalıdır",
            )
        }
    }

    @Test
    fun healthcareRemainingTenSearchable_positiveRecognition() {
        val testCases =
            listOf(
                "MEDIPOL HASTANESI BAGCILAR" to "medipol",
                "MEDIPOL MEGA HASTANESI" to "medipol",
                "BIRUNI HASTANESI" to "biruni_hastanesi",
                "BIRUNI UNIVERSITESI TIP FAKULTESI HASTANESI" to "biruni_hastanesi",
                "ANKARA GUVEN HASTANESI" to "guven_hastanesi",
                "GUVEN SAGLIK ANKARA" to "guven_hastanesi",
                "LOKMAN HEKIM HASTANESI" to "lokman_hekim",
                "BASKENT UNIVERSITESI HASTANESI" to "baskent_hastanesi",
                "SYNEVO LABORATUVAR" to "synevo",
                "DUZEN LABORATUVARI" to "duzen_laboratuvari",
                "DENT GROUP DIS KLINIGI" to "dentgroup",
                "HOSPITADENT DIS" to "hospitadent",
                "ATASUN OPTIK" to "atasun_optik",
                "ATASUN GOZLUK" to "atasun_optik",
            )

        for ((description, expectedMerchantId) in testCases) {
            val userRes = recognizeUser(description)
            assertNotNull(userRes.merchant, "User açıklaması '$description' için merchant null olmamalıdır")
            assertEquals(
                expectedMerchantId,
                userRes.merchant?.id,
                "User açıklaması '$description' -> '$expectedMerchantId' olarak tanınmalıdır",
            )
            assertEquals(
                MerchantMatchStatus.MATCHED,
                userRes.status,
                "User açıklaması '$description' MATCHED olmalıdır",
            )

            val bankRes = recognizeBank(description)
            assertNotNull(bankRes.merchant, "Banka açıklaması '$description' için merchant null olmamalıdır")
            assertEquals(
                expectedMerchantId,
                bankRes.merchant?.id,
                "Banka açıklaması '$description' -> '$expectedMerchantId' olarak tanınmalıdır",
            )
            assertEquals(
                MerchantMatchStatus.MATCHED,
                bankRes.status,
                "Banka açıklaması '$description' MATCHED olmalıdır",
            )
        }
    }

    @Test
    fun healthcareRemainingTenSearchable_negativeAndFailClosed() {
        val negativeAndFailClosedCases =
            listOf(
                "MEDIPOL",
                "MEDIPOL UNIVERSITESI HARC",
                "MEDIPOL OGRENCI ISLERI",
                "BIRUNI",
                "BIRUNI UNIVERSITESI HARC",
                "GUVEN",
                "GUVEN TAKSI",
                "GUVEN SIGORTA",
                "LOKMAN",
                "LOKMAN HEKIM BAHARAT",
                "BASKENT",
                "BASKENT GAZ",
                "BASKENT EDAS",
                "BASKENT UNIVERSITESI HARC",
                "LAB",
                "LABORATUVAR",
                "TAHLIL",
                "DUZEN",
                "EV DUZENI",
                "DENT",
                "DIS",
                "DENTAL",
                "HOSPITA",
                "OPTIK",
                "GOZLUK",
                "LENS",
            )

        for (desc in negativeAndFailClosedCases) {
            val userRes = recognizeUser(desc)
            assertNull(userRes.merchant, "Negatif veya jenerik açıklama '$desc' hiçbir merchant ile eşleşmemelidir")
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                userRes.status,
                "Negatif veya jenerik açıklama '$desc' NO_MATCH statüsünde olmalıdır",
            )

            val bankRes = recognizeBank(desc)
            assertNull(bankRes.merchant, "Negatif veya jenerik banka açıklaması '$desc' hiçbir merchant ile eşleşmemelidir")
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                bankRes.status,
                "Negatif veya jenerik banka açıklaması '$desc' NO_MATCH statüsünde olmalıdır",
            )

            val resolved = TransactionMerchantAttributionResolver.resolve(desc)
            assertNull(resolved, "Negatif veya jenerik açıklama '$desc' TransactionMerchantAttributionResolver tarafından çözülmemelidir")
        }
    }

    @Test
    fun educationCatalogIntegrity_v22Contract() {
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)
        assertTrue(merchants.all { it.catalogVersion == 26 }, "Tüm merchant'lar v26 olmalıdır")

        val educationMerchants = merchants.filter { it.categoryIconKey.key == "education" }
        assertEquals(20, educationMerchants.size, "Toplam 20 eğitim merchant'ı olmalıdır")
        assertEquals(
            20,
            educationMerchants.count { it.verifiedLogoKey != null },
            "Eğitim kategorisinde uzak logo sayısı 20 olmalıdır",
        )
        assertEquals(
            0,
            educationMerchants.count { it.verifiedLogoKey == null },
            "Eğitim kategorisinde fallback merchant sayısı 0 olmalıdır",
        )

        val educationKeys =
            listOf(
                "udemy" to "udemy",
                "coursera" to "coursera",
                "duolingo" to "duolingo",
                "cambly" to "cambly",
                "kunduz" to "kunduz",
                "tonguc_akademi" to "tonguc_akademi",
                "okulistik" to "okulistik",
                "morpa_kampus" to "morpa_kampus",
                "raunt" to "raunt",
                "busuu" to "busuu",
                "preply" to "preply",
                "italki" to "italki",
                "skillshare" to "skillshare",
                "derslig" to "derslig",
                "benim_hocam" to "benim_hocam",
                "pegem_akademi" to "pegem_akademi",
                "english_time" to "english_time",
                "bilge_adam" to "bilge_adam",
                "wall_street_english" to "wall_street_english",
                "amerikankultur" to "amerikankultur",
            )

        educationKeys.forEach { (id, expectedLogoKey) ->
            val merchant = merchants.firstOrNull { it.id == id }
            assertNotNull(merchant, "Eğitim merchant'ı '$id' katalogda bulunmalıdır")
            assertEquals("education", merchant.categoryIconKey.key, "Kategori 'education' olmalıdır")
            assertEquals(expectedLogoKey, merchant.verifiedLogoKey, "verifiedLogoKey '$expectedLogoKey' olmalıdır")
            assertNotNull(
                MerchantLogoDomainAllowlist.resolveDomain(merchant.verifiedLogoKey),
                "verifiedLogoKey allowlist'te çözülmelidir",
            )
        }

        // Dokuz yeni eğitim merchant'ı için duplicate normalized alias bulunmadığı kontrolü
        educationMerchants.forEach { m ->
            val normalizedAliases = m.aliases.map { TurkishDescriptionNormalizer.normalize(it.value) }
            assertEquals(
                normalizedAliases.toSet().size,
                normalizedAliases.size,
                "Merchant '${m.id}' normalize edilmiş duplicate alias içeremez!",
            )
        }
    }

    @Test
    fun educationSuggestedNine_positiveRecognition() {
        val testCases =
            listOf(
                "UDEMY" to "udemy",
                "UDEMY COM" to "udemy",
                "UDEMY IRELAND" to "udemy",
                "COURSERA" to "coursera",
                "COURSERA ORG" to "coursera",
                "COURSERA PLUS" to "coursera",
                "DUOLINGO" to "duolingo",
                "DUOLINGO SUPER" to "duolingo",
                "DUOLINGO PLUS" to "duolingo",
                "CAMBLY" to "cambly",
                "CAMBLY KIDS" to "cambly",
                "CAMBLY COM" to "cambly",
                "KUNDUZ EGITIM" to "kunduz",
                "KUNDUZ APP" to "kunduz",
                "KUNDUZ COM" to "kunduz",
                "TONGUC AKADEMI" to "tonguc_akademi",
                "TONGUCAKADEMI.COM" to "tonguc_akademi",
                "TONGUCAKADEMI COM" to "tonguc_akademi",
                "OKULISTIK" to "okulistik",
                "OKULISTIK COM" to "okulistik",
                "MORPA KAMPUS" to "morpa_kampus",
                "MORPAKAMPUS COM" to "morpa_kampus",
                "RAUNT EGITIM" to "raunt",
                "RAUNT COM" to "raunt",
            )

        for ((description, expectedMerchantId) in testCases) {
            val userRes = recognizeUser(description)
            assertNotNull(userRes.merchant, "User açıklaması '$description' için merchant null olmamalıdır")
            assertEquals(
                expectedMerchantId,
                userRes.merchant?.id,
                "User açıklaması '$description' -> '$expectedMerchantId' olarak tanınmalıdır",
            )
            assertEquals(
                MerchantMatchStatus.MATCHED,
                userRes.status,
                "User açıklaması '$description' MATCHED olmalıdır",
            )

            val bankRes = recognizeBank(description)
            assertNotNull(bankRes.merchant, "Banka açıklaması '$description' için merchant null olmamalıdır")
            assertEquals(
                expectedMerchantId,
                bankRes.merchant?.id,
                "Banka açıklaması '$description' -> '$expectedMerchantId' olarak tanınmalıdır",
            )
            assertEquals(
                MerchantMatchStatus.MATCHED,
                bankRes.status,
                "Banka açıklaması '$description' MATCHED olmalıdır",
            )

            val resolved = TransactionMerchantAttributionResolver.resolve(description)
            assertNotNull(resolved, "Açıklama '$description' resolver tarafından çözülmelidir")
            assertEquals(expectedMerchantId, resolved.merchantId.value)
            assertEquals(26, resolved.catalogVersion)
        }
    }

    @Test
    fun educationSuggestedNine_negativeAndFailClosed() {
        val negativeAndFailClosedCases =
            listOf(
                "KUNDUZ",
                "TONGUC",
                "MORPA",
                "RAUNT",
                "EGITIM",
                "KURS",
                "AKADEMI",
                "OKUL HARCI",
                "UNIVERSITE HARCI",
                "KUNDUZ DERISI",
                "BOKS ILK RAUNT",
            )

        for (desc in negativeAndFailClosedCases) {
            val userRes = recognizeUser(desc)
            assertNull(userRes.merchant, "Negatif veya jenerik açıklama '$desc' hiçbir merchant ile eşleşmemelidir")
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                userRes.status,
                "Negatif veya jenerik açıklama '$desc' NO_MATCH statüsünde olmalıdır",
            )

            val bankRes = recognizeBank(desc)
            assertNull(bankRes.merchant, "Negatif veya jenerik banka açıklaması '$desc' hiçbir merchant ile eşleşmemelidir")
            assertEquals(
                MerchantMatchStatus.NO_MATCH,
                bankRes.status,
                "Negatif veya jenerik banka açıklaması '$desc' NO_MATCH statüsünde olmalıdır",
            )

            val resolved = TransactionMerchantAttributionResolver.resolve(desc)
            assertNull(resolved, "Negatif veya jenerik açıklama '$desc' TransactionMerchantAttributionResolver tarafından çözülmemelidir")
        }
    }

    @Test
    fun tongucAkademi_canonicalDomainAndFailClosedContract() {
        // A. TONGUCAKADEMI.COM
        val userRes = recognizeUser("TONGUCAKADEMI.COM")
        assertNotNull(userRes.merchant)
        assertEquals("tonguc_akademi", userRes.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, userRes.status)

        val bankRes = recognizeBank("TONGUCAKADEMI.COM")
        assertNotNull(bankRes.merchant)
        assertEquals("tonguc_akademi", bankRes.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bankRes.status)

        val resolved = TransactionMerchantAttributionResolver.resolve("TONGUCAKADEMI.COM")
        assertNotNull(resolved)
        assertEquals("tonguc_akademi", resolved.merchantId.value)
        assertEquals(26, resolved.catalogVersion)

        // B. TONGUC
        val userTonguc = recognizeUser("TONGUC")
        assertNull(userTonguc.merchant)
        assertEquals(MerchantMatchStatus.NO_MATCH, userTonguc.status)

        val bankTonguc = recognizeBank("TONGUC")
        assertNull(bankTonguc.merchant)
        assertEquals(MerchantMatchStatus.NO_MATCH, bankTonguc.status)

        val resolvedTonguc = TransactionMerchantAttributionResolver.resolve("TONGUC")
        assertNull(resolvedTonguc)
    }

    @Test
    fun fazH4G2J2_kalan18EgitimMerchant_tanimaVeGuvenlikSozlesmesi() {
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        assertEquals(223, merchants.count { it.verifiedLogoKey != null })
        assertEquals(37, merchants.count { it.verifiedLogoKey == null })

        // 1. 15 Yeni Merchant ve Doğruluk Kontrolü
        val j2Ids =
            listOf(
                "busuu",
                "preply",
                "italki",
                "skillshare",
                "derslig",
                "benim_hocam",
                "pegem_akademi",
                "english_time",
                "bilge_adam",
                "wall_street_english",
                "amerikankultur",
                "dr",
                "kitapyurdu",
                "bkm_kitap",
                "idefix",
            )
        for (id in j2Ids) {
            val m = merchants.firstOrNull { it.id == id }
            assertNotNull(m, "J2 merchant '$id' katalogda bulunmalıdır")
            assertEquals(id, m.verifiedLogoKey, "verifiedLogoKey '$id' olmalıdır")
            assertEquals(26, m.catalogVersion)
            val normalizedAliases = m.aliases.map { TurkishDescriptionNormalizer.normalize(it.value) }
            assertEquals(
                normalizedAliases.size,
                normalizedAliases.distinct().size,
                "Merchant '$id' için mükerrer normalized alias bulunmamalıdır",
            )
        }

        // Kategori sahiplikleri
        val educationIds =
            listOf(
                "busuu",
                "preply",
                "italki",
                "skillshare",
                "derslig",
                "benim_hocam",
                "pegem_akademi",
                "english_time",
                "bilge_adam",
                "wall_street_english",
                "amerikankultur",
            )
        for (id in educationIds) {
            assertEquals("education", merchants.first { it.id == id }.categoryIconKey.key)
        }
        val shoppingBookstoreIds = listOf("dr", "kitapyurdu", "bkm_kitap", "idefix")
        for (id in shoppingBookstoreIds) {
            assertEquals("shopping", merchants.first { it.id == id }.categoryIconKey.key)
        }
        assertEquals("Bilge Adam", merchants.first { it.id == "bilge_adam" }.displayName)
        assertEquals("Idefix", merchants.first { it.id == "idefix" }.displayName)

        // 2. D&R Özel Sözleşmesi
        val drMerchant = merchants.first { it.id == "dr" }
        val drRawAliases = drMerchant.aliases.map { it.value }
        assertTrue(drRawAliases.contains("D&R"), "Ham alias listesinde 'D&R' olmalıdır")
        assertFalse(drRawAliases.contains("D R"), "Ham alias listesinde 'D R' olmamalıdır")
        assertFalse(drRawAliases.contains("DR"), "Ham alias listesinde tek başına 'DR' olmamalıdır")

        // USER "D&R" -> MATCHED / 95 / dr
        val drUser = recognizeUser("D&R")
        assertEquals("dr", drUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, drUser.status)
        assertEquals(95, drUser.confidence)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, drUser.source)
        val drInfo = TransactionMerchantAttributionResolver.resolve("D&R")
        assertNotNull(drInfo)
        assertEquals("dr", drInfo.merchantId.value)
        assertEquals(95, drInfo.confidence)

        // BANK "D&R" -> LOW_CONFIDENCE / 65 / resolver null (canShowMerchantLogo false, confidence < 70)
        val drBank = recognizeBank("D&R")
        assertEquals("dr", drBank.merchant?.id)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, drBank.status)
        assertEquals(65, drBank.confidence)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, drBank.source)
        assertFalse(drBank.canShowMerchantLogo)

        // Nitelikli D&R alias'ları
        val drMagazaUser = recognizeUser("DR MAGAZACILIK")
        assertEquals("dr", drMagazaUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, drMagazaUser.status)
        val drMagazaBank = recognizeBank("DR MAGAZACILIK")
        assertEquals("dr", drMagazaBank.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, drMagazaBank.status)

        val drComUser = recognizeUser("DR COM TR")
        assertEquals("dr", drComUser.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, drComUser.status)
        val drComBank = recognizeBank("DR COM TR")
        assertEquals("dr", drComBank.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, drComBank.status)

        // Doktor unvanları fail-closed
        val drFailClosedCases = listOf("DR AHMET KAYA", "UZM DR MEHMET", "OP DR AYSE", "DR")
        for (desc in drFailClosedCases) {
            val resUser = recognizeUser(desc)
            assertNull(resUser.merchant, "Doktor veya tekil unvan '$desc' eşleşmemelidir")
            val resBank = recognizeBank(desc)
            assertNull(resBank.merchant, "Doktor veya tekil unvan '$desc' bankada eşleşmemelidir")
            assertNull(TransactionMerchantAttributionResolver.resolve(desc))
        }

        // 3. Bilge Adam Fail-Closed ve Nitelikli Sözleşmesi
        val bilgeMerchant = merchants.first { it.id == "bilge_adam" }
        assertFalse(bilgeMerchant.aliases.any { it.value == "BILGE ADAM" }, "Çıplak 'BILGE ADAM' pozitif alias olmamalıdır")

        // Çıplak ve doğal dil fail-closed
        val bilgeFailClosed = listOf("BILGE ADAM", "BIR BILGE ADAM", "BILGE ADAM SOZU", "BILGE ADAM KITABI")
        for (desc in bilgeFailClosed) {
            val userRes = recognizeUser(desc)
            assertNull(userRes.merchant, "Doğal dil ifadesi '$desc' bilge_adam ile eşleşmemelidir")
            assertEquals(MerchantMatchStatus.NO_MATCH, userRes.status)
            val bankRes = recognizeBank(desc)
            assertNull(bankRes.merchant, "Doğal dil ifadesi '$desc' bankada eşleşmemelidir")
            assertEquals(MerchantMatchStatus.NO_MATCH, bankRes.status)
            assertNull(TransactionMerchantAttributionResolver.resolve(desc))
        }

        // Nitelikli Bilge Adam alias'ları
        val bilgeNitelikli = listOf("BILGE ADAM AKADEMI", "BILGE ADAM EGITIM", "BILGE ADAM BILGISAYAR", "BILGEADAM COM", "BILGEADAM")
        for (desc in bilgeNitelikli) {
            val res = recognizeUser(desc)
            assertEquals("bilge_adam", res.merchant?.id, "'$desc' bilge_adam ile eşleşmelidir")
            assertEquals(MerchantMatchStatus.MATCHED, res.status)
        }

        // 4. BKM Kitap Fail-Closed Sözleşmesi
        val bkmMerchant = merchants.first { it.id == "bkm_kitap" }
        assertFalse(bkmMerchant.aliases.any { it.value == "BKM" }, "Çıplak 'BKM' alias olmamalıdır")

        val bkmFailClosed = listOf("BKM", "BKM EXPRESS", "BESIKTAS KULTUR MERKEZI")
        for (desc in bkmFailClosed) {
            val res = recognizeUser(desc)
            assertNull(res.merchant, "'$desc' bkm_kitap ile eşleşmemelidir")
            assertEquals(MerchantMatchStatus.NO_MATCH, res.status)
            assertNull(TransactionMerchantAttributionResolver.resolve(desc))
        }
        val bkmPozitif = listOf("BKM KITAP", "BKMKITAP COM")
        for (desc in bkmPozitif) {
            val res = recognizeUser(desc)
            assertEquals("bkm_kitap", res.merchant?.id, "'$desc' bkm_kitap ile eşleşmelidir")
            assertEquals(MerchantMatchStatus.MATCHED, res.status)
        }

        // 5. Amerikan Kültür Fail-Closed Sözleşmesi
        val amkMerchant = merchants.first { it.id == "amerikankultur" }
        assertFalse(amkMerchant.aliases.any { it.value == "AMERIKAN" }, "Çıplak 'AMERIKAN' alias olmamalıdır")

        val amkFail = recognizeUser("AMERIKAN")
        assertNull(amkFail.merchant)
        assertEquals(MerchantMatchStatus.NO_MATCH, amkFail.status)

        val amHastane = recognizeUser("AMERIKAN HASTANESI")
        assertEquals("amerikan_hastanesi", amHastane.merchant?.id)
        assertTrue(amHastane.merchant?.id != "amerikankultur")

        val amkPozitif = listOf("AMERIKAN KULTUR", "AMERIKAN KULTUR DERNEGI", "AMERIKAN KULTUR DIL OKULLARI", "AMERIKANKULTUR ORG TR")
        for (desc in amkPozitif) {
            val res = recognizeUser(desc)
            assertEquals("amerikankultur", res.merchant?.id)
            assertEquals(MerchantMatchStatus.MATCHED, res.status)
        }

        // 6. Wall Street English Fail-Closed Sözleşmesi
        val wseMerchant = merchants.first { it.id == "wall_street_english" }
        assertFalse(wseMerchant.aliases.any { it.value == "WSE" || it.value == "WALL STREET" })

        val wseFail = listOf("WSE", "WALL STREET", "WALL STREET JOURNAL", "WALL STREET BORSA")
        for (desc in wseFail) {
            val res = recognizeUser(desc)
            assertNull(res.merchant, "'$desc' wall_street_english ile eşleşmemelidir")
            assertEquals(MerchantMatchStatus.NO_MATCH, res.status)
        }
        val wsePozitif = listOf("WALL STREET ENGLISH", "WSE COM TR")
        for (desc in wsePozitif) {
            val res = recognizeUser(desc)
            assertEquals("wall_street_english", res.merchant?.id)
            assertEquals(MerchantMatchStatus.MATCHED, res.status)
        }

        // 7. Çıplak TURKUVAZ Fail-Closed ve Ambiguity Yokluğu
        val turkuvazRes = recognizeUser("TURKUVAZ")
        assertNull(turkuvazRes.merchant, "Çıplak 'TURKUVAZ' hiçbir merchant'a eşleşmemelidir")
        assertEquals(MerchantMatchStatus.NO_MATCH, turkuvazRes.status)

        val idefixPozitif = listOf("IDEFIX", "IDEFIX COM")
        for (desc in idefixPozitif) {
            val res = recognizeUser(desc)
            assertEquals("idefix", res.merchant?.id)
            assertEquals(MerchantMatchStatus.MATCHED, res.status)
        }
    }

    @Test
    fun fazH4G2K1_aileVeEvcilHayvanIlk9Merchant_tanimaVeGuvenlikSozlesmesi() {
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        assertEquals(223, merchants.count { it.verifiedLogoKey != null })
        assertEquals(37, merchants.count { it.verifiedLogoKey == null })

        // 1. 8 Yeni K1 Merchant ve Doğruluk Kontrolü
        val k1Ids =
            listOf(
                "petlebi",
                "civil",
                "temizmama",
                "toyzz_shop",
                "juen_pet_market",
                "joker_baby",
                "armagan_oyuncak",
                "petzz_shop",
            )
        for (id in k1Ids) {
            val m = merchants.firstOrNull { it.id == id }
            assertNotNull(m, "K1 merchant '$id' katalogda bulunmalıdır")
            assertEquals(id, m.verifiedLogoKey, "verifiedLogoKey '$id' olmalıdır")
            assertEquals(26, m.catalogVersion)
            assertEquals("family_pets", m.categoryIconKey.key)
            val normalizedAliases = m.aliases.map { TurkishDescriptionNormalizer.normalize(it.value) }
            assertEquals(
                normalizedAliases.size,
                normalizedAliases.distinct().size,
                "Merchant '$id' için mükerrer normalized alias bulunmamalıdır",
            )
        }

        // 2. Exact Alias Pozitif Eşleşmeleri
        val pozitifler =
            listOf(
                "PETLEBI" to "petlebi",
                "PETLEBI COM" to "petlebi",
                "CIVILIM" to "civil",
                "CIVILIM COM" to "civil",
                "CIVIL COCUK GIYIM" to "civil",
                "CIVIL MAGAZACILIK" to "civil",
                "TEMIZMAMA" to "temizmama",
                "TEMIZMAMA COM" to "temizmama",
                "CAGATAY EVCIL HAYVAN MAMALARI" to "temizmama",
                "TOYZZ SHOP" to "toyzz_shop",
                "TOYZZSHOP" to "toyzz_shop",
                "TOYPA MAGAZACILIK" to "toyzz_shop",
                "JUEN PET MARKET" to "juen_pet_market",
                "JUEN PET" to "juen_pet_market",
                "JUENPETMARKET" to "juen_pet_market",
                "JOKER BABY" to "joker_baby",
                "JOKER COM TR" to "joker_baby",
                "ARMAGAN OYUNCAK" to "armagan_oyuncak",
                "ARMAGANOYUNCAK" to "armagan_oyuncak",
                "ARMAGAN OYUNCAK SANAYI" to "armagan_oyuncak",
                "PETZZ SHOP" to "petzz_shop",
                "PETZZSHOP" to "petzz_shop",
                "PETZZ" to "petzz_shop",
            )
        for ((desc, expectedId) in pozitifler) {
            val userRes = recognizeUser(desc)
            assertEquals(expectedId, userRes.merchant?.id, "'$desc' user description $expectedId ile eşleşmelidir")
            assertEquals(MerchantMatchStatus.MATCHED, userRes.status)

            val bankRes = recognizeBank("POS $desc ISTANBUL")
            assertEquals(expectedId, bankRes.merchant?.id, "'POS $desc ISTANBUL' bank description $expectedId ile eşleşmelidir")
            assertEquals(MerchantMatchStatus.MATCHED, bankRes.status)
        }

        // 3. Generic ve Fail-Closed Negatifleri (MerchantMatchStatus.NO_MATCH)
        val genericNegatifler =
            listOf(
                "CIVIL",
                "CIVIL DEFENSE",
                "CIVIL ENGINEERING",
                "JOKER",
                "JOKER BONUS",
                "PET",
                "PET SHOP",
                "PET MARKET",
                "JUEN PET URUNLERI",
                "MAMA",
                "CAGATAY MAMA",
                "BEBEK MAMASI",
                "BEBEK",
                "COCUK",
                "ARMAGAN",
                "SHOP",
                "OYUNCAK",
            )
        for (token in genericNegatifler) {
            val userRes = recognizeUser(token)
            assertNull(userRes.merchant, "Jenerik veya negatif '$token' merchant üretmemelidir")
            assertEquals(MerchantMatchStatus.NO_MATCH, userRes.status, "Jenerik '$token' NO_MATCH olmalıdır")

            val bankRes = recognizeBank("POS $token KART HARCAMASI")
            assertNull(bankRes.merchant, "Jenerik veya negatif banka '$token' merchant üretmemelidir")
            assertEquals(MerchantMatchStatus.NO_MATCH, bankRes.status, "Jenerik banka '$token' NO_MATCH olmalıdır")
        }

        // 4. Jolly Joker İzolasyonu (Hem joker_baby hem jolly_tur için fail-closed)
        val jollyJokerUser = recognizeUser("JOLLY JOKER")
        assertNull(jollyJokerUser.merchant, "JOLLY JOKER izole edilmiş negatif terimdir")
        assertEquals(MerchantMatchStatus.NO_MATCH, jollyJokerUser.status)

        val jollyJokerBank = recognizeBank("POS JOLLY JOKER VADI ISTANBUL")
        assertNull(jollyJokerBank.merchant, "POS JOLLY JOKER izole edilmiş negatif terimdir")
        assertEquals(MerchantMatchStatus.NO_MATCH, jollyJokerBank.status)
    }

    @Test
    fun fazH4G2K2_kalan8AileVeEvcilHayvanSearchableMerchant_tanimaVeGuvenlikSozlesmesi() {
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        assertEquals(223, merchants.count { it.verifiedLogoKey != null })
        assertEquals(37, merchants.count { it.verifiedLogoKey == null })

        // 1. 8 Yeni K2 Merchant ve Doğruluk Kontrolü
        val k2Ids =
            listOf(
                "markamama",
                "petburada",
                "petihtiyac",
                "kolaymama",
                "panco",
                "bg_store",
                "minycenter",
                "vetamerikan",
            )
        for (id in k2Ids) {
            val m = merchants.firstOrNull { it.id == id }
            assertNotNull(m, "K2 merchant '$id' katalogda bulunmalıdır")
            assertEquals(id, m.verifiedLogoKey, "verifiedLogoKey '$id' olmalıdır")
            assertEquals(26, m.catalogVersion)
            assertEquals("family_pets", m.categoryIconKey.key)
            val normalizedAliases = m.aliases.map { TurkishDescriptionNormalizer.normalize(it.value) }
            assertEquals(
                normalizedAliases.size,
                normalizedAliases.distinct().size,
                "Merchant '$id' için mükerrer normalized alias bulunmamalıdır",
            )
        }

        // 1a. Gerçek Üretim Alias ve Negatif Doğrulamaları
        val markamama = merchants.first { it.id == "markamama" }
        assertEquals(
            listOf("MARKAMAMA", "MARKAMAMA COM TR", "MARKAMAMA PET SANAYI"),
            markamama.aliases.map { it.value },
        )
        val markamamaYasaklar = listOf("MARKAMAMA COM", "MARKA MAMA", "MAMA ITRISAT")
        for (yasak in markamamaYasaklar) {
            assertFalse(
                markamama.aliases.any { it.value == yasak },
                "markamama yasak '$yasak' alias'ını içermemelidir",
            )
        }

        val kolaymama = merchants.first { it.id == "kolaymama" }
        assertEquals(
            listOf("KOLAYMAMA", "KOLAY MAMA", "KOLAYMAMA COM", "DORATEK GIDA"),
            kolaymama.aliases.map { it.value },
        )
        assertFalse(
            kolaymama.aliases.any { it.value == "KOLAY MAMA PAZARLAMA" },
            "kolaymama yasak 'KOLAY MAMA PAZARLAMA' alias'ını içermemelidir",
        )

        val bgStore = merchants.first { it.id == "bg_store" }
        assertEquals(
            listOf("BG STORE", "B&G STORE", "BGSTORE", "BGSTORE COM TR", "BGSTORE MAGAZACILIK"),
            bgStore.aliases.map { it.value },
        )
        assertFalse(
            bgStore.aliases.any { it.value == "BG TEKSTIL" },
            "bg_store yasak 'BG TEKSTIL' alias'ını içermemelidir",
        )

        val petburada = merchants.first { it.id == "petburada" }
        assertEquals(
            listOf("PETBURADA", "PET BURADA", "PETBURADA COM", "AKVET VETERINERLIK"),
            petburada.aliases.map { it.value },
        )
        assertEquals(listOf("HEPSIBURADA"), petburada.negativeAliases.map { it.value })

        val petihtiyac = merchants.first { it.id == "petihtiyac" }
        assertEquals(
            listOf("PETIHTIYAC", "PET IHTIYAC", "PETIHTIYAC COM", "SENTURKLER VETERINERLIK"),
            petihtiyac.aliases.map { it.value },
        )

        val panco = merchants.first { it.id == "panco" }
        assertEquals(
            listOf("PANCO", "PANCO COM TR", "PANCO COCUK", "PANCO GIYIM"),
            panco.aliases.map { it.value },
        )

        val minycenter = merchants.first { it.id == "minycenter" }
        assertEquals(
            listOf("MINYCENTER", "MINY CENTER", "MINYCENTER COM TR", "GAMI GIYIM"),
            minycenter.aliases.map { it.value },
        )

        val vetamerikan = merchants.first { it.id == "vetamerikan" }
        assertEquals(
            listOf(
                "VETAMERIKAN",
                "VET AMERIKAN",
                "VETAMERIKAN ORG",
                "VETAMERIKAN HAYVAN HASTANESI",
                "VET AMERIKAN HAYVAN HASTANESI",
                "VETAMERIKAN VETERINERLIK",
            ),
            vetamerikan.aliases.map { it.value },
        )
        assertTrue(vetamerikan.negativeAliases.isEmpty(), "vetamerikan için gereksiz negatif alias bulunmamalıdır")

        // 2. Exact ve Hizmet Alias Pozitif Eşleşmeleri
        val pozitifler =
            listOf(
                "MARKAMAMA" to "markamama",
                "MARKAMAMA COM TR" to "markamama",
                "MARKAMAMA PET SANAYI" to "markamama",
                "PETBURADA" to "petburada",
                "PET BURADA" to "petburada",
                "PETBURADA COM" to "petburada",
                "AKVET VETERINERLIK" to "petburada",
                "PETIHTIYAC" to "petihtiyac",
                "PET IHTIYAC" to "petihtiyac",
                "PETIHTIYAC COM" to "petihtiyac",
                "SENTURKLER VETERINERLIK" to "petihtiyac",
                "KOLAYMAMA" to "kolaymama",
                "KOLAY MAMA" to "kolaymama",
                "KOLAYMAMA COM" to "kolaymama",
                "DORATEK GIDA" to "kolaymama",
                "PANCO" to "panco",
                "PANCO COM TR" to "panco",
                "PANCO COCUK" to "panco",
                "PANCO GIYIM" to "panco",
                "BG STORE" to "bg_store",
                "B&G STORE" to "bg_store",
                "BGSTORE" to "bg_store",
                "BGSTORE COM TR" to "bg_store",
                "BGSTORE MAGAZACILIK" to "bg_store",
                "MINYCENTER" to "minycenter",
                "MINY CENTER" to "minycenter",
                "MINYCENTER COM TR" to "minycenter",
                "GAMI GIYIM" to "minycenter",
                "VETAMERIKAN" to "vetamerikan",
                "VET AMERIKAN" to "vetamerikan",
                "VETAMERIKAN ORG" to "vetamerikan",
                "VETAMERIKAN HAYVAN HASTANESI" to "vetamerikan",
                "VET AMERIKAN HAYVAN HASTANESI" to "vetamerikan",
                "VETAMERIKAN VETERINERLIK" to "vetamerikan",
            )
        for ((desc, expectedId) in pozitifler) {
            val userRes = recognizeUser(desc)
            assertEquals(expectedId, userRes.merchant?.id, "'$desc' user description $expectedId ile eşleşmelidir")
            assertEquals(MerchantMatchStatus.MATCHED, userRes.status)

            val bankRes = recognizeBank("POS $desc ISTANBUL")
            assertEquals(expectedId, bankRes.merchant?.id, "'POS $desc ISTANBUL' bank description $expectedId ile eşleşmelidir")
            assertEquals(MerchantMatchStatus.MATCHED, bankRes.status)
        }

        // 3. Generic ve Fail-Closed Negatifleri (MerchantMatchStatus.NO_MATCH)
        val genericNegatifler =
            listOf(
                "MAMA",
                "STORE",
                "CENTER",
                "VETERINER",
                "VETERINERLIK",
                "HAYVAN",
                "HAYVAN HASTANESI",
                "DORATEK",
                "GAMI",
                "AKVET",
            )
        for (token in genericNegatifler) {
            val userRes = recognizeUser(token)
            assertNull(userRes.merchant, "Jenerik '$token' merchant üretmemelidir")
            assertEquals(MerchantMatchStatus.NO_MATCH, userRes.status, "Jenerik '$token' NO_MATCH olmalıdır")

            val bankRes = recognizeBank("POS $token KART HARCAMASI")
            assertNull(bankRes.merchant, "Jenerik banka '$token' merchant üretmemelidir")
            assertEquals(MerchantMatchStatus.NO_MATCH, bankRes.status, "Jenerik banka '$token' NO_MATCH olmalıdır")
        }

        // 3a. K2 Güvenlik Girdileri ve Yanlış Eşleşme İzolasyonu
        val k2GuvenlikGirdileri =
            listOf(
                "MARKA",
                "PET",
                "IHTIYAC",
                "IHTIYAC KREDISI",
                "COCUK",
                "GIYIM",
                "BEBEK",
                "BG",
                "APP STORE",
                "PLAY STORE",
                "MINI",
                "MINI CENTER",
                "VET",
            )
        val k2IdSet = k2Ids.toSet()
        for (girdi in k2GuvenlikGirdileri) {
            val userRes = recognizeUser(girdi)
            assertFalse(
                k2IdSet.contains(userRes.merchant?.id),
                "Girdi '$girdi' (user description) hiçbir K2 merchant'ını üretmemelidir; ancak '${userRes.merchant?.id}' üretti",
            )
            assertNull(userRes.merchant, "Girdi '$girdi' meşru eşleşme olmadığında null olmalıdır")
            assertEquals(MerchantMatchStatus.NO_MATCH, userRes.status)

            val bankRes = recognizeBank(girdi)
            assertFalse(
                k2IdSet.contains(bankRes.merchant?.id),
                "Girdi '$girdi' (bank description) hiçbir K2 merchant'ını üretmemelidir; ancak '${bankRes.merchant?.id}' üretti",
            )
            assertNull(bankRes.merchant, "Girdi '$girdi' bank description için null olmalıdır")
            assertEquals(MerchantMatchStatus.NO_MATCH, bankRes.status)

            val bankPosRes = recognizeBank("POS $girdi ISTANBUL")
            assertFalse(
                k2IdSet.contains(bankPosRes.merchant?.id),
                "POS '$girdi' hiçbir K2 merchant'ını üretmemelidir; ancak '${bankPosRes.merchant?.id}' üretti",
            )
            assertNull(bankPosRes.merchant, "POS '$girdi' için merchant null olmalıdır")
            assertEquals(MerchantMatchStatus.NO_MATCH, bankPosRes.status)
        }

        // 4. Negatif İzolasyon ve Çapraz Çakışma Önleme
        // 4a. HEPSIBURADA petburada'yı tetiklememeli, hepsiburada ile eşleşmelidir
        val hbUser = recognizeUser("HEPSIBURADA")
        assertEquals("hepsiburada", hbUser.merchant?.id)
        assertTrue(hbUser.merchant?.id != "petburada")

        val hbBank = recognizeBank("POS HEPSIBURADA ISTANBUL")
        assertEquals("hepsiburada", hbBank.merchant?.id)
        assertTrue(hbBank.merchant?.id != "petburada")

        // 4b. AMERIKAN HASTANESI vetamerikan'ı tetiklememeli, amerikan_hastanesi ile eşleşmelidir
        val amHastaneUser = recognizeUser("AMERIKAN HASTANESI")
        assertEquals("amerikan_hastanesi", amHastaneUser.merchant?.id)
        assertTrue(amHastaneUser.merchant?.id != "vetamerikan")

        val amHastaneBank = recognizeBank("POS AMERIKAN HASTANESI NISANTASI")
        assertEquals("amerikan_hastanesi", amHastaneBank.merchant?.id)
        assertTrue(amHastaneBank.merchant?.id != "vetamerikan")

        // 4c. VET AMERIKAN amerikan_hastanesi'ni tetiklememeli, vetamerikan ile eşleşmelidir
        val vetAmUser = recognizeUser("VET AMERIKAN")
        assertEquals("vetamerikan", vetAmUser.merchant?.id)
        assertTrue(vetAmUser.merchant?.id != "amerikan_hastanesi")

        val vetAmBank = recognizeBank("POS VET AMERIKAN HAYVAN HASTANESI")
        assertEquals("vetamerikan", vetAmBank.merchant?.id)
        assertTrue(vetAmBank.merchant?.id != "amerikan_hastanesi")

        // 4d. VET AMERIKAN HASTANESI vetamerikan ile eşleşmeli, amerikan_hastanesi olmamalıdır
        val vetAmHastaneUser = recognizeUser("VET AMERIKAN HASTANESI")
        assertEquals("vetamerikan", vetAmHastaneUser.merchant?.id)
        assertTrue(vetAmHastaneUser.merchant?.id != "amerikan_hastanesi")

        val vetAmHastaneBank = recognizeBank("POS VET AMERIKAN HASTANESI")
        assertEquals("vetamerikan", vetAmHastaneBank.merchant?.id)
        assertTrue(vetAmHastaneBank.merchant?.id != "amerikan_hastanesi")

        val vkvAmBank = recognizeBank("POS VKV AMERIKAN HASTANESI")
        assertEquals("amerikan_hastanesi", vkvAmBank.merchant?.id)
        assertTrue(vkvAmBank.merchant?.id != "vetamerikan")

        // 4e. amerikan_hastanesi negatif listesinde VET AMERIKAN ve VETAMERIKAN bulunmalıdır
        val amHastanesi = merchants.first { it.id == "amerikan_hastanesi" }
        val amHastanesiNegatifler = amHastanesi.negativeAliases.map { it.value }
        assertTrue(
            amHastanesiNegatifler.contains("VET AMERIKAN"),
            "amerikan_hastanesi 'VET AMERIKAN' negatif alias'ını içermelidir",
        )
        assertTrue(
            amHastanesiNegatifler.contains("VETAMERIKAN"),
            "amerikan_hastanesi 'VETAMERIKAN' negatif alias'ını içermelidir",
        )
    }

    @Test
    fun fazH4G2L1_bagisVeSivilToplum8Merchant_tanimaVeGuvenlikSozlesmesi() {
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        assertEquals(223, merchants.count { it.verifiedLogoKey != null })
        assertEquals(37, merchants.count { it.verifiedLogoKey == null })

        // 1. 8 Yeni L1 Merchant ve Doğruluk Kontrolü
        val l1Ids =
            listOf(
                "kizilay",
                "losev",
                "tema",
                "tev",
                "mehmetcik_vakfi",
                "akut",
                "acev",
                "tog",
            )
        for (id in l1Ids) {
            val m = merchants.firstOrNull { it.id == id }
            assertNotNull(m, "L1 merchant '$id' katalogda bulunmalıdır")
            assertEquals(id, m.verifiedLogoKey, "verifiedLogoKey '$id' olmalıdır")
            assertEquals(26, m.catalogVersion)
            assertEquals("gifts_donations", m.categoryIconKey.key)
            val normalizedAliases = m.aliases.map { TurkishDescriptionNormalizer.normalize(it.value) }
            assertEquals(
                normalizedAliases.size,
                normalizedAliases.distinct().size,
                "Merchant '$id' için mükerrer normalized alias bulunmamalıdır",
            )
        }

        // 1a. Gerçek Üretim Alias ve Negatif Doğrulamaları
        val kizilay = merchants.first { it.id == "kizilay" }
        assertEquals(
            listOf(
                "TURK KIZILAY",
                "TURK KIZILAYI",
                "TURKIYE KIZILAY DERNEGI",
                "KIZILAY BAGIS",
            ),
            kizilay.aliases.map { it.value },
        )
        assertEquals(
            listOf("KIZILAY AVM", "KIZILAY METRO", "KIZILAY YATIRIM", "KIZILAY MAGAZA", "KIZILAY SAGLIK"),
            kizilay.negativeAliases.map { it.value },
        )

        val losev = merchants.first { it.id == "losev" }
        assertEquals(
            listOf(
                "LOSEV",
                "LOSEMILI COCUKLAR SAGLIK VE EGITIM VAKFI",
                "LOSEV VAKFI",
                "LOSEV BAGIS",
            ),
            losev.aliases.map { it.value },
        )
        assertTrue(losev.negativeAliases.isEmpty())

        val tema = merchants.first { it.id == "tema" }
        assertEquals(
            listOf(
                "TEMA TURKIYE EROZYONLA MUCADELE AGACLANDIRMA VE DOGAL VARLIKLARI KORUMA VAKFI",
                "TEMA VAKFI",
                "TEMA BAGIS",
            ),
            tema.aliases.map { it.value },
        )
        assertTrue(tema.negativeAliases.isEmpty())

        val tev = merchants.first { it.id == "tev" }
        assertEquals(
            listOf(
                "TURK EGITIM VAKFI",
                "TEV VAKFI",
                "TEV BAGIS",
            ),
            tev.aliases.map { it.value },
        )
        assertTrue(tev.negativeAliases.isEmpty())

        val mehmetcikVakfi = merchants.first { it.id == "mehmetcik_vakfi" }
        assertEquals(
            listOf(
                "TURK SILAHLI KUVVETLERI MEHMETCIK VAKFI",
                "TSK MEHMETCIK VAKFI",
                "MEHMETCIK VAKFI",
                "MEHMETCIK VAKFI BAGIS",
            ),
            mehmetcikVakfi.aliases.map { it.value },
        )
        assertEquals(listOf("TSKGV"), mehmetcikVakfi.negativeAliases.map { it.value })

        val akut = merchants.first { it.id == "akut" }
        assertEquals(
            listOf(
                "AKUT ARAMA KURTARMA",
                "AKUT DERNEGI",
                "AKUT BAGIS",
            ),
            akut.aliases.map { it.value },
        )
        assertTrue(akut.negativeAliases.isEmpty())

        val acev = merchants.first { it.id == "acev" }
        assertEquals(
            listOf(
                "ANNE COCUK EGITIM VAKFI",
                "ACEV",
                "ACEV VAKFI",
                "ACEV BAGIS",
            ),
            acev.aliases.map { it.value },
        )
        assertTrue(acev.negativeAliases.isEmpty())

        val tog = merchants.first { it.id == "tog" }
        assertEquals(
            listOf(
                "TOPLUM GONULLULERI VAKFI",
                "TOG VAKFI",
                "TOG BAGIS",
            ),
            tog.aliases.map { it.value },
        )
        assertEquals(listOf("TOGG"), tog.negativeAliases.map { it.value })

        // 2. Exact ve Hizmet Alias Pozitif Eşleşmeleri ve Skor Doğrulaması
        val exactPozitifler =
            listOf(
                "TURK KIZILAY" to "kizilay",
                "TURK KIZILAYI" to "kizilay",
                "TURKIYE KIZILAY DERNEGI" to "kizilay",
                "KIZILAY BAGIS" to "kizilay",
                "LOSEV" to "losev",
                "LOSEV VAKFI" to "losev",
                "LOSEMILI COCUKLAR SAGLIK VE EGITIM VAKFI" to "losev",
                "LOSEV BAGIS" to "losev",
                "TEMA VAKFI" to "tema",
                "TEMA BAGIS" to "tema",
                "TURK EGITIM VAKFI" to "tev",
                "TEV VAKFI" to "tev",
                "TEV BAGIS" to "tev",
                "MEHMETCIK VAKFI" to "mehmetcik_vakfi",
                "TSK MEHMETCIK VAKFI" to "mehmetcik_vakfi",
                "TURK SILAHLI KUVVETLERI MEHMETCIK VAKFI" to "mehmetcik_vakfi",
                "MEHMETCIK VAKFI BAGIS" to "mehmetcik_vakfi",
                "AKUT ARAMA KURTARMA" to "akut",
                "AKUT DERNEGI" to "akut",
                "AKUT BAGIS" to "akut",
                "TOPLUM GONULLULERI VAKFI" to "tog",
                "TOG VAKFI" to "tog",
                "TOG BAGIS" to "tog",
            )
        for ((desc, expectedId) in exactPozitifler) {
            val userRes = recognizeUser(desc)
            assertEquals(expectedId, userRes.merchant?.id, "'$desc' user description $expectedId ile eşleşmelidir")
            assertEquals(MerchantMatchStatus.MATCHED, userRes.status)
            assertEquals(MerchantMatchSource.EXACT_ALIAS, userRes.source)
            assertEquals(95, userRes.confidence)
            assertTrue(userRes.canShowMerchantLogo)

            val bankRes = recognizeBank(desc)
            assertEquals(expectedId, bankRes.merchant?.id, "'$desc' bank description $expectedId ile eşleşmelidir")
            assertEquals(MerchantMatchStatus.MATCHED, bankRes.status)
            assertEquals(MerchantMatchSource.EXACT_ALIAS, bankRes.source)
            assertEquals(95, bankRes.confidence)
            assertTrue(bankRes.canShowMerchantLogo)

            val resolved = TransactionMerchantAttributionResolver.resolve(desc)
            assertNotNull(resolved, "'$desc' resolver tarafından çözümlenmelidir")
            assertEquals(expectedId, resolved.merchantId.value)
            assertEquals(95, resolved.confidence)
        }

        // 2a. Kısa Alias Kontrolü (ACEV: 4 karakter)
        val userAcev = recognizeUser("ACEV")
        assertEquals("acev", userAcev.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, userAcev.status)
        assertEquals(MerchantMatchSource.EXACT_ALIAS, userAcev.source)
        assertEquals(95, userAcev.confidence)
        assertTrue(userAcev.canShowMerchantLogo)
        val resolvedAcev = TransactionMerchantAttributionResolver.resolve("ACEV")
        assertNotNull(resolvedAcev)
        assertEquals("acev", resolvedAcev.merchantId.value)
        assertEquals(95, resolvedAcev.confidence)

        val bankAcev = recognizeBank("ACEV")
        assertEquals("acev", bankAcev.merchant?.id)
        assertEquals(MerchantMatchStatus.LOW_CONFIDENCE, bankAcev.status)
        assertEquals(MerchantMatchSource.SHORT_ALIAS, bankAcev.source)
        assertEquals(65, bankAcev.confidence)
        assertFalse(bankAcev.canShowMerchantLogo)

        // 2b. Embedded Eşleşme (POS TSK MEHMETCIK VAKFI ISTANBUL -> STRONG_BRAND / 80)
        val bankMehmetcik = recognizeBank("POS TSK MEHMETCIK VAKFI ISTANBUL")
        assertEquals("mehmetcik_vakfi", bankMehmetcik.merchant?.id)
        assertEquals(MerchantMatchStatus.MATCHED, bankMehmetcik.status)
        assertEquals(MerchantMatchSource.STRONG_BRAND, bankMehmetcik.source)
        assertEquals(80, bankMehmetcik.confidence)
        assertTrue(bankMehmetcik.canShowMerchantLogo)
        val resolvedMehmetcik = TransactionMerchantAttributionResolver.resolve("POS TSK MEHMETCIK VAKFI ISTANBUL")
        assertNotNull(resolvedMehmetcik)
        assertEquals("mehmetcik_vakfi", resolvedMehmetcik.merchantId.value)
        assertEquals(80, resolvedMehmetcik.confidence)

        // 3. 20 Negatif Örnek (Hepsi NO_MATCH)
        val twentyNegatives =
            listOf(
                "KIZILAY",
                "KIZILAY AVM",
                "KIZILAY ANKARA",
                "KIZILAY METRO",
                "KIZILAY YATIRIM",
                "TEV",
                "TEMA",
                "TEMA TASARIM",
                "AKUT",
                "AKUT APANDISIT",
                "TOG",
                "TOGG",
                "TSKGV",
                "FONZIP",
                "VAKIF",
                "DERNEK",
                "BAGIS",
                "YARDIM",
                "DARUSSAFAKA DOGUS",
                "AHBAP",
            )
        for (neg in twentyNegatives) {
            val userRes = recognizeUser(neg)
            assertNull(userRes.merchant, "Negatif '$neg' user description merchant üretmemelidir")
            assertEquals(MerchantMatchStatus.NO_MATCH, userRes.status, "Negatif '$neg' NO_MATCH olmalıdır")

            val bankRes = recognizeBank(neg)
            assertNull(bankRes.merchant, "Negatif '$neg' bank description merchant üretmemelidir")
            assertEquals(MerchantMatchStatus.NO_MATCH, bankRes.status, "Negatif '$neg' bank NO_MATCH olmalıdır")

            val bankPosRes = recognizeBank("POS $neg ISTANBUL")
            assertNull(bankPosRes.merchant, "POS '$neg' merchant üretmemelidir")
            assertEquals(MerchantMatchStatus.NO_MATCH, bankPosRes.status, "POS '$neg' NO_MATCH olmalıdır")

            assertNull(TransactionMerchantAttributionResolver.resolve(neg), "Negatif '$neg' resolver null dönmelidir")
            assertNull(TransactionMerchantAttributionResolver.resolve("POS $neg ISTANBUL"))
        }

        // 4. 3 Transfer Exclusion (EFT, FAST, HAVALE)
        val threeTransfers =
            listOf(
                "EFT TURKIYE KIZILAY DERNEGI",
                "FAST LOSEV BAGIS",
                "HAVALE TOPLUM GONULLULERI VAKFI",
            )
        for (desc in threeTransfers) {
            val userRes = recognizeUser(desc)
            assertNull(userRes.merchant, "Transfer '$desc' merchant null olmalıdır")
            assertEquals(0, userRes.confidence)
            assertEquals(MerchantMatchStatus.EXCLUDED_TRANSACTION, userRes.status)
            assertEquals(TransactionClassification.PERSONAL_TRANSFER, userRes.transactionClassification)
            assertNull(userRes.source)
            assertFalse(userRes.canShowMerchantLogo)

            val bankRes = recognizeBank(desc)
            assertNull(bankRes.merchant, "Transfer '$desc' bank merchant null olmalıdır")
            assertEquals(0, bankRes.confidence)
            assertEquals(MerchantMatchStatus.EXCLUDED_TRANSACTION, bankRes.status)
            assertEquals(TransactionClassification.PERSONAL_TRANSFER, bankRes.transactionClassification)
            assertNull(bankRes.source)
            assertFalse(bankRes.canShowMerchantLogo)

            assertNull(TransactionMerchantAttributionResolver.resolve(desc), "Transfer '$desc' resolver null dönmelidir")
        }
    }

    @Test
    fun starterCatalog_m1aTaxesFees_gibAndSgkContract() {
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(260, merchants.size)
        assertEquals(26, StarterMerchantCatalog.CATALOG_VERSION)
        assertEquals(223, merchants.count { it.verifiedLogoKey != null })

        // 1. GİB ve SGK varlık ve alan kontrolleri
        val gib = merchants.firstOrNull { it.id == "gib" }
        assertNotNull(gib, "gib katalogda bulunmalıdır")
        assertEquals("Gelir İdaresi Başkanlığı", gib.displayName)
        assertEquals("taxes_fees", gib.categoryIconKey.key)
        assertEquals("gib", gib.verifiedLogoKey)
        assertEquals(26, gib.catalogVersion)
        assertEquals(
            listOf(
                "GELIR IDARESI BASKANLIGI",
                "DIJITAL VERGI DAIRESI",
                "INTERNET VERGI DAIRESI",
            ),
            gib.aliases.map { it.value },
        )
        assertEquals(listOf("IBAN", "TRANSFER"), gib.negativeAliases.map { it.value })

        val sgk = merchants.firstOrNull { it.id == "sgk" }
        assertNotNull(sgk, "sgk katalogda bulunmalıdır")
        assertEquals("Sosyal Güvenlik Kurumu", sgk.displayName)
        assertEquals("taxes_fees", sgk.categoryIconKey.key)
        assertEquals("sgk", sgk.verifiedLogoKey)
        assertEquals(26, sgk.catalogVersion)
        assertEquals(
            listOf(
                "SOSYAL GUVENLIK KURUMU",
            ),
            sgk.aliases.map { it.value },
        )
        assertEquals(listOf("IBAN", "TRANSFER", "MOSIP"), sgk.negativeAliases.map { it.value })

        // 2. Pozitif eşleşmeler
        val positiveCases =
            listOf(
                "GELIR IDARESI BASKANLIGI" to "gib",
                "DIJITAL VERGI DAIRESI" to "gib",
                "INTERNET VERGI DAIRESI" to "gib",
                "SOSYAL GUVENLIK KURUMU" to "sgk",
            )
        for ((desc, expectedId) in positiveCases) {
            val userRes = recognizeUser(desc)
            assertNotNull(userRes.merchant, "'$desc' merchant üretmelidir")
            assertEquals(expectedId, userRes.merchant?.id, "'$desc' $expectedId ile eşleşmelidir")
            assertEquals(MerchantMatchStatus.MATCHED, userRes.status)
            assertTrue(userRes.canShowMerchantLogo)

            val bankRes = recognizeBank(desc)
            assertNotNull(bankRes.merchant, "Bank '$desc' merchant üretmelidir")
            assertEquals(expectedId, bankRes.merchant?.id)
            assertEquals(MerchantMatchStatus.MATCHED, bankRes.status)
            assertTrue(bankRes.canShowMerchantLogo)

            val attrib = TransactionMerchantAttributionResolver.resolve(desc)
            assertNotNull(attrib, "'$desc' resolver ile çözümlenmelidir")
            assertEquals(expectedId, attrib.merchantId.value)
        }

        // 3. Negatif / Fail-Closed senaryolar
        val negativeCases =
            listOf(
                "GIB",
                "SGK",
                "GIB SANAL POS",
                "GIB VERGI ODEMESI",
                "SGK PRIM ODEMESI",
                "SGK MOSIP",
                "KART ILE PRIM ODEME",
                "IBAN TRANSFER GELIR IDARESI BASKANLIGI",
                "IBAN TRANSFER SOSYAL GUVENLIK KURUMU",
                "SOSYAL GUVENLIK KURUMU MOSIP",
                "BELEDIYE VERGI",
                "E DEVLET",
                "BANKA KOMISYONU",
                "KART AIDATI",
            )
        for (neg in negativeCases) {
            val userRes = recognizeUser(neg)
            assertNull(userRes.merchant, "Negatif '$neg' merchant üretmemelidir (fail-closed)")
            assertEquals(MerchantMatchStatus.NO_MATCH, userRes.status, "Negatif '$neg' NO_MATCH olmalıdır")

            val bankRes = recognizeBank(neg)
            assertNull(bankRes.merchant, "Negatif '$neg' bank merchant üretmemelidir (fail-closed)")
            assertEquals(MerchantMatchStatus.NO_MATCH, bankRes.status, "Negatif '$neg' bank NO_MATCH olmalıdır")

            assertNull(TransactionMerchantAttributionResolver.resolve(neg), "Negatif '$neg' resolver null dönmelidir")
        }

        // 4. Transfer exclusion (EFT / FAST / HAVALE) regresyonları
        val transfers =
            listOf(
                "EFT GELIR IDARESI BASKANLIGI",
                "FAST SOSYAL GUVENLIK KURUMU",
                "HAVALE DIJITAL VERGI DAIRESI",
            )
        for (desc in transfers) {
            val userRes = recognizeUser(desc)
            assertNull(userRes.merchant, "Transfer '$desc' merchant null olmalıdır")
            assertEquals(0, userRes.confidence)
            assertEquals(MerchantMatchStatus.EXCLUDED_TRANSACTION, userRes.status)
            assertEquals(TransactionClassification.PERSONAL_TRANSFER, userRes.transactionClassification)
            assertFalse(userRes.canShowMerchantLogo)

            val bankRes = recognizeBank(desc)
            assertNull(bankRes.merchant, "Transfer '$desc' bank merchant null olmalıdır")
            assertEquals(0, bankRes.confidence)
            assertEquals(MerchantMatchStatus.EXCLUDED_TRANSACTION, bankRes.status)
            assertEquals(TransactionClassification.PERSONAL_TRANSFER, bankRes.transactionClassification)
            assertFalse(bankRes.canShowMerchantLogo)

            assertNull(TransactionMerchantAttributionResolver.resolve(desc), "Transfer '$desc' resolver null dönmelidir")
        }
    }
}

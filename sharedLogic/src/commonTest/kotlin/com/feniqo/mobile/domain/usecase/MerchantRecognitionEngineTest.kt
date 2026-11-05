package com.feniqo.mobile.domain.usecase

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
        val primeResult = recognizeBank("AMAZON PRIME 1234")
        assertEquals("amazon_prime", primeResult.merchant?.id)
        assertEquals(90, primeResult.confidence)
        assertTrue(primeResult.canShowMerchantLogo)

        val amazonResult = recognizeBank("AMAZON COM TR 1234")
        assertEquals("amazon", amazonResult.merchant?.id)
        assertEquals(80, amazonResult.confidence)
        assertTrue(amazonResult.canShowMerchantLogo)

        val userPrime = recognizeUser("Amazon Prime")
        assertEquals("amazon_prime", userPrime.merchant?.id)

        val userAmazon = recognizeUser("Amazon")
        assertEquals("amazon", userAmazon.merchant?.id)
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
            )
        StarterMerchantCatalog.merchants.forEach { merchant ->
            if (merchant.id in approvedKeys) {
                assertEquals(
                    merchant.id,
                    merchant.verifiedLogoKey,
                    "Approved merchant '${merchant.id}' must carry stable logo key equal to its id.",
                )
            } else {
                assertNull(
                    merchant.verifiedLogoKey,
                    "Merchant '${merchant.id}' cannot have unverified logo key before real assets exist.",
                )
            }
        }
        assertEquals(44, StarterMerchantCatalog.merchants.count { it.verifiedLogoKey != null })
        assertEquals(107, StarterMerchantCatalog.merchants.count { it.verifiedLogoKey == null })
        assertEquals("getir", StarterMerchantCatalog.merchants.first { it.id == "getir" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "migros_sanal_market" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "yemeksepeti_market" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "trendyol_yemek" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "getiryemek" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "migros_yemek" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "turk_telekom" }.verifiedLogoKey)
        assertNull(StarterMerchantCatalog.merchants.first { it.id == "istanbulkart" }.verifiedLogoKey)
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
        assertEquals(151, merchants.size)
        assertEquals(151, merchants.map { it.id }.distinct().size)
        assertEquals(10, StarterMerchantCatalog.CATALOG_VERSION)
        assertTrue(merchants.all { it.catalogVersion == 10 })
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
        assertEquals(10, martiInfo.catalogVersion)

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
        assertEquals(10, tiktakInfo.catalogVersion)

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
    fun catalogV10_attributionsCarryVersionTenContract() {
        val merchants = StarterMerchantCatalog.merchants
        assertEquals(10, StarterMerchantCatalog.CATALOG_VERSION)
        assertTrue(merchants.all { it.catalogVersion == 10 }, "All 151 merchants must default to catalogVersion 10")

        val tk = TransactionMerchantAttributionResolver.resolve("TARIM KREDI")
        assertNotNull(tk)
        assertEquals("tarim_kredi", tk.merchantId.value)
        assertEquals(10, tk.catalogVersion)

        val getir = TransactionMerchantAttributionResolver.resolve("GETIR")
        assertNotNull(getir)
        assertEquals("getir", getir.merchantId.value)
        assertEquals(10, getir.catalogVersion)

        val netflix = TransactionMerchantAttributionResolver.resolve("NETFLIX")
        assertNotNull(netflix)
        assertEquals("netflix", netflix.merchantId.value)
        assertEquals(10, netflix.catalogVersion)
    }
}

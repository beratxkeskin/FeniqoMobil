package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantAlias
import com.feniqo.mobile.domain.model.MerchantAliasType
import com.feniqo.mobile.domain.model.MerchantMatchSource
import com.feniqo.mobile.domain.model.MerchantMatchStatus
import com.feniqo.mobile.domain.model.MerchantNegativeAlias
import com.feniqo.mobile.domain.model.MerchantRecognitionInputSource
import com.feniqo.mobile.domain.model.MerchantRecognitionRequest
import com.feniqo.mobile.domain.model.MerchantRecognitionResult
import com.feniqo.mobile.domain.model.TransactionClassification
import com.feniqo.mobile.domain.model.TransactionMerchantEligibility
import com.feniqo.mobile.domain.model.TransactionMerchantInfo
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import com.feniqo.mobile.domain.model.UserMerchantDecision
import com.feniqo.mobile.domain.repository.UserMerchantVerificationLookupResult
import com.feniqo.mobile.domain.repository.UserMerchantVerificationRepository

class MerchantRecognitionEngine(
    private val catalog: List<Merchant> = StarterMerchantCatalog.merchants,
) {
    private data class Candidate(
        val merchant: Merchant,
        val alias: String,
        val confidence: Int,
        val source: MerchantMatchSource,
        val priority: Int,
    )

    fun recognize(request: MerchantRecognitionRequest): MerchantRecognitionResult {
        val normalized = TurkishDescriptionNormalizer.normalize(request.rawDescription)
        val classification = TransactionMerchantEligibility.classify(normalized)
        if (TransactionMerchantEligibility.isExcluded(normalized)) {
            return result(request, normalized, classification, status = MerchantMatchStatus.EXCLUDED_TRANSACTION)
        }

        request.personalDecision?.let { decision ->
            when (decision) {
                is UserMerchantDecision.Assign -> {
                    val merchant = catalog.firstOrNull { it.id == decision.merchantId }
                    if (merchant != null) {
                        return result(
                            request = request,
                            normalized = normalized,
                            classification = classification,
                            merchant = merchant,
                            confidence = 100,
                            source = MerchantMatchSource.USER_VERIFIED,
                            status = MerchantMatchStatus.MATCHED,
                        )
                    } else {
                        return result(
                            request = request,
                            normalized = normalized,
                            classification = classification,
                            merchant = null,
                            confidence = 0,
                            source = null,
                            status = MerchantMatchStatus.INVALID_VERIFICATION,
                        )
                    }
                }
                is UserMerchantDecision.Block -> {
                    return result(
                        request = request,
                        normalized = normalized,
                        classification = classification,
                        merchant = null,
                        confidence = 0,
                        source = MerchantMatchSource.USER_VERIFIED,
                        status = MerchantMatchStatus.BLOCKED_BY_USER,
                    )
                }
            }
        }
        verifiedResult(
            merchantId = request.workspaceVerifiedMerchantId,
            source = MerchantMatchSource.WORKSPACE_VERIFIED,
            confidence = 100,
            request = request,
            normalized = normalized,
            classification = classification,
        )?.let { return it }
        verifiedResult(
            merchantId = request.bankVerifiedMerchantId,
            source = MerchantMatchSource.BANK_VERIFIED_ID,
            confidence = 98,
            request = request,
            normalized = normalized,
            classification = classification,
        )?.let { return it }

        val candidates = catalog.flatMap { candidatesFor(it, normalized, request.inputSource) }
        if (candidates.isEmpty()) {
            return result(request, normalized, classification, status = MerchantMatchStatus.NO_MATCH)
        }

        val groupedByMerchant =
            candidates
                .groupBy { it.merchant.id }
                .mapValues { (_, merchantCandidates) -> merchantCandidates.maxWith(candidateComparator) }
                .values
                .sortedWith(candidateComparator.reversed())

        val best = groupedByMerchant.first()
        val runnerUp = groupedByMerchant.getOrNull(1)

        if (runnerUp != null && (best.confidence - runnerUp.confidence) < MIN_DECISIVE_SCORE_GAP) {
            return result(request, normalized, classification, status = MerchantMatchStatus.AMBIGUOUS)
        }

        val status =
            if (best.confidence >= MerchantRecognitionResult.MIN_LOGO_CONFIDENCE) {
                MerchantMatchStatus.MATCHED
            } else {
                MerchantMatchStatus.LOW_CONFIDENCE
            }
        return result(
            request = request,
            normalized = normalized,
            classification = classification,
            merchant = best.merchant,
            confidence = best.confidence,
            source = best.source,
            status = status,
        )
    }

    private fun candidatesFor(
        merchant: Merchant,
        description: String,
        inputSource: MerchantRecognitionInputSource,
    ): List<Candidate> {
        if (merchant.negativeAliases.any { containsPhrase(description, TurkishDescriptionNormalizer.normalize(it.value)) }) {
            return emptyList()
        }
        return merchant.aliases.mapNotNull { alias ->
            val normalizedAlias = TurkishDescriptionNormalizer.normalize(alias.value)
            if (normalizedAlias.isEmpty()) return@mapNotNull null
            if (normalizedAlias in DISALLOWED_STANDALONE_GENERIC_TOKENS) return@mapNotNull null
            if (!containsPhrase(description, normalizedAlias)) return@mapNotNull null

            val isExactFullMatch = description == normalizedAlias
            val isShortAlias = alias.type == MerchantAliasType.SHORT || normalizedAlias.length <= SHORT_ALIAS_MAX_LENGTH

            val effectiveType =
                when {
                    isShortAlias -> {
                        // Kaynağa duyarlı kısa marka kuralı:
                        // Kullanıcı tarafından yazılan açıklamanın tamamı doğrulanmış kısa marka alias'ıyla
                        // birebir eşleşirse EXACT kabul edilebilir (95 puan, logo uygun).
                        // Banka açıklaması veya tam eşleşmeyen kullanıcı girdisinde ise kısa alias 65 puanda kalır (logo gizli).
                        if (inputSource == MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION && isExactFullMatch) {
                            MerchantAliasType.EXACT
                        } else {
                            MerchantAliasType.SHORT
                        }
                    }
                    isExactFullMatch -> MerchantAliasType.EXACT
                    else -> alias.type
                }
            val confidence = confidenceFor(effectiveType)
            Candidate(
                merchant = merchant,
                alias = normalizedAlias,
                confidence = confidence,
                source = sourceFor(effectiveType),
                priority = alias.priority,
            )
        }
    }

    private fun confidenceFor(type: MerchantAliasType): Int =
        when (type) {
            MerchantAliasType.USER_VERIFIED -> 100
            MerchantAliasType.EXACT -> 95
            MerchantAliasType.LEGAL_NAME -> 92
            MerchantAliasType.BRAND_AND_SERVICE -> 90
            MerchantAliasType.BRANCH_OR_POS_PATTERN -> 86
            MerchantAliasType.STRONG_BRAND -> 80
            MerchantAliasType.SHORT -> 65
        }

    private fun sourceFor(type: MerchantAliasType): MerchantMatchSource =
        when (type) {
            MerchantAliasType.USER_VERIFIED -> MerchantMatchSource.USER_VERIFIED
            MerchantAliasType.EXACT -> MerchantMatchSource.EXACT_ALIAS
            MerchantAliasType.LEGAL_NAME -> MerchantMatchSource.LEGAL_NAME
            MerchantAliasType.BRAND_AND_SERVICE -> MerchantMatchSource.BRAND_AND_SERVICE
            MerchantAliasType.BRANCH_OR_POS_PATTERN -> MerchantMatchSource.BRANCH_OR_POS_PATTERN
            MerchantAliasType.STRONG_BRAND -> MerchantMatchSource.STRONG_BRAND
            MerchantAliasType.SHORT -> MerchantMatchSource.SHORT_ALIAS
        }

    private fun verifiedResult(
        merchantId: String?,
        source: MerchantMatchSource,
        confidence: Int,
        request: MerchantRecognitionRequest,
        normalized: String,
        classification: TransactionClassification,
    ): MerchantRecognitionResult? =
        merchantId
            ?.let { id -> catalog.firstOrNull { it.id == id } }
            ?.let { merchant ->
                result(
                    request = request,
                    normalized = normalized,
                    classification = classification,
                    merchant = merchant,
                    confidence = confidence,
                    source = source,
                    status = MerchantMatchStatus.MATCHED,
                )
            }

    private fun result(
        request: MerchantRecognitionRequest,
        normalized: String,
        classification: TransactionClassification,
        merchant: Merchant? = null,
        confidence: Int = 0,
        source: MerchantMatchSource? = null,
        status: MerchantMatchStatus = MerchantMatchStatus.NO_MATCH,
    ) = MerchantRecognitionResult(
        rawDescription = request.rawDescription,
        inputSource = request.inputSource,
        normalizedDescription = normalized,
        transactionClassification = classification,
        merchant = merchant,
        confidence = confidence,
        source = source,
        status = status,
    )

    private fun containsPhrase(
        description: String,
        phrase: String,
    ): Boolean = phrase.isNotEmpty() && " $description ".contains(" $phrase ")

    private companion object {
        const val SHORT_ALIAS_MAX_LENGTH = 4
        const val MIN_DECISIVE_SCORE_GAP = 10
        val candidateComparator =
            compareBy<Candidate> { it.confidence }
                .thenBy { it.priority }
                .thenBy { it.alias.length }
    }
}

object StarterMerchantCatalog {
    const val CATALOG_VERSION: Int = 26
    val merchants: List<Merchant> =
        listOf(
            // Dijital abonelik
            merchant(
                "netflix",
                "Netflix",
                "subscriptions",
                alias("NETFLIX"),
                verifiedLogoKey = "netflix",
            ),
            merchant(
                "spotify",
                "Spotify",
                "subscriptions",
                alias("SPOTIFY"),
                verifiedLogoKey = "spotify",
            ),
            merchant(
                "youtube_premium",
                "YouTube Premium",
                "subscriptions",
                alias("YOUTUBE PREMIUM", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("YOUTUBE MUSIC", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("GOOGLE YOUTUBE", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "youtube",
            ),
            merchant(
                "amazon_prime",
                "Amazon Prime",
                "subscriptions",
                alias("AMAZON PRIME", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("PRIME VIDEO", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("AMAZON PRIME VIDEO", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "amazon",
            ),
            merchant(
                "disney_plus",
                "Disney+",
                "subscriptions",
                alias("DISNEY PLUS", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "disney_plus",
            ),
            merchant(
                "max",
                "HBO Max",
                "subscriptions",
                alias("HBO MAX", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("MAX", MerchantAliasType.SHORT, priority = 20),
                alias("MAX HBO", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("BLUTV", priority = 10),
                alias("BLU TV", priority = 10),
                verifiedLogoKey = "max",
            ),
            merchant(
                "exxen",
                "Exxen",
                "subscriptions",
                alias("EXXEN"),
                verifiedLogoKey = "exxen",
            ),
            merchant(
                "gain",
                "Gain",
                "subscriptions",
                alias("GAIN", MerchantAliasType.SHORT),
                alias("GAIN MEDYA", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "gain",
            ),
            merchant(
                "mubi",
                "MUBI",
                "subscriptions",
                alias("MUBI", MerchantAliasType.SHORT),
                verifiedLogoKey = "mubi",
            ),
            merchant(
                "tod",
                "TOD",
                "subscriptions",
                alias("TOD", MerchantAliasType.SHORT),
                alias("TOD TV", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                alias("BEIN CONNECT", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                alias("BEINCONNECT", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "tod",
            ),
            merchant(
                "tabii",
                "tabii",
                "subscriptions",
                alias("TABII"),
                alias("TRT TABII", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "tabii",
            ),
            merchant(
                "apple_services",
                "Apple Services",
                "subscriptions",
                alias("APPLE COM BILL", MerchantAliasType.BRAND_AND_SERVICE),
                alias("APPLE SERVICES", MerchantAliasType.BRAND_AND_SERVICE),
                alias("ITUNES COM BILL", MerchantAliasType.BRAND_AND_SERVICE),
                verifiedLogoKey = "apple_services",
            ),
            merchant(
                "google_one",
                "Google One",
                "subscriptions",
                alias("GOOGLE ONE", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("GOOGLE STORAGE", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "google",
            ),
            merchant(
                "microsoft_365",
                "Microsoft 365",
                "subscriptions",
                alias("MICROSOFT 365", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("MSFT 365", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                alias("MICROSOFT OFFICE", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "microsoft",
            ),
            merchant(
                "adobe",
                "Adobe",
                "subscriptions",
                alias("ADOBE"),
                alias("ADOBE SYSTEMS", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "adobe",
            ),
            merchant(
                "canva",
                "Canva",
                "subscriptions",
                alias("CANVA"),
                alias("CANVA PRO", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "canva",
            ),
            merchant(
                "playstation_plus",
                "PlayStation Plus",
                "subscriptions",
                alias("PLAYSTATION PLUS", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("PS PLUS", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("PLAYSTATION NETWORK", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "playstation",
            ),
            merchant(
                "xbox_game_pass",
                "Xbox Game Pass",
                "subscriptions",
                alias("XBOX GAME PASS", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("GAME PASS", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                alias("XBOX PASS", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "xbox",
            ),
            merchant(
                "s_sport_plus",
                "S Sport Plus",
                "subscriptions",
                alias("S SPORT PLUS", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("SSPORT PLUS", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "s_sport_plus",
            ),
            merchant(
                "tv_plus",
                "TV+",
                "subscriptions",
                alias("TV PLUS", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("TURKCELL TV PLUS", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("TVPLUS", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "tv_plus",
            ),
            merchant(
                "tivibu",
                "Tivibu",
                "subscriptions",
                alias("TIVIBU"),
                alias("TIVIBU GO", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                alias("TTNET TIVIBU", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("TURK TELEKOM TIVIBU", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "tivibu",
            ),
            merchant(
                "d_smart_go",
                "D-Smart GO",
                "subscriptions",
                alias("D SMART GO", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("DSMART GO", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "d_smart_go",
            ),
            merchant(
                "deezer",
                "Deezer",
                "subscriptions",
                alias("DEEZER"),
                verifiedLogoKey = "deezer",
            ),
            merchant(
                "fizy",
                "Fizy",
                "subscriptions",
                alias("FIZY"),
                alias("TURKCELL FIZY", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "fizy",
            ),
            merchant(
                "muud",
                "Muud",
                "subscriptions",
                alias("MUUD"),
                alias("TURK TELEKOM MUUD", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "muud",
            ),
            merchant(
                "storytel",
                "Storytel",
                "subscriptions",
                alias("STORYTEL"),
                verifiedLogoKey = "storytel",
            ),
            merchant(
                "audible",
                "Audible",
                "subscriptions",
                alias("AUDIBLE"),
                verifiedLogoKey = "audible",
            ),
            merchant(
                "dropbox",
                "Dropbox",
                "subscriptions",
                alias("DROPBOX"),
                verifiedLogoKey = "dropbox",
            ),
            merchant(
                "linkedin_premium",
                "LinkedIn Premium",
                "subscriptions",
                alias("LINKEDIN PREMIUM", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "linkedin",
            ),
            merchant(
                "nintendo_switch_online",
                "Nintendo Switch Online",
                "subscriptions",
                alias("NINTENDO SWITCH ONLINE", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "nintendo",
            ),
            merchant(
                "ea_play",
                "EA Play",
                "subscriptions",
                alias("EA PLAY", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "ea",
            ),
            merchant(
                "hepsiburada_premium",
                "Hepsiburada Premium",
                "subscriptions",
                alias("HEPSIBURADA PREMIUM", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("HB PREMIUM", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "hepsiburada",
            ),
            merchant(
                "money_gold",
                "Money Gold",
                "subscriptions",
                alias("MONEY GOLD", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "migros",
            ),
            merchant(
                "ypro",
                "Ypro",
                "subscriptions",
                alias("YPRO"),
                alias("YEMEKSEPETI YPRO", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "yemeksepeti",
            ),
            // Alışveriş
            merchant(
                "trendyol",
                "Trendyol",
                "shopping",
                alias("TRENDYOL"),
                alias("DSM GRUP", MerchantAliasType.LEGAL_NAME),
                negative = listOf("TRENDYOL YEMEK", "TRENDYOLYEMEK"),
                verifiedLogoKey = "trendyol",
            ),
            merchant(
                "hepsiburada",
                "Hepsiburada",
                "shopping",
                alias("HEPSIBURADA"),
                alias("D-MARKET", MerchantAliasType.LEGAL_NAME),
                negative = listOf("HEPSIBURADA PREMIUM", "HB PREMIUM"),
                verifiedLogoKey = "hepsiburada",
            ),
            merchant(
                "amazon",
                "Amazon",
                "shopping",
                alias("AMAZON"),
                alias("AMAZON COM TR"),
                alias("AMAZON TURKIYE"),
                negative = listOf("AMAZON PRIME", "PRIME VIDEO"),
                verifiedLogoKey = "amazon",
            ),
            merchant(
                "n11",
                "n11",
                "shopping",
                alias("N11", MerchantAliasType.SHORT),
                alias("DOGUS PLANET", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "n11",
            ),
            merchant(
                "ciceksepeti",
                "ÇiçekSepeti",
                "shopping",
                alias("CICEKSEPETI"),
                alias("CICEK SEPETI"),
                verifiedLogoKey = "ciceksepeti",
            ),
            merchant(
                "pazarama",
                "Pazarama",
                "shopping",
                alias("PAZARAMA"),
                alias("TOPKAPI DANISMANLIK", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "pazarama",
            ),
            merchant(
                "boyner",
                "Boyner",
                "shopping",
                alias("BOYNER"),
                alias("BOYNER BUYUK MAGAZACILIK", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "boyner",
            ),
            merchant(
                "lc_waikiki",
                "LC Waikiki",
                "shopping",
                alias("LC WAIKIKI"),
                alias("LCWAIKIKI"),
                alias("LCW", MerchantAliasType.SHORT),
                verifiedLogoKey = "lc_waikiki",
            ),
            merchant(
                "defacto",
                "DeFacto",
                "shopping",
                alias("DEFACTO"),
                alias("DE FACTO"),
                verifiedLogoKey = "defacto",
            ),
            merchant(
                "teknosa",
                "Teknosa",
                "shopping",
                alias("TEKNOSA"),
                alias("TEKNOSA IC VE DIS TICARET", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "teknosa",
            ),
            merchant(
                "mediamarkt",
                "MediaMarkt",
                "shopping",
                alias("MEDIAMARKT"),
                alias("MEDIA MARKT"),
                verifiedLogoKey = "mediamarkt",
            ),
            merchant(
                "flo",
                "FLO",
                "shopping",
                alias("FLO", MerchantAliasType.SHORT),
                alias("FLO MAGAZACILIK", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "flo",
            ),
            merchant(
                "koton",
                "Koton",
                "shopping",
                alias("KOTON"),
                alias("KOTON MAGAZACILIK", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "koton",
            ),
            merchant(
                "mavi",
                "Mavi",
                "shopping",
                alias("MAVI GIYIM", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                alias("MAVI JEANS", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "mavi",
            ),
            merchant(
                "zara",
                "Zara",
                "shopping",
                alias("ZARA", MerchantAliasType.SHORT),
                alias("ZARA GIYIM", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "zara",
            ),
            merchant(
                "hm",
                "H&M",
                "shopping",
                alias("H M MAGAZACILIK", MerchantAliasType.LEGAL_NAME),
                alias("H AND M", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                alias("HENNES MAURITZ", MerchantAliasType.LEGAL_NAME),
                alias("H M GIYIM", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "hm",
            ),
            merchant(
                "pull_and_bear",
                "Pull&Bear",
                "shopping",
                alias("PULL BEAR"),
                alias("PULL AND BEAR", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                alias("PULLBEAR"),
                verifiedLogoKey = "pull_and_bear",
            ),
            merchant(
                "bershka",
                "Bershka",
                "shopping",
                alias("BERSHKA"),
                alias("BERSHKA TEKSTIL", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "bershka",
            ),
            merchant(
                "stradivarius",
                "Stradivarius",
                "shopping",
                alias("STRADIVARIUS"),
                verifiedLogoKey = "stradivarius",
            ),
            merchant(
                "beymen",
                "Beymen",
                "shopping",
                alias("BEYMEN"),
                alias("BEYMEN MAGAZACILIK", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "beymen",
            ),
            merchant(
                "vakko",
                "Vakko",
                "shopping",
                alias("VAKKO"),
                alias("VAKKO TEKSTIL", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "vakko",
            ),
            merchant(
                "ikea",
                "IKEA",
                "shopping",
                alias("IKEA", MerchantAliasType.SHORT),
                alias("IKEA MOBILYA", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "ikea",
            ),
            merchant(
                "koctas",
                "Koçtaş",
                "shopping",
                alias("KOCTAS"),
                alias("KOCTAS YAPI MARKET", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "koctas",
            ),
            merchant(
                "bauhaus",
                "Bauhaus",
                "shopping",
                alias("BAUHAUS"),
                alias("BAUHAUS YAPI MARKET", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "bauhaus",
            ),
            merchant(
                "vatan_bilgisayar",
                "Vatan Bilgisayar",
                "shopping",
                alias("VATAN BILGISAYAR"),
                alias("VATAN BILG", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "vatan_bilgisayar",
            ),
            merchant(
                "ebebek",
                "ebebek",
                "shopping",
                alias("EBEBEK"),
                alias("E BEBEK"),
                alias("EBEBEK MAGAZACILIK", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "ebebek",
            ),
            // Market ve hızlı teslimat
            merchant(
                "migros",
                "Migros",
                "groceries",
                alias("MIGROS"),
                alias("MIGROS TICARET", MerchantAliasType.LEGAL_NAME),
                negative = listOf("MIGROS YEMEK", "MIGROS SANAL MARKET", "MIGROS HEMEN", "MONEY GOLD"),
                verifiedLogoKey = "migros",
            ),
            merchant(
                "migros_sanal_market",
                "Migros Sanal Market",
                "groceries",
                alias("MIGROS SANAL MARKET", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
            ),
            merchant(
                "macrocenter",
                "Macrocenter",
                "groceries",
                alias("MACROCENTER"),
                alias("MACRO CENTER"),
                verifiedLogoKey = "macrocenter",
            ),
            merchant(
                "carrefoursa",
                "CarrefourSA",
                "groceries",
                alias("CARREFOURSA"),
                alias("CARREFOUR"),
                verifiedLogoKey = "carrefoursa",
            ),
            merchant(
                "a101",
                "A101",
                "groceries",
                alias("A101", MerchantAliasType.SHORT),
                alias("YENI MAGAZACILIK", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "a101",
            ),
            merchant(
                "bim",
                "BİM",
                "groceries",
                alias("BIM", MerchantAliasType.SHORT),
                alias("BIM BIRLESIK MAGAZALAR", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "bim",
            ),
            merchant(
                "sok",
                "ŞOK",
                "groceries",
                alias("SOK", MerchantAliasType.SHORT),
                alias("SOK MARKETLER", MerchantAliasType.LEGAL_NAME),
                alias("SOK MARKET", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "sok",
            ),
            merchant(
                "file_market",
                "File Market",
                "groceries",
                alias("FILE MARKET", MerchantAliasType.BRAND_AND_SERVICE),
                alias("FILE GIDA", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "file_market",
            ),
            merchant(
                "tarim_kredi",
                "Tarım Kredi",
                "groceries",
                alias("TARIM KREDI"),
                alias("TARIM KREDI KOOPERATIF", MerchantAliasType.LEGAL_NAME),
                alias("TARIM KREDI MARKET", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "tarim_kredi",
            ),
            merchant(
                "getir",
                "Getir",
                "groceries",
                alias("GETIR"),
                alias("GETIR PERAKENDE", MerchantAliasType.LEGAL_NAME),
                negative = listOf("GETIRYEMEK", "GETIR YEMEK"),
                verifiedLogoKey = "getir",
            ),
            merchant(
                "yemeksepeti_market",
                "Yemeksepeti Market",
                "groceries",
                alias("YEMEKSEPETI MARKET", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("YEMEK SEPETI MARKET", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("BANABI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
            ),
            merchant(
                "bizim_toptan",
                "Bizim Toptan",
                "groceries",
                alias("BIZIM TOPTAN"),
                alias("BIZIM TOPTAN SATIS MAGAZALARI", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "bizim_toptan",
            ),
            merchant(
                "metro_turkiye",
                "Metro Türkiye",
                "groceries",
                alias("METRO GROSSMARKET"),
                alias("METRO TURKIYE"),
                verifiedLogoKey = "metro_turkiye",
            ),
            merchant(
                "istegelsin",
                "istegelsin",
                "groceries",
                alias("ISTEGELSIN"),
            ),
            merchant(
                "onur_market",
                "Onur Market",
                "groceries",
                alias("ONUR MARKET"),
            ),
            merchant(
                "happy_center",
                "Happy Center",
                "groceries",
                alias("HAPPY CENTER"),
            ),
            merchant(
                "hakmar",
                "Hakmar",
                "groceries",
                alias("HAKMAR"),
                alias("HAKMAR EXPRESS", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
            ),
            merchant(
                "kim_market",
                "KİM Market",
                "groceries",
                alias("KIM MARKET"),
            ),
            merchant(
                "cagri_market",
                "Çağrı Market",
                "groceries",
                alias("CAGRI MARKET"),
            ),
            merchant(
                "ozkuruslar",
                "Özkuruşlar",
                "groceries",
                alias("OZKURUSLAR"),
                alias("OZKURUSLAR MARKET", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
            ),
            merchant(
                "sec_market",
                "Seç Market",
                "groceries",
                alias("SEC MARKET"),
            ),
            // Yemek
            merchant(
                "yemeksepeti",
                "Yemeksepeti",
                "food_dining",
                alias("YEMEKSEPETI"),
                alias("YEMEK SEPETI"),
                negative = listOf("YEMEKSEPETI MARKET", "YEMEK SEPETI MARKET", "BANABI", "YEMEKSEPETI YPRO"),
                verifiedLogoKey = "yemeksepeti",
            ),
            merchant(
                "trendyol_yemek",
                "Trendyol Yemek",
                "food_dining",
                alias("TRENDYOL YEMEK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("TRENDYOLYEMEK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
            ),
            merchant(
                "getiryemek",
                "GetirYemek",
                "food_dining",
                alias("GETIRYEMEK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("GETIR YEMEK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
            ),
            merchant(
                "migros_yemek",
                "Migros Yemek",
                "food_dining",
                alias("MIGROS YEMEK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
            ),
            merchant(
                "mcdonalds",
                "McDonald's",
                "food_dining",
                alias("MCDONALDS"),
                alias("MCDONALD S"),
                verifiedLogoKey = "mcdonalds",
            ),
            merchant(
                "burger_king",
                "Burger King",
                "food_dining",
                alias("BURGER KING"),
                verifiedLogoKey = "burger_king",
            ),
            merchant(
                "kfc",
                "KFC",
                "food_dining",
                alias("KFC", MerchantAliasType.SHORT),
                verifiedLogoKey = "kfc",
            ),
            merchant(
                "popeyes",
                "Popeyes",
                "food_dining",
                alias("POPEYES"),
                verifiedLogoKey = "popeyes",
            ),
            merchant(
                "dominos",
                "Domino's Pizza",
                "food_dining",
                alias("DOMINOS"),
                alias("DOMINO S"),
                alias("DOMINOS PIZZA", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "dominos",
            ),
            merchant(
                "little_caesars",
                "Little Caesars",
                "food_dining",
                alias("LITTLE CAESARS"),
                verifiedLogoKey = "little_caesars",
            ),
            merchant(
                "pizza_lazza",
                "PizzaLazza",
                "food_dining",
                alias("PIZZA LAZZA"),
                alias("PIZZALAZZA"),
                verifiedLogoKey = "pizza_lazza",
            ),
            merchant(
                "tavuk_dunyasi",
                "Tavuk Dünyası",
                "food_dining",
                alias("TAVUK DUNYASI"),
                verifiedLogoKey = "tavuk_dunyasi",
            ),
            merchant(
                "baydoner",
                "Baydöner",
                "food_dining",
                alias("BAYDONER"),
                alias("BAY DONER"),
                verifiedLogoKey = "baydoner",
            ),
            merchant(
                "kofteci_yusuf",
                "Köfteci Yusuf",
                "food_dining",
                alias("KOFTECI YUSUF"),
                verifiedLogoKey = "kofteci_yusuf",
            ),
            merchant(
                "kahve_dunyasi",
                "Kahve Dünyası",
                "food_dining",
                alias("KAHVE DUNYASI"),
                verifiedLogoKey = "kahve_dunyasi",
            ),
            merchant(
                "mado",
                "Mado",
                "food_dining",
                alias("MADO", MerchantAliasType.SHORT),
                verifiedLogoKey = "mado",
            ),
            merchant(
                "espressolab",
                "Espressolab",
                "food_dining",
                alias("ESPRESSOLAB"),
                alias("ESPRESSO LAB"),
                verifiedLogoKey = "espressolab",
            ),
            merchant(
                "simit_sarayi",
                "Simit Sarayı",
                "food_dining",
                alias("SIMIT SARAYI"),
                verifiedLogoKey = "simit_sarayi",
            ),
            merchant(
                "bigchefs",
                "BigChefs",
                "food_dining",
                alias("BIGCHEFS"),
                alias("BIG CHEFS"),
                verifiedLogoKey = "bigchefs",
            ),
            // Telekom
            merchant(
                "turkcell",
                "Turkcell",
                "utilities",
                alias("TURKCELL"),
                alias("TURKCELL ILETISIM", MerchantAliasType.LEGAL_NAME),
                negative =
                    listOf(
                        "SUPERONLINE",
                        "TURKCELL SUPERONLINE",
                        "TURKCELL TV PLUS",
                        "TV PLUS",
                        "TURKCELL FIZY",
                    ),
                verifiedLogoKey = "turkcell",
            ),
            merchant(
                "vodafone",
                "Vodafone",
                "utilities",
                alias("VODAFONE"),
                alias("VODAFONE TELEKOMUNIKASYON", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "vodafone",
            ),
            merchant(
                "turk_telekom",
                "Türk Telekom",
                "utilities",
                alias("TURK TELEKOM"),
                alias("TURK TELEKOMUNIKASYON", MerchantAliasType.LEGAL_NAME),
                alias("AVEA"),
                negative = listOf("TURK TELEKOM MUUD", "TURK TELEKOM TIVIBU"),
            ),
            merchant(
                "superonline",
                "Superonline",
                "utilities",
                alias("SUPERONLINE", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("TURKCELL SUPERONLINE", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
            ),
            // Akaryakıt
            merchant(
                "shell",
                "Shell",
                "fuel",
                alias("SHELL"),
                alias("SHELL TURCAS", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "shell",
            ),
            merchant(
                "opet",
                "OPET",
                "fuel",
                alias("OPET", MerchantAliasType.SHORT),
                alias("OPET PETROLCULUK", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "opet",
            ),
            merchant(
                "petrol_ofisi",
                "Petrol Ofisi",
                "fuel",
                alias("PETROL OFISI"),
                alias("POAS", MerchantAliasType.SHORT),
                verifiedLogoKey = "petrol_ofisi",
            ),
            merchant(
                "totalenergies",
                "TotalEnergies",
                "fuel",
                alias("TOTALENERGIES"),
                alias("TOTAL ENERGIES"),
            ),
            merchant(
                "aytemiz",
                "Aytemiz",
                "fuel",
                alias("AYTEMIZ"),
                alias("AYTEMIZ PETROL", MerchantAliasType.LEGAL_NAME),
            ),
            // Ulaşım
            merchant(
                "thy",
                "THY",
                "travel",
                alias("TURK HAVA YOLLARI"),
                alias("TURKISH AIRLINES"),
                alias("THY", MerchantAliasType.SHORT),
                verifiedLogoKey = "thy",
            ),
            merchant(
                "pegasus",
                "Pegasus",
                "travel",
                alias("PEGASUS"),
                alias("PEGASUS HAVA YOLLARI", MerchantAliasType.LEGAL_NAME),
                alias("FLYPGS"),
                verifiedLogoKey = "pegasus",
            ),
            merchant(
                "sunexpress",
                "SunExpress",
                "travel",
                alias("SUNEXPRESS"),
                alias("SUN EXPRESS"),
                verifiedLogoKey = "sunexpress",
            ),
            merchant(
                "tcdd",
                "TCDD",
                "transportation",
                alias("TCDD", MerchantAliasType.SHORT),
                alias("TCDD TASIMACILIK", MerchantAliasType.LEGAL_NAME),
                verifiedLogoKey = "tcdd",
            ),
            merchant(
                "uber",
                "Uber",
                "transportation",
                alias("UBER", MerchantAliasType.SHORT),
                verifiedLogoKey = "uber",
            ),
            merchant(
                "bitaksi",
                "BiTaksi",
                "transportation",
                alias("BITAKSI"),
                alias("BI TAKSI"),
                verifiedLogoKey = "bitaksi",
            ),
            merchant(
                "marti",
                "Martı",
                "transportation",
                alias("MARTI", MerchantAliasType.SHORT),
                alias("MARTI ILERI TEKNOLOJI", MerchantAliasType.LEGAL_NAME),
                alias("MARTI TAG", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                alias("MARTI SCOOTER", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "marti",
            ),
            // Kafe
            merchant(
                "starbucks",
                "Starbucks",
                "food_dining",
                alias("STARBUCKS"),
                alias("SBUX", MerchantAliasType.SHORT),
                verifiedLogoKey = "starbucks",
            ),
            // Kişisel Bakım ve Kozmetik
            merchant(
                "gratis",
                "Gratis",
                "personal_care",
                alias("GRATIS"),
                verifiedLogoKey = "gratis",
            ),
            merchant(
                "watsons",
                "Watsons",
                "personal_care",
                alias("WATSONS"),
                verifiedLogoKey = "watsons",
            ),
            merchant(
                "rossmann",
                "Rossmann",
                "personal_care",
                alias("ROSSMANN"),
                verifiedLogoKey = "rossmann",
            ),
            merchant(
                "sephora",
                "Sephora",
                "personal_care",
                alias("SEPHORA"),
                verifiedLogoKey = "sephora",
            ),
            merchant(
                "eve_shop",
                "EVE",
                "personal_care",
                alias("EVE SHOP"),
                verifiedLogoKey = "eve_shop",
            ),
            merchant(
                "flormar",
                "Flormar",
                "personal_care",
                alias("FLORMAR"),
                verifiedLogoKey = "flormar",
            ),
            merchant(
                "golden_rose",
                "Golden Rose",
                "personal_care",
                alias("GOLDEN ROSE"),
                verifiedLogoKey = "golden_rose",
            ),
            merchant(
                "yves_rocher",
                "Yves Rocher",
                "personal_care",
                alias("YVES ROCHER"),
                verifiedLogoKey = "yves_rocher",
            ),
            merchant(
                "the_body_shop",
                "The Body Shop",
                "personal_care",
                alias("THE BODY SHOP", MerchantAliasType.BRAND_AND_SERVICE),
                verifiedLogoKey = "the_body_shop",
            ),
            merchant(
                "kiko_milano",
                "KIKO Milano",
                "personal_care",
                alias("KIKO MILANO"),
                verifiedLogoKey = "kiko_milano",
            ),
            merchant(
                "mac_cosmetics",
                "MAC Cosmetics",
                "personal_care",
                alias("MAC COSMETICS"),
                verifiedLogoKey = "mac_cosmetics",
            ),
            merchant(
                "clinique",
                "Clinique",
                "personal_care",
                alias("CLINIQUE"),
                verifiedLogoKey = "clinique",
            ),
            merchant(
                "avon",
                "Avon",
                "personal_care",
                alias("AVON", MerchantAliasType.SHORT),
                alias("AVON KOZMETIK", MerchantAliasType.BRAND_AND_SERVICE),
                verifiedLogoKey = "avon",
            ),
            merchant(
                "oriflame",
                "Oriflame",
                "personal_care",
                alias("ORIFLAME"),
                verifiedLogoKey = "oriflame",
            ),
            merchant(
                "farmasi",
                "Farmasi",
                "personal_care",
                alias("FARMASI"),
                verifiedLogoKey = "farmasi",
            ),
            // Fatura & Altyapı Sağlayıcıları (Utilities)
            merchant(
                "turknet",
                "TurkNet",
                "utilities",
                alias("TURKNET"),
            ),
            merchant(
                "millenicom",
                "Millenicom",
                "utilities",
                alias("MILLENICOM"),
            ),
            merchant(
                "netspeed",
                "Netspeed",
                "utilities",
                alias("NETSPEED"),
            ),
            merchant(
                "turksat_kablo",
                "Türksat Kablo",
                "utilities",
                alias("TURKSAT KABLO"),
                alias("KABLONET"),
            ),
            merchant(
                "comnet",
                "Comnet",
                "utilities",
                alias("COMNET"),
            ),
            merchant(
                "enerjisa",
                "Enerjisa",
                "utilities",
                alias("ENERJISA"),
            ),
            merchant(
                "ck_enerji",
                "CK Enerji",
                "utilities",
                alias("CK ENERJI"),
            ),
            merchant(
                "gediz_elektrik",
                "Gediz Elektrik",
                "utilities",
                alias("GEDIZ ELEKTRIK"),
            ),
            merchant(
                "aydem_enerji",
                "Aydem Enerji",
                "utilities",
                alias("AYDEM ENERJI"),
            ),
            merchant(
                "iski",
                "İSKİ",
                "utilities",
                alias("ISKI SU"),
                alias("ISTANBUL SU VE KANALIZASYON"),
            ),
            merchant(
                "aski_ankara",
                "ASKİ Ankara",
                "utilities",
                alias("ASKI SU"),
                alias("ANKARA SU VE KANALIZASYON"),
            ),
            merchant(
                "izsu",
                "İZSU",
                "utilities",
                alias("IZSU SU"),
                alias("IZMIR SU VE KANALIZASYON"),
            ),
            merchant(
                "igdas",
                "İGDAŞ",
                "utilities",
                alias("IGDAS DOGALGAZ"),
                alias("ISTANBUL GAZ DAGITIM"),
            ),
            merchant(
                "baskentgaz",
                "Başkentgaz",
                "utilities",
                alias("BASKENTGAZ"),
            ),
            merchant(
                "enerya",
                "Enerya",
                "utilities",
                alias("ENERYA DOGALGAZ"),
            ),
            // Ulaşım (Transportation)
            merchant(
                "istanbulkart",
                "İstanbulkart",
                "transportation",
                alias("ISTANBULKART", priority = 20),
                alias("BELBIM", MerchantAliasType.LEGAL_NAME, priority = 20),
                alias("BELBIM ELEKTRONIK PARA", MerchantAliasType.LEGAL_NAME, priority = 10),
                alias("ISTANBUL KART", priority = 10),
                verifiedLogoKey = "istanbulkart",
            ),
            merchant(
                "ispark",
                "İSPARK",
                "transportation",
                alias("ISPARK", priority = 20),
                alias("ISTANBUL OTOPARK", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                alias("ISPARK OTOPARK", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "ispark",
            ),
            merchant(
                "tiktak",
                "TikTak",
                "transportation",
                alias("TIKTAK", MerchantAliasType.SHORT),
                alias("TIKTAK ARAC", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("TIKTAK KIRALAMA", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
            ),
            merchant(
                "getir_arac",
                "GetirAraç",
                "transportation",
                alias("GETIRARAC", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("GETIR ARAC", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("MOOV BY GETIR", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("MOOV", MerchantAliasType.SHORT, priority = 10),
            ),
            merchant(
                "kamil_koc",
                "Kâmil Koç",
                "transportation",
                alias("KAMIL KOC", priority = 20),
                alias("KAMILKOC", priority = 20),
                alias("KAMIL KOC OTOBUS", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
            ),
            merchant(
                "metro_turizm",
                "Metro Turizm",
                "transportation",
                alias("METRO TURIZM", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("METRO TURIZM TASIMACILIK", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "metro_turizm",
            ),
            merchant(
                "pamukkale_turizm",
                "Pamukkale Turizm",
                "transportation",
                alias("PAMUKKALE TURIZM", priority = 20),
                alias("PAMUKKALE SEYAHAT", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                alias("PAMUKKALE OTOBUS", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "pamukkale_turizm",
            ),
            merchant(
                "varan_turizm",
                "Varan Turizm",
                "transportation",
                alias("VARAN TURIZM", priority = 20),
                alias("VARAN SEYAHAT", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "varan_turizm",
            ),
            merchant(
                "ido",
                "İDO",
                "transportation",
                alias("IDO DENIZ OTOBUSLERI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("ISTANBUL DENIZ OTOBUSLERI", MerchantAliasType.LEGAL_NAME, priority = 20),
                alias("IDO", MerchantAliasType.SHORT, priority = 10),
                verifiedLogoKey = "ido",
            ),
            merchant(
                "budo",
                "BUDO",
                "transportation",
                alias("BURSA DENIZ OTOBUSLERI", MerchantAliasType.LEGAL_NAME, priority = 20),
                alias("BUDO DENIZ OTOBUSLERI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("BUDO", MerchantAliasType.SHORT, priority = 10),
            ),
            // Seyahat (Travel)
            merchant(
                "ajet",
                "AJet",
                "travel",
                alias("AJET HAVA TASIMACILIGI", MerchantAliasType.LEGAL_NAME, priority = 20),
                alias("ANADOLUJET", priority = 20),
                alias("ANADOLU JET", priority = 20),
                alias("AJET", MerchantAliasType.SHORT, priority = 10),
                verifiedLogoKey = "ajet",
            ),
            merchant(
                "corendon",
                "Corendon",
                "travel",
                alias("CORENDON", priority = 20),
                alias("CORENDON AIRLINES", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("CORENDON HAVA YOLLARI", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "corendon",
            ),
            merchant(
                "obilet",
                "obilet",
                "travel",
                alias("OBILET", priority = 20),
                alias("OBILET BILISIM", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "obilet",
            ),
            merchant(
                "enuygun",
                "Enuygun",
                "travel",
                alias("ENUYGUN", priority = 20),
                alias("WINGIE ENUYGUN", MerchantAliasType.LEGAL_NAME, priority = 10),
                alias("ENUYGUN COM", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "enuygun",
            ),
            merchant(
                "ets_tur",
                "ETS Tur",
                "travel",
                alias("ETSTUR", priority = 20),
                alias("ETS TUR", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("ERSOY TURISTIK SERVISLERI", MerchantAliasType.LEGAL_NAME, priority = 10),
                alias("ETS", MerchantAliasType.SHORT, priority = 10),
                verifiedLogoKey = "ets_tur",
            ),
            merchant(
                "jolly_tur",
                "Jolly Tur",
                "travel",
                alias("JOLLY TUR", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("JOLLY TURIZM", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("JOLLY", priority = 10),
                negative = listOf("JOKER"),
                verifiedLogoKey = "jolly_tur",
            ),
            merchant(
                "tatilbudur",
                "TatilBudur",
                "travel",
                alias("TATILBUDUR", priority = 20),
                alias("TATIL BUDUR", priority = 20),
                alias("TATILBUDUR SEYAHAT", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
            ),
            merchant(
                "setur",
                "Setur",
                "travel",
                alias("SETUR", priority = 20),
                alias("SETUR SERVIS TURISTIK", MerchantAliasType.LEGAL_NAME, priority = 10),
                alias("SETUR TURIZM", MerchantAliasType.BRAND_AND_SERVICE, priority = 10),
                verifiedLogoKey = "setur",
            ),
            merchant(
                "airbnb",
                "Airbnb",
                "travel",
                alias("AIRBNB", priority = 20),
                alias("AIRBNB IRELAND", MerchantAliasType.LEGAL_NAME, priority = 10),
                alias("AIRBNB PAYMENTS", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "airbnb",
            ),
            merchant(
                "booking",
                "Booking.com",
                "travel",
                alias("BOOKING COM", priority = 20),
                alias("BOOKINGCOM", priority = 20),
                alias("BOOKING BV", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "booking",
            ),
            // Eğlence
            merchant(
                "paribu_cineverse",
                "Paribu Cineverse",
                "entertainment",
                alias("PARIBU CINEVERSE", priority = 20),
                alias("CINEVERSE", priority = 20),
                alias("CGV MARS", MerchantAliasType.LEGAL_NAME, priority = 10),
                alias("CINEMAXIMUM", priority = 10),
                negative = listOf("PARIBU TEKNOLOJI"),
                verifiedLogoKey = "paribu_cineverse",
            ),
            merchant(
                "biletix",
                "Biletix",
                "entertainment",
                alias("BILETIX", priority = 20),
                alias("BILETIX BILET DAGITIM", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "biletix",
            ),
            merchant(
                "steam",
                "Steam",
                "entertainment",
                alias("STEAM GAMES", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("STEAMPOWERED COM", priority = 20),
                alias("VALVE SOFTWARE", MerchantAliasType.LEGAL_NAME, priority = 10),
                negative = listOf("STEAM CLEANING"),
                verifiedLogoKey = "steam",
            ),
            merchant(
                "biletinial",
                "Biletinial",
                "entertainment",
                alias("BILETINIAL", priority = 20),
                alias("BILETINIAL COM", priority = 20),
                verifiedLogoKey = "biletinial",
            ),
            merchant(
                "bubilet",
                "Bubilet",
                "entertainment",
                alias("BUBILET", priority = 20),
                alias("BUBILET BILET BASIM", MerchantAliasType.LEGAL_NAME, priority = 10),
                alias("BUBILET BILET DAGITIM", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "bubilet",
            ),
            merchant(
                "passo",
                "Passo",
                "entertainment",
                alias("PASSO", priority = 20),
                alias("PASSO DIJITAL HIZMETLER", MerchantAliasType.LEGAL_NAME, priority = 10),
                negative = listOf("PASSOLIG"),
                verifiedLogoKey = "passo",
            ),
            merchant(
                "mobilet",
                "Mobilet",
                "entertainment",
                alias("MOBILET", priority = 20),
                alias("EVENT MASTER", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "mobilet",
            ),
            merchant(
                "biletino",
                "Biletino",
                "entertainment",
                alias("BILETINO", priority = 20),
                verifiedLogoKey = "biletino",
            ),
            merchant(
                "bugece",
                "BUGECE",
                "entertainment",
                alias("BUGECE", priority = 20),
                alias("BUGECE CO", priority = 20),
                verifiedLogoKey = "bugece",
            ),
            merchant(
                "epic_games",
                "Epic Games",
                "entertainment",
                alias("EPIC GAMES", priority = 20),
                alias("EPICGAMES", priority = 20),
                negative = listOf("EPIC HOTEL"),
                verifiedLogoKey = "epic_games",
            ),
            merchant(
                "playstation_store",
                "PlayStation Store",
                "entertainment",
                alias("PLAYSTATION STORE", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("SONY PLAYSTATION STORE", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("PLAYSTATION PLUS"),
                verifiedLogoKey = "playstation",
            ),
            merchant(
                "xbox_store",
                "Xbox Store",
                "entertainment",
                alias("XBOX STORE", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("MICROSOFT XBOX STORE", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("XBOX GAME PASS"),
                verifiedLogoKey = "xbox",
            ),
            merchant(
                "riot_games",
                "Riot Games",
                "entertainment",
                alias("RIOT GAMES", priority = 20),
                alias("RIOTGAMES", priority = 20),
                verifiedLogoKey = "riot_games",
            ),
            merchant(
                "battle_net",
                "Battle.net",
                "entertainment",
                alias("BATTLE NET", priority = 20),
                alias("BATTLENET", priority = 20),
                alias("BLIZZARD ENTERTAINMENT", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "battle_net",
            ),
            merchant(
                "ubisoft_store",
                "Ubisoft Store",
                "entertainment",
                alias("UBISOFT STORE", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("UBISOFT CONNECT", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("UBISOFT", priority = 10),
                verifiedLogoKey = "ubisoft_store",
            ),
            merchant(
                "cinemarine",
                "Cinemarine",
                "entertainment",
                alias("CINEMARINE", priority = 20),
                verifiedLogoKey = "cinemarine",
            ),
            merchant(
                "vialand",
                "Vialand",
                "entertainment",
                alias("VIALAND", priority = 20),
                alias("ISFANBUL", priority = 10),
                verifiedLogoKey = "vialand",
            ),
            // Ev & Kira (Housing)
            merchant(
                "apsiyon",
                "Apsiyon",
                "housing",
                alias("APSIYON", priority = 20),
                alias("APSIYON BILISIM", priority = 20),
                alias("APSIYON AIDAT", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("APSIYON.COM", priority = 20),
                alias("APSIYON BILISIM SISTEMLERI SANAYI VE TICARET ANONIM SIRKETI", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "apsiyon",
            ),
            merchant(
                "yonetimcell",
                "Yönetimcell",
                "housing",
                alias("YONETIMCELL", priority = 20),
                alias("YONETIM CELL", priority = 20),
                alias("YONETIMCELL.COM", priority = 20),
                alias("YONETIMCELL YAZILIM BILISIM LIMITED SIRKETI", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "yonetimcell",
            ),
            merchant(
                "armut",
                "Armut",
                "housing",
                alias("ARMUT.COM", priority = 20),
                alias("ARMUT TEKNOLOJI", priority = 20),
                alias("ARMUT HIZMET", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("ARMUT TEMIZLIK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("ARMUT TADILAT", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("ARMUT TEKNOLOJI ANONIM SIRKETI", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "armut",
            ),
            merchant(
                "senyonet",
                "Senyonet",
                "housing",
                alias("SENYONET", priority = 20),
                alias("SEN YONET", priority = 20),
                alias("SENYONET.NET", priority = 20),
                alias("SENYONET BILISIM SISTEMLERI SANAYI VE TICARET ANONIM SIRKETI", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "senyonet",
            ),
            merchant(
                "mrusta",
                "Mr Usta",
                "housing",
                alias("MR USTA", priority = 20),
                alias("MRUSTA", priority = 20),
                alias("MRUSTA.COM.TR", priority = 20),
                alias("MR USTA ELEKTRONIK HIZMETLER VE TICARET ANONIM SIRKETI", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "mrusta",
            ),
            merchant(
                "pronet",
                "Pronet",
                "housing",
                alias("PRONET", priority = 20),
                alias("PRONET GUVENLIK", priority = 20),
                alias("PRONET ALARM", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("PRONET.COM.TR", priority = 20),
                alias("PRONET GUVENLIK HIZMETLERI ANONIM SIRKETI", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "pronet",
            ),
            merchant(
                "kale_alarm",
                "Kale Alarm",
                "housing",
                alias("KALE ALARM", priority = 20),
                alias("KALE GUVENLIK", priority = 20),
                alias("KALEALARM.COM.TR", priority = 20),
                alias("KALE GUVENLIK SISTEMLERI ANONIM SIRKETI", MerchantAliasType.LEGAL_NAME, priority = 10),
                verifiedLogoKey = "kale_alarm",
            ),
            // Sağlık (Healthcare)
            merchant(
                "acibadem",
                "Acıbadem",
                "healthcare",
                alias("ACIBADEM", priority = 20),
                alias("ACIBADEM HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("ACIBADEM SAGLIK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("ACIBADEM UNIVERSITESI", "ACIBADEM MAHALLESI", "ACIBADEM CAD"),
                verifiedLogoKey = "acibadem",
            ),
            merchant(
                "memorial",
                "Memorial",
                "healthcare",
                alias("MEMORIAL", priority = 20),
                alias("MEMORIAL HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("MEMORIAL SAGLIK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("MEMORIAL PARK", "MEMORIAL CUP"),
                verifiedLogoKey = "memorial",
            ),
            merchant(
                "medical_park",
                "Medical Park",
                "healthcare",
                alias("MEDICAL PARK", priority = 20),
                alias("MEDICALPARK", priority = 20),
                alias("VM MEDICAL PARK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("PARK MEDIKAL", "MEDICAL CIHAZLAR", "MERKEZ PARK OTOPARK"),
                verifiedLogoKey = "medical_park",
            ),
            merchant(
                "liv_hospital",
                "Liv Hospital",
                "healthcare",
                alias("LIV HOSPITAL", priority = 20),
                alias("LIV ULUS", MerchantAliasType.BRANCH_OR_POS_PATTERN, priority = 10),
                alias("LIV VADI", MerchantAliasType.BRANCH_OR_POS_PATTERN, priority = 10),
                alias("LIV ANKARA", MerchantAliasType.BRANCH_OR_POS_PATTERN, priority = 10),
                alias("MLP SAGLIK HIZMETLERI LIV HOSPITAL", MerchantAliasType.LEGAL_NAME, priority = 10),
                negative = listOf("LIV TV", "LIV YAYINCILIK"),
                verifiedLogoKey = "liv_hospital",
            ),
            merchant(
                "medicana",
                "Medicana",
                "healthcare",
                alias("MEDICANA", priority = 20),
                alias("MEDICANA HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("MEDICANA SAGLIK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("MEDIKAL", "MEDITERRANEAN"),
                verifiedLogoKey = "medicana",
            ),
            merchant(
                "florence_nightingale",
                "Florence Nightingale",
                "healthcare",
                alias("FLORENCE NIGHTINGALE", priority = 20),
                alias("FLORENCE HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("ISTANBUL FLORENCE NIGHTINGALE", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("FLORENCE TOUR", "FLORENCE HOTEL", "FLORENCE TRAVEL"),
                verifiedLogoKey = "florence_nightingale",
            ),
            merchant(
                "anadolu_saglik",
                "Anadolu Sağlık Merkezi",
                "healthcare",
                alias("ANADOLU SAGLIK MERKEZI", priority = 20),
                alias("ANADOLU SAGLIK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("ANADOLU UNIVERSITESI", "ANADOLU SIGORTA", "ANADOLU EFES", "ANADOLU ISUZU", "ANADOLU HASTANESI"),
                verifiedLogoKey = "anadolu_saglik",
            ),
            merchant(
                "amerikan_hastanesi",
                "Amerikan Hastanesi",
                "healthcare",
                alias("AMERIKAN HASTANESI", priority = 20),
                alias("VKV AMERIKAN HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("KOC AMERIKAN HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative =
                    listOf(
                        "AMERIKAN KULTUR",
                        "AMERIKAN DIL KURSU",
                        "AMERIKAN PAZARI",
                        "AMERIKAN KAPI",
                        "VET AMERIKAN",
                        "VETAMERIKAN",
                    ),
                verifiedLogoKey = "amerikan_hastanesi",
            ),
            merchant(
                "dunyagoz",
                "Dünyagöz",
                "healthcare",
                alias("DUNYAGOZ", priority = 20),
                alias("DUNYA GOZ", priority = 20),
                alias("DUNYAGOZ HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("DUNYA GAZETESI", "DUNYA DONER", "GOZ DOKTORU"),
                verifiedLogoKey = "dunyagoz",
            ),
            merchant(
                "medipol",
                "Medipol",
                "healthcare",
                alias("MEDIPOL HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("MEDIPOL SAGLIK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("MEDIPOL MEGA", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("MEDIPOL UNIVERSITE HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("MEDIPOL UNIVERSITESI HARC", "MEDIPOL UNIVERSITESI KAYIT", "MEDIPOL OGRENCI ISLERI"),
                verifiedLogoKey = "medipol",
            ),
            merchant(
                "biruni_hastanesi",
                "Biruni Hastanesi",
                "healthcare",
                alias("BIRUNI HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("BIRUNI UNIVERSITE HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("BIRUNI UNIVERSITESI TIP FAKULTESI HASTANESI", MerchantAliasType.LEGAL_NAME, priority = 10),
                negative = listOf("BIRUNI UNIVERSITESI HARC", "BIRUNI UNIVERSITESI KAYIT", "BIRUNI OGRENCI ISLERI"),
                verifiedLogoKey = "biruni_hastanesi",
            ),
            merchant(
                "guven_hastanesi",
                "Güven Hastanesi",
                "healthcare",
                alias("GUVEN HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("ANKARA GUVEN HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("GUVEN SAGLIK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("GUVEN TAKSI", "GUVEN SIGORTA", "GUVEN TEMIZLIK"),
                verifiedLogoKey = "guven_hastanesi",
            ),
            merchant(
                "lokman_hekim",
                "Lokman Hekim",
                "healthcare",
                alias("LOKMAN HEKIM", priority = 20),
                alias("LOKMAN HEKIM HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("LOKMAN HEKIM BAHARAT", "LOKMAN HEKIM AKTAR"),
                verifiedLogoKey = "lokman_hekim",
            ),
            merchant(
                "baskent_hastanesi",
                "Başkent Hastanesi",
                "healthcare",
                alias("BASKENT HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("BASKENT UNIVERSITESI HASTANESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("BASKENT GAZ", "BASKENT EDAS", "BASKENT UNIVERSITESI HARC", "BASKENT UNIVERSITESI KAYIT"),
                verifiedLogoKey = "baskent_hastanesi",
            ),
            merchant(
                "synevo",
                "Synevo",
                "healthcare",
                alias("SYNEVO", priority = 20),
                alias("SYNEVO LABORATUVAR", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("SYNEVO LAB", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "synevo",
            ),
            merchant(
                "duzen_laboratuvari",
                "Düzen Laboratuvarı",
                "healthcare",
                alias("DUZEN LABORATUVARI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("DUZEN LAB", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("DUZEN SAGLIK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative = listOf("EV DUZENI", "DUZEN KURULUM"),
                verifiedLogoKey = "duzen_laboratuvari",
            ),
            merchant(
                "dentgroup",
                "DentGroup",
                "healthcare",
                alias("DENTGROUP", priority = 20),
                alias("DENT GROUP", priority = 20),
                alias("DENTGROUP DIS", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "dentgroup",
            ),
            merchant(
                "hospitadent",
                "Hospitadent",
                "healthcare",
                alias("HOSPITADENT", priority = 20),
                alias("HOSPITA DENT", priority = 20),
                alias("HOSPITADENT DIS", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "hospitadent",
            ),
            merchant(
                "atasun_optik",
                "Atasun Optik",
                "shopping",
                alias("ATASUN OPTIK", priority = 20),
                alias("ATASUN", priority = 20),
                alias("ATASUN GOZLUK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "atasun_optik",
            ),
            // Eğitim / Education (H4-G2-J1)
            merchant(
                "udemy",
                "Udemy",
                "education",
                alias("UDEMY", priority = 21),
                alias("UDEMY COM", priority = 21),
                alias("UDEMY IRELAND", priority = 21),
                verifiedLogoKey = "udemy",
            ),
            merchant(
                "coursera",
                "Coursera",
                "education",
                alias("COURSERA", priority = 21),
                alias("COURSERA ORG", priority = 21),
                alias("COURSERA PLUS", priority = 21),
                verifiedLogoKey = "coursera",
            ),
            merchant(
                "duolingo",
                "Duolingo",
                "education",
                alias("DUOLINGO", priority = 21),
                alias("DUOLINGO SUPER", priority = 21),
                alias("DUOLINGO PLUS", priority = 21),
                verifiedLogoKey = "duolingo",
            ),
            merchant(
                "cambly",
                "Cambly",
                "education",
                alias("CAMBLY", priority = 21),
                alias("CAMBLY KIDS", priority = 21),
                alias("CAMBLY COM", priority = 21),
                verifiedLogoKey = "cambly",
            ),
            merchant(
                "kunduz",
                "Kunduz",
                "education",
                alias("KUNDUZ EGITIM", priority = 21),
                alias("KUNDUZ APP", priority = 21),
                alias("KUNDUZ COM", priority = 21),
                verifiedLogoKey = "kunduz",
            ),
            merchant(
                "tonguc_akademi",
                "Tonguç Akademi",
                "education",
                alias("TONGUC AKADEMI", priority = 21),
                alias("TONGUCAKADEMI COM", priority = 21),
                verifiedLogoKey = "tonguc_akademi",
            ),
            merchant(
                "okulistik",
                "Okulistik",
                "education",
                alias("OKULISTIK", priority = 21),
                alias("OKULISTIK COM", priority = 21),
                verifiedLogoKey = "okulistik",
            ),
            merchant(
                "morpa_kampus",
                "Morpa Kampüs",
                "education",
                alias("MORPA KAMPUS", priority = 21),
                alias("MORPAKAMPUS COM", priority = 21),
                verifiedLogoKey = "morpa_kampus",
            ),
            merchant(
                "raunt",
                "Raunt",
                "education",
                alias("RAUNT EGITIM", priority = 21),
                alias("RAUNT COM", priority = 21),
                verifiedLogoKey = "raunt",
            ),
            merchant(
                "busuu",
                "Busuu",
                "education",
                alias("BUSUU", priority = 22),
                alias("BUSUU COM", priority = 22),
                verifiedLogoKey = "busuu",
            ),
            merchant(
                "preply",
                "Preply",
                "education",
                alias("PREPLY", priority = 22),
                alias("PREPLY COM", priority = 22),
                verifiedLogoKey = "preply",
            ),
            merchant(
                "italki",
                "italki",
                "education",
                alias("ITALKI", priority = 22),
                alias("ITALKI COM", priority = 22),
                verifiedLogoKey = "italki",
            ),
            merchant(
                "skillshare",
                "Skillshare",
                "education",
                alias("SKILLSHARE", priority = 22),
                alias("SKILLSHARE COM", priority = 22),
                verifiedLogoKey = "skillshare",
            ),
            merchant(
                "derslig",
                "Derslig",
                "education",
                alias("DERSLIG", priority = 22),
                alias("DERSLIG COM", priority = 22),
                alias("DERSLIG EGITIM", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "derslig",
            ),
            merchant(
                "benim_hocam",
                "Benim Hocam",
                "education",
                alias("BENIM HOCAM", priority = 22),
                alias("BENIMHOCAM COM", priority = 22),
                alias("BENIM HOCAM YAYINLARI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "benim_hocam",
            ),
            merchant(
                "pegem_akademi",
                "Pegem Akademi",
                "education",
                alias("PEGEM AKADEMI", priority = 22),
                alias("PEGEM NET", priority = 22),
                alias("PEGEM YAYINLARI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "pegem_akademi",
            ),
            merchant(
                "english_time",
                "English Time",
                "education",
                alias("ENGLISH TIME", priority = 22),
                alias("ENGLISHTIME COM", priority = 22),
                alias("ENGLISH TIME DIL OKULLARI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "english_time",
            ),
            merchant(
                "bilge_adam",
                "Bilge Adam",
                "education",
                alias("BILGE ADAM AKADEMI", MerchantAliasType.BRAND_AND_SERVICE, priority = 22),
                alias("BILGE ADAM EGITIM", MerchantAliasType.BRAND_AND_SERVICE, priority = 22),
                alias("BILGE ADAM BILGISAYAR", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("BILGEADAM COM", priority = 22),
                alias("BILGEADAM", priority = 20),
                verifiedLogoKey = "bilge_adam",
            ),
            merchant(
                "wall_street_english",
                "Wall Street English",
                "education",
                alias("WALL STREET ENGLISH", priority = 22),
                alias("WSE COM TR", priority = 22),
                verifiedLogoKey = "wall_street_english",
            ),
            merchant(
                "amerikankultur",
                "Amerikan Kültür",
                "education",
                alias("AMERIKAN KULTUR", priority = 22),
                alias("AMERIKAN KULTUR DERNEGI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("AMERIKAN KULTUR DIL OKULLARI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("AMERIKANKULTUR ORG TR", priority = 22),
                verifiedLogoKey = "amerikankultur",
            ),
            merchant(
                "dr",
                "D&R",
                "shopping",
                alias("D&R", priority = 22),
                alias("DR MAGAZACILIK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                alias("DR COM TR", priority = 22),
                verifiedLogoKey = "dr",
            ),
            merchant(
                "kitapyurdu",
                "Kitapyurdu",
                "shopping",
                alias("KITAPYURDU", priority = 22),
                alias("KITAPYURDU COM", priority = 22),
                alias("KITAPYURDU YAYINCILIK", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                verifiedLogoKey = "kitapyurdu",
            ),
            merchant(
                "bkm_kitap",
                "BKM Kitap",
                "shopping",
                alias("BKM KITAP", priority = 22),
                alias("BKMKITAP COM", priority = 22),
                verifiedLogoKey = "bkm_kitap",
            ),
            merchant(
                "idefix",
                "Idefix",
                "shopping",
                alias("IDEFIX", priority = 22),
                alias("IDEFIX COM", priority = 22),
                verifiedLogoKey = "idefix",
            ),
            merchant(
                "petlebi",
                "Petlebi",
                "family_pets",
                alias("PETLEBI", priority = 23),
                alias("PETLEBI COM", priority = 23),
                verifiedLogoKey = "petlebi",
            ),
            merchant(
                "civil",
                "Civil",
                "family_pets",
                alias("CIVILIM", priority = 23),
                alias("CIVILIM COM", priority = 23),
                alias("CIVIL COCUK GIYIM", priority = 23),
                alias("CIVIL MAGAZACILIK", MerchantAliasType.LEGAL_NAME, priority = 20),
                verifiedLogoKey = "civil",
            ),
            merchant(
                "temizmama",
                "Temizmama",
                "family_pets",
                alias("TEMIZMAMA", priority = 23),
                alias("TEMIZMAMA COM", priority = 23),
                alias("CAGATAY EVCIL HAYVAN MAMALARI", MerchantAliasType.LEGAL_NAME, priority = 20),
                verifiedLogoKey = "temizmama",
            ),
            merchant(
                "toyzz_shop",
                "Toyzz Shop",
                "family_pets",
                alias("TOYZZ SHOP", priority = 23),
                alias("TOYZZSHOP", priority = 23),
                alias("TOYPA MAGAZACILIK", MerchantAliasType.LEGAL_NAME, priority = 20),
                verifiedLogoKey = "toyzz_shop",
            ),
            merchant(
                "juen_pet_market",
                "Juen Pet Market",
                "family_pets",
                alias("JUEN PET MARKET", priority = 23),
                alias("JUEN PET", priority = 23),
                alias("JUENPETMARKET", priority = 23),
                negative = listOf("JUEN PET URUNLERI"),
                verifiedLogoKey = "juen_pet_market",
            ),
            merchant(
                "joker_baby",
                "Joker Baby",
                "family_pets",
                alias("JOKER BABY", priority = 23),
                alias("JOKER COM TR", priority = 23),
                negative = listOf("JOLLY JOKER"),
                verifiedLogoKey = "joker_baby",
            ),
            merchant(
                "armagan_oyuncak",
                "Armağan Oyuncak",
                "family_pets",
                alias("ARMAGAN OYUNCAK", priority = 23),
                alias("ARMAGANOYUNCAK", priority = 23),
                alias("ARMAGAN OYUNCAK SANAYI", MerchantAliasType.LEGAL_NAME, priority = 20),
                verifiedLogoKey = "armagan_oyuncak",
            ),
            merchant(
                "petzz_shop",
                "Petzz Shop",
                "family_pets",
                alias("PETZZ SHOP", priority = 23),
                alias("PETZZSHOP", priority = 23),
                alias("PETZZ", priority = 23),
                verifiedLogoKey = "petzz_shop",
            ),
            merchant(
                "markamama",
                "Markamama",
                "family_pets",
                alias("MARKAMAMA", priority = 24),
                alias("MARKAMAMA COM TR", priority = 24),
                alias("MARKAMAMA PET SANAYI", MerchantAliasType.LEGAL_NAME, priority = 20),
                verifiedLogoKey = "markamama",
            ),
            merchant(
                "petburada",
                "Petburada",
                "family_pets",
                alias("PETBURADA", priority = 24),
                alias("PET BURADA", priority = 24),
                alias("PETBURADA COM", priority = 24),
                alias("AKVET VETERINERLIK", MerchantAliasType.LEGAL_NAME, priority = 20),
                negative = listOf("HEPSIBURADA"),
                verifiedLogoKey = "petburada",
            ),
            merchant(
                "petihtiyac",
                "Petihtiyaç",
                "family_pets",
                alias("PETIHTIYAC", priority = 24),
                alias("PET IHTIYAC", priority = 24),
                alias("PETIHTIYAC COM", priority = 24),
                alias("SENTURKLER VETERINERLIK", MerchantAliasType.LEGAL_NAME, priority = 20),
                verifiedLogoKey = "petihtiyac",
            ),
            merchant(
                "kolaymama",
                "Kolay Mama",
                "family_pets",
                alias("KOLAYMAMA", priority = 24),
                alias("KOLAY MAMA", priority = 24),
                alias("KOLAYMAMA COM", priority = 24),
                alias("DORATEK GIDA", MerchantAliasType.LEGAL_NAME, priority = 20),
                verifiedLogoKey = "kolaymama",
            ),
            merchant(
                "panco",
                "Panço",
                "family_pets",
                alias("PANCO", priority = 24),
                alias("PANCO COM TR", priority = 24),
                alias("PANCO COCUK", priority = 24),
                alias("PANCO GIYIM", MerchantAliasType.LEGAL_NAME, priority = 20),
                verifiedLogoKey = "panco",
            ),
            merchant(
                "bg_store",
                "B&G Store",
                "family_pets",
                alias("BG STORE", priority = 24),
                alias("B&G STORE", priority = 24),
                alias("BGSTORE", priority = 24),
                alias("BGSTORE COM TR", priority = 24),
                alias("BGSTORE MAGAZACILIK", MerchantAliasType.LEGAL_NAME, priority = 20),
                verifiedLogoKey = "bg_store",
            ),
            merchant(
                "minycenter",
                "MinyCenter",
                "family_pets",
                alias("MINYCENTER", priority = 24),
                alias("MINY CENTER", priority = 24),
                alias("MINYCENTER COM TR", priority = 24),
                alias("GAMI GIYIM", MerchantAliasType.LEGAL_NAME, priority = 20),
                verifiedLogoKey = "minycenter",
            ),
            merchant(
                "vetamerikan",
                "VetAmerikan",
                "family_pets",
                alias("VETAMERIKAN", priority = 24),
                alias("VET AMERIKAN", priority = 24),
                alias("VETAMERIKAN ORG", priority = 24),
                alias("VETAMERIKAN HAYVAN HASTANESI", priority = 24),
                alias("VET AMERIKAN HAYVAN HASTANESI", priority = 24),
                alias("VETAMERIKAN VETERINERLIK", MerchantAliasType.LEGAL_NAME, priority = 20),
                verifiedLogoKey = "vetamerikan",
            ),
            // Gifts & Donations
            merchant(
                "kizilay",
                "Türk Kızılay",
                "gifts_donations",
                alias("TURK KIZILAY", priority = 24),
                alias("TURK KIZILAYI", priority = 20),
                alias("TURKIYE KIZILAY DERNEGI", MerchantAliasType.LEGAL_NAME, priority = 20),
                alias("KIZILAY BAGIS", MerchantAliasType.BRAND_AND_SERVICE, priority = 24),
                negative =
                    listOf(
                        "KIZILAY AVM",
                        "KIZILAY METRO",
                        "KIZILAY YATIRIM",
                        "KIZILAY MAGAZA",
                        "KIZILAY SAGLIK",
                    ),
                verifiedLogoKey = "kizilay",
            ),
            merchant(
                "losev",
                "LÖSEV",
                "gifts_donations",
                alias("LOSEV", priority = 24),
                alias("LOSEMILI COCUKLAR SAGLIK VE EGITIM VAKFI", MerchantAliasType.LEGAL_NAME, priority = 20),
                alias("LOSEV VAKFI", priority = 15),
                alias("LOSEV BAGIS", MerchantAliasType.BRAND_AND_SERVICE, priority = 24),
                verifiedLogoKey = "losev",
            ),
            merchant(
                "tema",
                "TEMA Vakfı",
                "gifts_donations",
                alias(
                    "TEMA TURKIYE EROZYONLA MUCADELE AGACLANDIRMA VE DOGAL VARLIKLARI KORUMA VAKFI",
                    MerchantAliasType.LEGAL_NAME,
                    priority = 20,
                ),
                alias("TEMA VAKFI", priority = 24),
                alias("TEMA BAGIS", MerchantAliasType.BRAND_AND_SERVICE, priority = 24),
                verifiedLogoKey = "tema",
            ),
            merchant(
                "tev",
                "TEV",
                "gifts_donations",
                alias("TURK EGITIM VAKFI", MerchantAliasType.LEGAL_NAME, priority = 24),
                alias("TEV VAKFI", priority = 20),
                alias("TEV BAGIS", MerchantAliasType.BRAND_AND_SERVICE, priority = 24),
                verifiedLogoKey = "tev",
            ),
            merchant(
                "mehmetcik_vakfi",
                "Mehmetçik Vakfı",
                "gifts_donations",
                alias("TURK SILAHLI KUVVETLERI MEHMETCIK VAKFI", MerchantAliasType.LEGAL_NAME, priority = 20),
                alias("TSK MEHMETCIK VAKFI", priority = 24),
                alias("MEHMETCIK VAKFI", priority = 20),
                alias("MEHMETCIK VAKFI BAGIS", MerchantAliasType.BRAND_AND_SERVICE, priority = 24),
                negative =
                    listOf(
                        "TSKGV",
                    ),
                verifiedLogoKey = "mehmetcik_vakfi",
            ),
            merchant(
                "akut",
                "AKUT",
                "gifts_donations",
                alias("AKUT ARAMA KURTARMA", priority = 24),
                alias("AKUT DERNEGI", priority = 20),
                alias("AKUT BAGIS", MerchantAliasType.BRAND_AND_SERVICE, priority = 24),
                verifiedLogoKey = "akut",
            ),
            merchant(
                "acev",
                "AÇEV",
                "gifts_donations",
                alias("ANNE COCUK EGITIM VAKFI", MerchantAliasType.LEGAL_NAME, priority = 20),
                alias("ACEV", MerchantAliasType.SHORT, priority = 24),
                alias("ACEV VAKFI", priority = 20),
                alias("ACEV BAGIS", MerchantAliasType.BRAND_AND_SERVICE, priority = 24),
                verifiedLogoKey = "acev",
            ),
            merchant(
                "tog",
                "Toplum Gönüllüleri Vakfı",
                "gifts_donations",
                alias("TOPLUM GONULLULERI VAKFI", MerchantAliasType.LEGAL_NAME, priority = 24),
                alias("TOG VAKFI", priority = 20),
                alias("TOG BAGIS", MerchantAliasType.BRAND_AND_SERVICE, priority = 24),
                negative =
                    listOf(
                        "TOGG",
                    ),
                verifiedLogoKey = "tog",
            ),
            merchant(
                "gib",
                "Gelir İdaresi Başkanlığı",
                "taxes_fees",
                alias("GELIR IDARESI BASKANLIGI", MerchantAliasType.LEGAL_NAME, priority = 24),
                alias("DIJITAL VERGI DAIRESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 24),
                alias("INTERNET VERGI DAIRESI", MerchantAliasType.BRAND_AND_SERVICE, priority = 20),
                negative =
                    listOf(
                        "IBAN",
                        "TRANSFER",
                    ),
                verifiedLogoKey = "gib",
            ),
            merchant(
                "sgk",
                "Sosyal Güvenlik Kurumu",
                "taxes_fees",
                alias("SOSYAL GUVENLIK KURUMU", MerchantAliasType.LEGAL_NAME, priority = 24),
                negative =
                    listOf(
                        "IBAN",
                        "TRANSFER",
                        "MOSIP",
                    ),
                verifiedLogoKey = "sgk",
            ),
        )

    private fun merchant(
        id: String,
        name: String,
        icon: String,
        vararg aliases: MerchantAlias,
        negative: List<String> = emptyList(),
        catalogVersion: Int = CATALOG_VERSION,
        verifiedLogoKey: String? = null,
    ) = Merchant(
        id = id,
        displayName = name,
        aliases = aliases.toList(),
        negativeAliases = negative.map(::MerchantNegativeAlias),
        categoryIconKey = CategoryIcon(icon),
        verifiedLogoKey = verifiedLogoKey,
        catalogVersion = catalogVersion,
    )

    private fun alias(
        value: String,
        type: MerchantAliasType = MerchantAliasType.STRONG_BRAND,
        priority: Int = 0,
    ) = MerchantAlias(value = value, type = type, priority = priority)
}

sealed interface TransactionMerchantResolutionResult {
    data class Success(
        val merchantInfo: TransactionMerchantInfo?,
    ) : TransactionMerchantResolutionResult

    data class InvalidVerification(
        val id: String? = null,
    ) : TransactionMerchantResolutionResult
}

/**
 * Repository ve veri katmanı için saf ve ortak merchant attribution çözümleyicisi.
 * Yalnızca MATCHED ve güven >= MIN_LOGO_CONFIDENCE durumunda tipli TransactionMerchantInfo üretir.
 */
object TransactionMerchantAttributionResolver {
    fun resolve(
        description: String?,
        engine: MerchantRecognitionEngine = MerchantRecognitionEngine(),
    ): TransactionMerchantInfo? = resolve(description, decision = null, engine = engine)

    fun resolve(
        description: String?,
        decision: UserMerchantDecision?,
        engine: MerchantRecognitionEngine = MerchantRecognitionEngine(),
    ): TransactionMerchantInfo? {
        val raw = description?.trim().orEmpty()
        if (raw.isBlank()) return null

        val request =
            MerchantRecognitionRequest(
                rawDescription = raw,
                inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
                personalDecision = decision,
            )
        val result = engine.recognize(request)

        return toMerchantInfo(result)
    }

    private fun toMerchantInfo(result: MerchantRecognitionResult): TransactionMerchantInfo? =
        if (result.status == MerchantMatchStatus.MATCHED &&
            result.confidence >= MerchantRecognitionResult.MIN_LOGO_CONFIDENCE &&
            result.merchant != null &&
            result.source != null
        ) {
            TransactionMerchantInfo(
                merchantId = EntityId(result.merchant.id),
                matchSource = result.source,
                confidence = result.confidence,
                catalogVersion = result.merchant.catalogVersion,
            )
        } else {
            null
        }

    suspend fun resolveWithDecision(
        description: String?,
        verificationRepository: UserMerchantVerificationRepository,
        engine: MerchantRecognitionEngine = MerchantRecognitionEngine(),
    ): TransactionMerchantResolutionResult {
        val raw = description?.trim().orEmpty()
        if (raw.isBlank()) return TransactionMerchantResolutionResult.Success(null)

        val baseRequest =
            MerchantRecognitionRequest(
                rawDescription = raw,
                inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
                personalDecision = null,
            )
        val baseResult = engine.recognize(baseRequest)
        if (baseResult.status == MerchantMatchStatus.EXCLUDED_TRANSACTION) {
            return TransactionMerchantResolutionResult.Success(null)
        }

        return when (val lookup = verificationRepository.getDecisionForAlias(raw)) {
            is UserMerchantVerificationLookupResult.Decision -> {
                val decisionRequest =
                    MerchantRecognitionRequest(
                        rawDescription = raw,
                        inputSource = MerchantRecognitionInputSource.USER_ENTERED_DESCRIPTION,
                        personalDecision = lookup.decision,
                    )
                val decisionResult = engine.recognize(decisionRequest)
                if (decisionResult.status == MerchantMatchStatus.INVALID_VERIFICATION) {
                    val invalidId = (lookup.decision as? UserMerchantDecision.Assign)?.merchantId
                    TransactionMerchantResolutionResult.InvalidVerification(invalidId)
                } else {
                    TransactionMerchantResolutionResult.Success(toMerchantInfo(decisionResult))
                }
            }
            is UserMerchantVerificationLookupResult.NotFound -> {
                TransactionMerchantResolutionResult.Success(toMerchantInfo(baseResult))
            }
            is UserMerchantVerificationLookupResult.InvalidStoredVerification -> {
                TransactionMerchantResolutionResult.InvalidVerification(lookup.id)
            }
        }
    }
}

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
    const val CATALOG_VERSION: Int = 18
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

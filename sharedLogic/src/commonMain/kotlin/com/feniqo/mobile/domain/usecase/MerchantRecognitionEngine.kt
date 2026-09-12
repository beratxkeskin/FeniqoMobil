package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantAlias
import com.feniqo.mobile.domain.model.MerchantAliasType
import com.feniqo.mobile.domain.model.MerchantMatchSource
import com.feniqo.mobile.domain.model.MerchantMatchStatus
import com.feniqo.mobile.domain.model.MerchantNegativeAlias
import com.feniqo.mobile.domain.model.MerchantRecognitionRequest
import com.feniqo.mobile.domain.model.MerchantRecognitionResult
import com.feniqo.mobile.domain.model.TransactionClassification

/** Banka açıklamasını locale/platform davranışına bağlı kalmadan eşleştirmeye hazırlar. */
object TurkishDescriptionNormalizer {
    fun normalize(value: String): String = buildString(value.length) {
        var previousWasSpace = true
        value.forEach { character ->
            val normalized = normalizeCharacter(character)
            if (normalized == null) {
                if (!previousWasSpace) append(' ')
                previousWasSpace = true
            } else {
                append(normalized)
                previousWasSpace = false
            }
        }
    }.trim()

    private fun normalizeCharacter(value: Char): Char? = when (value) {
        in 'a'..'z' -> value.uppercaseChar()
        in 'A'..'Z', in '0'..'9' -> value
        'ç', 'Ç' -> 'C'
        'ğ', 'Ğ' -> 'G'
        'ı', 'İ', 'i' -> 'I'
        'ö', 'Ö' -> 'O'
        'ş', 'Ş' -> 'S'
        'ü', 'Ü' -> 'U'
        else -> null
    }
}

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
        val classification = classify(normalized)
        if (classification in excludedClassifications) {
            return result(request, normalized, classification, status = MerchantMatchStatus.EXCLUDED_TRANSACTION)
        }

        request.verifiedMerchantId?.let { verifiedId ->
            catalog.firstOrNull { it.id == verifiedId }?.let { merchant ->
                return result(
                    request,
                    normalized,
                    classification,
                    merchant,
                    100,
                    MerchantMatchSource.USER_VERIFIED,
                    MerchantMatchStatus.MATCHED,
                )
            }
        }
        verifiedResult(request.workspaceVerifiedMerchantId, MerchantMatchSource.WORKSPACE_VERIFIED, 100, request, normalized, classification)?.let { return it }
        verifiedResult(request.bankVerifiedMerchantId, MerchantMatchSource.BANK_VERIFIED_ID, 98, request, normalized, classification)?.let { return it }

        if (normalized.isEmpty()) return result(request, normalized, classification)
        val candidates = catalog.flatMap { merchant -> candidatesFor(merchant, normalized) }
        if (candidates.isEmpty()) return result(request, normalized, classification)

        val sorted = candidates
            .groupBy { it.merchant.id }
            .map { (_, merchantCandidates) -> merchantCandidates.maxWith(candidateComparator) }
            .sortedWith(
            compareByDescending<Candidate> { it.confidence }
                .thenByDescending { it.priority }
                .thenByDescending { it.alias.length }
                .thenBy { it.merchant.id },
        )
        val best = sorted.first()
        val competing = sorted.drop(1).firstOrNull { best.confidence - it.confidence < MIN_DECISIVE_SCORE_GAP }
        if (competing != null) {
            return result(request, normalized, classification, status = MerchantMatchStatus.AMBIGUOUS)
        }
        val status = if (best.confidence >= MerchantRecognitionResult.MIN_LOGO_CONFIDENCE) {
            MerchantMatchStatus.MATCHED
        } else {
            MerchantMatchStatus.LOW_CONFIDENCE
        }
        return result(request, normalized, classification, best.merchant, best.confidence, best.source, status)
    }

    private fun candidatesFor(merchant: Merchant, description: String): List<Candidate> {
        if (merchant.negativeAliases.any { containsPhrase(description, TurkishDescriptionNormalizer.normalize(it.value)) }) {
            return emptyList()
        }
        return merchant.aliases.mapNotNull { alias ->
            val normalizedAlias = TurkishDescriptionNormalizer.normalize(alias.value)
            if (!containsPhrase(description, normalizedAlias)) return@mapNotNull null
            val effectiveType = when {
                normalizedAlias.length <= SHORT_ALIAS_MAX_LENGTH -> MerchantAliasType.SHORT
                description == normalizedAlias -> MerchantAliasType.EXACT
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


    private fun confidenceFor(type: MerchantAliasType): Int = when (type) {
        MerchantAliasType.USER_VERIFIED -> 100
        MerchantAliasType.EXACT -> 95
        MerchantAliasType.LEGAL_NAME -> 92
        MerchantAliasType.BRAND_AND_SERVICE -> 90
        MerchantAliasType.BRANCH_OR_POS_PATTERN -> 86
        MerchantAliasType.STRONG_BRAND -> 80
        MerchantAliasType.SHORT -> 65
    }

    private fun sourceFor(type: MerchantAliasType): MerchantMatchSource = when (type) {
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
    ): MerchantRecognitionResult? = merchantId
        ?.let { id -> catalog.firstOrNull { it.id == id } }
        ?.let { merchant -> result(request, normalized, classification, merchant, confidence, source, MerchantMatchStatus.MATCHED) }

    private fun classify(description: String): TransactionClassification = when {
        containsAny(description, "IADE", "REFUND") -> TransactionClassification.REFUND
        containsAny(description, "MAAS ODEMESI", "MAAS", "UCRET ODEMESI") -> TransactionClassification.SALARY
        containsAny(description, "KREDI KARTI ODEMESI", "KART BORCU ODEMESI") -> TransactionClassification.CREDIT_CARD_PAYMENT
        containsAny(description, "HESAPLAR ARASI", "VIRMAN") -> TransactionClassification.INTERNAL_TRANSFER
        containsAny(description, "YATIRIM HESABINA", "BIRIKIM HESABINA", "FON ALIM", "HISSE ALIM") -> TransactionClassification.INVESTMENT_TRANSFER
        containsAny(description, "NAKIT CEKIM", "ATM PARA CEKME", "ATM CEKIM") -> TransactionClassification.CASH_WITHDRAWAL
        containsAny(description, "NAKIT YATIRMA", "ATM PARA YATIRMA", "ATM YATIRMA") -> TransactionClassification.CASH_DEPOSIT
        containsAny(description, "BAKIYE DUZELTMESI", "BASLANGIC BAKIYESI") -> TransactionClassification.BALANCE_ADJUSTMENT
        containsAny(description, "FAST", "EFT", "HAVALE") -> TransactionClassification.PERSONAL_TRANSFER
        description.isBlank() -> TransactionClassification.UNKNOWN
        else -> TransactionClassification.PURCHASE
    }

    private fun containsAny(description: String, vararg phrases: String): Boolean =
        phrases.any { containsPhrase(description, it) }

    private fun containsPhrase(description: String, phrase: String): Boolean =
        phrase.isNotEmpty() && " $description ".contains(" $phrase ")

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
        normalizedDescription = normalized,
        transactionClassification = classification,
        merchant = merchant,
        confidence = confidence,
        source = source,
        status = status,
    )

    private companion object {
        const val SHORT_ALIAS_MAX_LENGTH = 4
        const val MIN_DECISIVE_SCORE_GAP = 10
        val candidateComparator = compareBy<Candidate> { it.confidence }
            .thenBy { it.priority }
            .thenBy { it.alias.length }
        val excludedClassifications = setOf(
            TransactionClassification.PERSONAL_TRANSFER,
            TransactionClassification.SALARY,
            TransactionClassification.CREDIT_CARD_PAYMENT,
            TransactionClassification.INTERNAL_TRANSFER,
            TransactionClassification.INVESTMENT_TRANSFER,
            TransactionClassification.CASH_WITHDRAWAL,
            TransactionClassification.CASH_DEPOSIT,
            TransactionClassification.BALANCE_ADJUSTMENT,
        )
    }
}

object StarterMerchantCatalog {
    val merchants: List<Merchant> = listOf(
        merchant("starbucks", "Starbucks", "food_dining", alias("STARBUCKS"), alias("SBUX", MerchantAliasType.SHORT)),
        merchant("migros", "Migros", "groceries", alias("MIGROS"), alias("MIGROS SANAL MARKET"), negative = listOf("MIGROS HEMEN")),
        merchant("shell", "Shell", "fuel", alias("SHELL"), alias("SHELL TURCAS")),
        merchant("spotify", "Spotify", "subscriptions", alias("SPOTIFY")),
        merchant("netflix", "Netflix", "subscriptions", alias("NETFLIX")),
        merchant("trendyol_yemek", "Trendyol Yemek", "food_dining", alias("TRENDYOL YEMEK", MerchantAliasType.BRAND_AND_SERVICE, 20)),
        merchant("trendyol", "Trendyol", "shopping", alias("TRENDYOL")),
        merchant("getiryemek", "GetirYemek", "food_dining", alias("GETIRYEMEK", MerchantAliasType.BRAND_AND_SERVICE, 20), alias("GETIR YEMEK", MerchantAliasType.BRAND_AND_SERVICE, 20)),
        merchant("getir", "Getir", "groceries", alias("GETIR")),
        merchant("amazon_prime", "Amazon Prime", "subscriptions", alias("AMAZON PRIME", MerchantAliasType.BRAND_AND_SERVICE, 20)),
        merchant("amazon", "Amazon", "shopping", alias("AMAZON")),
        merchant("uber", "Uber", "transportation", alias("UBER")),
        merchant("thy", "THY", "travel", alias("TURK HAVA YOLLARI"), alias("TURKISH AIRLINES"), alias("THY", MerchantAliasType.SHORT)),
        merchant("turkcell", "Turkcell", "utilities", alias("TURKCELL")),
        merchant("apple_services", "Apple Services", "subscriptions", alias("APPLE COM BILL", MerchantAliasType.BRAND_AND_SERVICE), alias("APPLE SERVICES", MerchantAliasType.BRAND_AND_SERVICE), alias("ITUNES COM BILL", MerchantAliasType.BRAND_AND_SERVICE)),
    )

    private fun merchant(
        id: String,
        name: String,
        icon: String,
        vararg aliases: MerchantAlias,
        negative: List<String> = emptyList(),
    ) = Merchant(
        id = id,
        displayName = name,
        aliases = aliases.toList(),
        negativeAliases = negative.map(::MerchantNegativeAlias),
        categoryIconKey = CategoryIcon(icon),
        verifiedLogoKey = id,
    )

    private fun alias(
        value: String,
        type: MerchantAliasType = MerchantAliasType.STRONG_BRAND,
        priority: Int = 0,
    ) = MerchantAlias(value = value, type = type, priority = priority)
}

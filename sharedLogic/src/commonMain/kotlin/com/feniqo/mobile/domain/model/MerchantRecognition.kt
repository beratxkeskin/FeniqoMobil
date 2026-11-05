package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

/** Bir merchant'ın farklı banka açıklamalarında veya kullanıcı girdisinde görülebilen, önceliklendirilebilir adı. */
data class MerchantAlias(
    val value: String,
    val type: MerchantAliasType = MerchantAliasType.STRONG_BRAND,
    val scope: MerchantAliasScope = MerchantAliasScope.GENERAL,
    val priority: Int = 0,
) {
    init {
        require(value.isNotBlank()) { "Merchant alias boş olamaz." }
        require(priority >= 0) { "Merchant alias önceliği negatif olamaz." }
    }
}

enum class MerchantAliasType {
    EXACT,
    LEGAL_NAME,
    STRONG_BRAND,
    BRAND_AND_SERVICE,
    BRANCH_OR_POS_PATTERN,
    SHORT,
    USER_VERIFIED,
}

enum class MerchantAliasScope {
    PERSONAL,
    WORKSPACE,
    GENERAL,
}

/** Bu ifade varsa ilgili merchant adayını dışlayan bağlama özgü alias. */
data class MerchantNegativeAlias(val value: String) {
    init {
        require(value.isNotBlank()) { "Merchant negatif alias boş olamaz." }
    }
}

enum class TransactionClassification {
    PURCHASE,
    REFUND,
    PERSONAL_TRANSFER,
    SALARY,
    CREDIT_CARD_PAYMENT,
    INTERNAL_TRANSFER,
    INVESTMENT_TRANSFER,
    CASH_WITHDRAWAL,
    CASH_DEPOSIT,
    BALANCE_ADJUSTMENT,
    UNKNOWN,
}

/**
 * İşlem açıklamasının sınıflandırmasını ve merchant tanıma/düzeltme için uygunluğunu
 * tek bir fail-closed kaynaktan belirleyen domain sözleşmesi.
 */
object TransactionMerchantEligibility {
    val EXCLUDED_CLASSIFICATIONS: Set<TransactionClassification> =
        setOf(
            TransactionClassification.PERSONAL_TRANSFER,
            TransactionClassification.SALARY,
            TransactionClassification.CREDIT_CARD_PAYMENT,
            TransactionClassification.INTERNAL_TRANSFER,
            TransactionClassification.INVESTMENT_TRANSFER,
            TransactionClassification.CASH_WITHDRAWAL,
            TransactionClassification.CASH_DEPOSIT,
            TransactionClassification.BALANCE_ADJUSTMENT,
        )

    fun classify(rawOrNormalizedDescription: String): TransactionClassification {
        val description = TurkishDescriptionNormalizer.normalize(rawOrNormalizedDescription)
        return when {
            containsAny(description, "IADE", "REFUND") -> TransactionClassification.REFUND
            containsAny(description, "MAAS ODEMESI", "MAAS", "UCRET ODEMESI") -> TransactionClassification.SALARY
            containsAny(description, "KREDI KARTI ODEMESI", "KART BORCU ODEMESI") -> TransactionClassification.CREDIT_CARD_PAYMENT
            containsAny(description, "HESAPLAR ARASI", "VIRMAN") -> TransactionClassification.INTERNAL_TRANSFER
            containsAny(description, "YATIRIM HESABINA", "BIRIKIM HESABINA", "FON ALIM", "HISSE ALIM") ->
                TransactionClassification.INVESTMENT_TRANSFER
            containsAny(description, "NAKIT CEKIM", "ATM PARA CEKME", "ATM CEKIM") -> TransactionClassification.CASH_WITHDRAWAL
            containsAny(description, "NAKIT YATIRMA", "ATM PARA YATIRMA", "ATM YATIRMA") -> TransactionClassification.CASH_DEPOSIT
            containsAny(description, "BAKIYE DUZELTMESI", "BASLANGIC BAKIYESI") -> TransactionClassification.BALANCE_ADJUSTMENT
            containsAny(description, "FAST", "EFT", "HAVALE") -> TransactionClassification.PERSONAL_TRANSFER
            description.isBlank() -> TransactionClassification.UNKNOWN
            else -> TransactionClassification.PURCHASE
        }
    }

    fun isExcluded(rawOrNormalizedDescription: String): Boolean {
        val classification = classify(rawOrNormalizedDescription)
        return classification in EXCLUDED_CLASSIFICATIONS
    }

    private fun containsAny(
        description: String,
        vararg phrases: String,
    ): Boolean = phrases.any { containsPhrase(description, it) }

    private fun containsPhrase(
        description: String,
        phrase: String,
    ): Boolean = phrase.isNotEmpty() && " $description ".contains(" $phrase ")
}

/** Merchant tanıma isteğinin kaynağını tipli olarak belirten sözleşme. */
enum class MerchantRecognitionInputSource {
    USER_ENTERED_DESCRIPTION,
    BANK_DESCRIPTION,
}

enum class MerchantMatchSource {
    USER_VERIFIED,
    WORKSPACE_VERIFIED,
    BANK_VERIFIED_ID,
    EXACT_ALIAS,
    LEGAL_NAME,
    BRAND_AND_SERVICE,
    BRANCH_OR_POS_PATTERN,
    STRONG_BRAND,
    SHORT_ALIAS,
}

enum class MerchantMatchStatus {
    MATCHED,
    LOW_CONFIDENCE,
    AMBIGUOUS,
    EXCLUDED_TRANSACTION,
    NO_MATCH,
    BLOCKED_BY_USER,
    INVALID_VERIFICATION,
}

/** Kişisel kullanıcı merchant doğrulama veya engelleme kararı. */
sealed interface UserMerchantDecision {
    data class Assign(
        val merchantId: String,
    ) : UserMerchantDecision {
        constructor(merchantId: EntityId) : this(merchantId.value)

        init {
            require(merchantId.isNotBlank()) { "Assign merchantId boş olamaz." }
        }
    }

    data object Block : UserMerchantDecision
}

data class Merchant(
    val id: String,
    val displayName: String,
    val aliases: List<MerchantAlias>,
    val negativeAliases: List<MerchantNegativeAlias> = emptyList(),
    val categoryIconKey: CategoryIcon,
    /** Sağlayıcı URL'si değil, daha sonra güvenilir logo kaynağına çözümlenecek kararlı anahtardır. */
    val verifiedLogoKey: String? = null,
    val catalogVersion: Int = DEFAULT_CATALOG_VERSION,
) {
    init {
        require(id.isNotBlank()) { "Merchant kimliği boş olamaz." }
        require(displayName.isNotBlank()) { "Merchant adı boş olamaz." }
        require(aliases.isNotEmpty()) { "Merchant en az bir alias taşımalıdır." }
        require(catalogVersion > 0) { "Merchant katalog sürümü pozitif olmalıdır: $catalogVersion" }
    }

    companion object {
        const val DEFAULT_CATALOG_VERSION = 1
    }
}

/** İşleme bağlanan tipli merchant attribution bilgisi. */
data class TransactionMerchantInfo(
    val merchantId: EntityId,
    val matchSource: MerchantMatchSource,
    val confidence: Int,
    val catalogVersion: Int,
) {
    init {
        require(merchantId.value.isNotBlank()) { "merchantId boş olamaz." }
        require(confidence in 0..100) { "merchantConfidence 0..100 aralığında olmalıdır: $confidence" }
        require(catalogVersion > 0) { "merchantCatalogVersion pozitif olmalıdır: $catalogVersion" }
    }
}

data class MerchantRecognitionRequest(
    /** Ham açıklama değiştirilmez ve hiçbir üçüncü tarafa gönderilmez. */
    val rawDescription: String,
    val inputSource: MerchantRecognitionInputSource,
    val personalDecision: UserMerchantDecision? = null,
    val workspaceVerifiedMerchantId: String? = null,
    val bankVerifiedMerchantId: String? = null,
)

data class MerchantRecognitionResult(
    val rawDescription: String,
    val inputSource: MerchantRecognitionInputSource,
    val normalizedDescription: String,
    val transactionClassification: TransactionClassification,
    val merchant: Merchant?,
    val confidence: Int,
    val source: MerchantMatchSource?,
    val status: MerchantMatchStatus,
) {
    init {
        require(confidence in 0..100) { "Merchant güven puanı 0..100 aralığında olmalıdır: $confidence" }
        if (status == MerchantMatchStatus.MATCHED) {
            require(merchant != null) { "MATCHED durumunda merchant boş olamaz." }
            require(source != null) { "MATCHED durumunda source boş olamaz." }
        }
        if (status == MerchantMatchStatus.BLOCKED_BY_USER) {
            require(merchant == null) { "BLOCKED_BY_USER durumunda merchant bulunamaz." }
            require(confidence == 0) { "BLOCKED_BY_USER durumunda güven puanı 0 olmalıdır: $confidence" }
            require(source == MerchantMatchSource.USER_VERIFIED) {
                "BLOCKED_BY_USER durumunda kaynak USER_VERIFIED olmalıdır: $source"
            }
        }
        if (status == MerchantMatchStatus.INVALID_VERIFICATION) {
            require(merchant == null) { "INVALID_VERIFICATION durumunda merchant bulunamaz." }
            require(confidence == 0) { "INVALID_VERIFICATION durumunda güven puanı 0 olmalıdır: $confidence" }
        }
    }

    val canShowMerchantLogo: Boolean
        get() = merchant != null && confidence >= MIN_LOGO_CONFIDENCE

    val confidenceBand: MerchantConfidenceBand
        get() =
            when (confidence) {
                in 90..100 -> MerchantConfidenceBand.VERIFIED
                in 75..89 -> MerchantConfidenceBand.HIGH
                in 55..74 -> MerchantConfidenceBand.MEDIUM
                in 1..54 -> MerchantConfidenceBand.LOW
                else -> MerchantConfidenceBand.NONE
            }

    companion object {
        const val MIN_LOGO_CONFIDENCE = 75
    }
}

enum class MerchantConfidenceBand {
    VERIFIED,
    HIGH,
    MEDIUM,
    LOW,
    NONE,
}

/** Genel sözcüklerin tek başına merchant eşleşmesi üretmesini engelleyen kanonik küme. */
val DISALLOWED_STANDALONE_GENERIC_TOKENS: Set<String> =
    setOf(
        "MARKET",
        "PREMIUM",
        "APPLE",
        "SHOP",
        "STORE",
        "YEMEK",
    )

enum class MerchantVerificationScope {
    PERSONAL,
}

/** İşlem açıklamasını locale/platform davranışına bağlı kalmadan eşleştirmeye hazırlar. */
object TurkishDescriptionNormalizer {
    fun normalize(value: String): String =
        buildString(value.length + 8) {
            var previousWasSpace = true
            value.forEach { character ->
                if (character == '+') {
                    if (!previousWasSpace && isNotEmpty()) append(' ')
                    append("PLUS ")
                    previousWasSpace = true
                } else {
                    val normalized = normalizeCharacter(character)
                    if (normalized == null) {
                        if (!previousWasSpace) append(' ')
                        previousWasSpace = true
                    } else {
                        append(normalized)
                        previousWasSpace = false
                    }
                }
            }
        }.trim()

    private fun normalizeCharacter(value: Char): Char? =
        when (value) {
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

/**
 * Kullanıcının belirli bir kanonik alias için verdiği kalıcı kişisel merchant doğrulama/engelleme kararı.
 * Teknik Room ve sync metadata'sı bu domain modeline taşınmaz.
 */
data class UserMerchantVerification(
    val id: EntityId,
    val ownerId: EntityId,
    val workspaceId: EntityId? = null,
    val normalizedAlias: String,
    val decision: UserMerchantDecision,
    val scope: MerchantVerificationScope = MerchantVerificationScope.PERSONAL,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(workspaceId == null) { "C2'de workspaceId null olmalıdır." }
        require(scope == MerchantVerificationScope.PERSONAL) { "C2'de yalnız PERSONAL scope desteklenir." }
        require(normalizedAlias.isNotBlank()) { "normalizedAlias boş olamaz." }
        require(normalizedAlias.length <= Transaction.MAX_DESCRIPTION_LENGTH) {
            "normalizedAlias uzunluğu ${Transaction.MAX_DESCRIPTION_LENGTH} karakteri geçemez."
        }
        val canonical = TurkishDescriptionNormalizer.normalize(normalizedAlias)
        require(normalizedAlias == canonical) {
            "normalizedAlias kanonik normalizasyon biçiminde olmalıdır (beklenen: '$canonical', gelen: '$normalizedAlias')."
        }
        require(normalizedAlias !in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
            "normalizedAlias genel sözcükler kümesinde ($normalizedAlias) yer alamaz."
        }
        when (decision) {
            is UserMerchantDecision.Assign ->
                require(decision.merchantId.isNotBlank()) {
                    "Assign merchantId boş olamaz."
                }
            is UserMerchantDecision.Block -> Unit
        }
    }
}

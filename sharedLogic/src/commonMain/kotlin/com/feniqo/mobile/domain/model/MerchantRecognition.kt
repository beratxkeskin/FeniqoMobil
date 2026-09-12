package com.feniqo.mobile.domain.model

/** Bir merchant'ın farklı banka açıklamalarında görülebilen, önceliklendirilebilir adı. */
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
}

data class Merchant(
    val id: String,
    val displayName: String,
    val aliases: List<MerchantAlias>,
    val negativeAliases: List<MerchantNegativeAlias> = emptyList(),
    val categoryIconKey: CategoryIcon,
    /** Sağlayıcı URL'si değil, daha sonra güvenilir logo kaynağına çözümlenecek kararlı anahtardır. */
    val verifiedLogoKey: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Merchant kimliği boş olamaz." }
        require(displayName.isNotBlank()) { "Merchant adı boş olamaz." }
        require(aliases.isNotEmpty()) { "Merchant en az bir alias taşımalıdır." }
    }
}

data class MerchantRecognitionRequest(
    /** Ham banka açıklaması değiştirilmez ve hiçbir üçüncü tarafa gönderilmez. */
    val rawDescription: String,
    val verifiedMerchantId: String? = null,
    val workspaceVerifiedMerchantId: String? = null,
    val bankVerifiedMerchantId: String? = null,
)

data class MerchantRecognitionResult(
    val rawDescription: String,
    val normalizedDescription: String,
    val transactionClassification: TransactionClassification,
    val merchant: Merchant?,
    val confidence: Int,
    val source: MerchantMatchSource?,
    val status: MerchantMatchStatus,
) {
    init {
        require(confidence in 0..100) { "Merchant güven puanı 0..100 aralığında olmalıdır." }
    }

    val canShowMerchantLogo: Boolean
        get() = merchant != null && confidence >= MIN_LOGO_CONFIDENCE

    val confidenceBand: MerchantConfidenceBand
        get() = when (confidence) {
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

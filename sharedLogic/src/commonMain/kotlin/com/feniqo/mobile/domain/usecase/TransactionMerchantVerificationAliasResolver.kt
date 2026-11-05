package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.DISALLOWED_STANDALONE_GENERIC_TOKENS
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantCorrectionRejectionReason
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionClassification
import com.feniqo.mobile.domain.model.TransactionMerchantCreateDecision
import com.feniqo.mobile.domain.model.TransactionMerchantEligibility
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer

/**
 * Explicit merchant kararları (KnownMerchant ve UseTransactionName) için
 * kişisel doğrulama (verification) alias'ını ve geçerliliğini belirleyen saf çözücü.
 *
 * İş kuralları:
 * - İşlem açıklamasını (rawDescription) doğrular (boşluk, uzunluk, EXCLUDED_TRANSACTION).
 * - UseTransactionName için generic alias (örn. YEMEK, MARKET) kabul edilmez (fail-closed).
 * - KnownMerchant için:
 *   - Açıklama generic değilse, kullanıcının girdiği açıklama alias olarak kullanılır.
 *   - Açıklama generic ise (örn. "Yemek"), genel sözcük üzerine geniş kapsamlı verification
 *     oluşturulmaz; bunun yerine seçilen katalog merchant'ının deterministik, kanonik ve
 *     generic olmayan ilk geçerli alias'ı (örn. "YEMEKSEPETI") seçilir.
 *   - Katalogda güvenli kanonik alias bulunamazsa fail-closed GENERIC_ALIAS_NOT_ALLOWED döner.
 */
object TransactionMerchantVerificationAliasResolver {
    sealed interface Result {
        data class Resolved(
            val normalizedAlias: String,
            val targetMerchant: Merchant?,
        ) : Result

        data class Rejected(
            val reason: MerchantCorrectionRejectionReason,
        ) : Result
    }

    fun resolve(
        rawDescription: String?,
        decision: TransactionMerchantCreateDecision,
        catalog: List<Merchant> = StarterMerchantCatalog.merchants,
    ): Result {
        val trimmedDesc = rawDescription?.trim().orEmpty()
        if (trimmedDesc.isBlank()) {
            return Result.Rejected(MerchantCorrectionRejectionReason.INVALID_DESCRIPTION)
        }

        val normalizedDesc = TurkishDescriptionNormalizer.normalize(trimmedDesc)
        if (normalizedDesc.isBlank()) {
            return Result.Rejected(MerchantCorrectionRejectionReason.INVALID_ALIAS)
        }
        if (normalizedDesc.length > Transaction.MAX_DESCRIPTION_LENGTH) {
            return Result.Rejected(MerchantCorrectionRejectionReason.ALIAS_TOO_LONG)
        }
        if (TransactionMerchantEligibility.classify(trimmedDesc) != TransactionClassification.PURCHASE) {
            return Result.Rejected(MerchantCorrectionRejectionReason.EXCLUDED_TRANSACTION)
        }
        if (TransactionMerchantEligibility.isExcluded(normalizedDesc)) {
            return Result.Rejected(MerchantCorrectionRejectionReason.EXCLUDED_TRANSACTION)
        }

        return when (decision) {
            is TransactionMerchantCreateDecision.Auto -> {
                Result.Rejected(MerchantCorrectionRejectionReason.INVALID_ALIAS)
            }
            is TransactionMerchantCreateDecision.UseTransactionName -> {
                if (normalizedDesc in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
                    Result.Rejected(MerchantCorrectionRejectionReason.GENERIC_ALIAS_NOT_ALLOWED)
                } else {
                    Result.Resolved(
                        normalizedAlias = normalizedDesc,
                        targetMerchant = null,
                    )
                }
            }
            is TransactionMerchantCreateDecision.KnownMerchant -> {
                val cleanId = decision.merchantId.value.trim()
                if (cleanId.isBlank()) {
                    return Result.Rejected(MerchantCorrectionRejectionReason.UNKNOWN_MERCHANT)
                }
                val found =
                    catalog.firstOrNull { it.id == cleanId }
                        ?: return Result.Rejected(MerchantCorrectionRejectionReason.UNKNOWN_MERCHANT)

                if (normalizedDesc !in DISALLOWED_STANDALONE_GENERIC_TOKENS) {
                    Result.Resolved(
                        normalizedAlias = normalizedDesc,
                        targetMerchant = found,
                    )
                } else {
                    val canonicalAlias = resolveCanonicalAlias(found)
                    if (canonicalAlias == null) {
                        Result.Rejected(MerchantCorrectionRejectionReason.GENERIC_ALIAS_NOT_ALLOWED)
                    } else {
                        Result.Resolved(
                            normalizedAlias = canonicalAlias,
                            targetMerchant = found,
                        )
                    }
                }
            }
        }
    }

    /**
     * Bir [Merchant] için katalogdan deterministik, kanonik ve generic olmayan ilk geçerli alias'ı döner.
     */
    fun resolveCanonicalAlias(merchant: Merchant): String? =
        merchant.aliases
            .map { TurkishDescriptionNormalizer.normalize(it.value) }
            .firstOrNull { normalized ->
                normalized.isNotBlank() &&
                    normalized.length <= Transaction.MAX_DESCRIPTION_LENGTH &&
                    normalized !in DISALLOWED_STANDALONE_GENERIC_TOKENS &&
                    !TransactionMerchantEligibility.isExcluded(normalized)
            }
}

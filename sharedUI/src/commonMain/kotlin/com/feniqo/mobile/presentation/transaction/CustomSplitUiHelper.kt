package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.validation.MoneyAmountParser
import com.feniqo.mobile.presentation.util.MoneyFormatter

/**
 * Custom Split UI durum özetidir.
 */
data class CustomSplitSummary(
    val totalAmountMinor: Long?,
    val distributedAmountMinor: Long,
    val remainingAmountMinor: Long?,
    val isBalanced: Boolean,
    val isLongOverflow: Boolean = false,
    val isMoneyMaxExceeded: Boolean = false,
    val hasInvalidShares: Boolean = false,
    val hasInactiveMember: Boolean = false,
    val inactiveMemberMessage: String? = null,
    val isOverflow: Boolean = isLongOverflow || isMoneyMaxExceeded,
    val hasExcess: Boolean = false,
    val canApplyPayerRemainder: Boolean = false,
    val calculatedPayerRemainderMinor: Long? = null,
)

/**
 * Custom Split hesaplama, kalan aktarma ve girdi çözümleme saf UI motorudur.
 */
object CustomSplitUiHelper {

    /**
     * Kullanıcının girdiği pay metnini en küçük para birimine dönüştürür.
     * Ödeyen için 0 pay geçerlidir, ancak diğer katılımcılar için 0 pay geçersizdir.
     * Boş giriş kesinlikle sıfır sayılmaz; hata döner.
     */
    fun parseShare(
        input: String,
        isPayer: Boolean,
        currency: Currency,
    ): Pair<Long?, TransactionFormFieldError?> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return null to TransactionFormFieldError.SPLIT_CUSTOM_SHARE_REQUIRED
        }

        return when (val result = MoneyAmountParser.parseToMinorUnits(trimmed, currency)) {
            is MoneyAmountParser.ParseResult.Success -> {
                result.amountMinor to null
            }
            is MoneyAmountParser.ParseResult.Invalid -> {
                if (result.error == MoneyAmountParser.MoneyParseError.NON_POSITIVE) {
                    if (isPayer) {
                        0L to null
                    } else {
                        0L to TransactionFormFieldError.SPLIT_CUSTOM_NON_PAYER_ZERO_SHARE_NOT_ALLOWED
                    }
                } else when (result.error) {
                    MoneyAmountParser.MoneyParseError.EMPTY -> null to TransactionFormFieldError.SPLIT_CUSTOM_SHARE_REQUIRED
                    MoneyAmountParser.MoneyParseError.EXCESSIVE_DECIMAL_DIGITS,
                    MoneyAmountParser.MoneyParseError.INVALID_FORMAT -> null to TransactionFormFieldError.SPLIT_CUSTOM_SHARE_INVALID
                    MoneyAmountParser.MoneyParseError.MAX_AMOUNT_EXCEEDED -> null to TransactionFormFieldError.SPLIT_CUSTOM_SHARE_TOO_LARGE
                    MoneyAmountParser.MoneyParseError.NON_POSITIVE -> null to TransactionFormFieldError.SPLIT_CUSTOM_SHARE_INVALID
                }
            }
        }
    }

    /**
     * Formdaki toplam tutar ve katılımcı paylarına göre toplam/dağıtılan/kalan özetini Long ve Money.MAX_AMOUNT_MINOR taşma korumasıyla hesaplar.
     * "Tam Eşleşti" yalnız tüm seçili paylar eksiksiz, geçerli ve tüm üyeler aktifken true olur.
     */
    fun calculateSplitSummary(
        amountText: String,
        currency: Currency,
        payer: EntityId?,
        participants: Set<EntityId>,
        customSharesText: Map<EntityId, String>,
        activeMembers: Set<EntityId> = participants + listOfNotNull(payer),
    ): CustomSplitSummary {
        val totalParse = MoneyAmountParser.parseToMinorUnits(amountText, currency)
        val totalMinor = (totalParse as? MoneyAmountParser.ParseResult.Success)?.amountMinor

        var distributed = 0L
        var isLongOverflow = false
        var isMoneyMaxExceeded = false
        var hasInvalidShares = false

        if (payer == null || payer !in participants || participants.isEmpty() || totalMinor == null) {
            hasInvalidShares = true
        }

        val hasInactiveMember = (payer != null && payer !in activeMembers) ||
            participants.any { it !in activeMembers }

        val inactiveMemberMessage = if (hasInactiveMember) {
            if (payer != null && payer !in activeMembers && participants.any { it !in activeMembers }) {
                "Ödeyen ve bazı katılımcılar çalışma alanında aktif üye değil."
            } else if (payer != null && payer !in activeMembers) {
                "Harcamayı ödeyen kişi çalışma alanında aktif üye değil."
            } else {
                "Seçilen bazı katılımcılar çalışma alanında aktif üye değil."
            }
        } else null

        for (p in participants) {
            val text = customSharesText[p]?.trim().orEmpty()
            val isPayer = (p == payer)
            val (minor, error) = parseShare(text, isPayer = isPayer, currency = currency)
            if (minor == null || error != null) {
                hasInvalidShares = true
            } else {
                if (!isPayer && minor == 0L) {
                    hasInvalidShares = true
                }
                if (Long.MAX_VALUE - distributed < minor) {
                    isLongOverflow = true
                    distributed = Long.MAX_VALUE
                } else {
                    distributed += minor
                }
            }
        }

        if (distributed > Money.MAX_AMOUNT_MINOR) {
            isMoneyMaxExceeded = true
        }

        val remaining = if (totalMinor != null && !isLongOverflow) {
            totalMinor - distributed
        } else null

        val hasExcess = remaining != null && remaining < 0L
        val isBalanced = !hasInvalidShares &&
            !hasInactiveMember &&
            !isLongOverflow &&
            !isMoneyMaxExceeded &&
            totalMinor != null &&
            remaining == 0L

        // "Kalan tutarı ödeyene aktar" aksiyonu uygunluk kontrolü
        var canApply = false
        var calculatedPayerRemainder: Long? = null

        if (payer != null && payer in participants && totalMinor != null && totalMinor > 0L && !hasInactiveMember) {
            val otherParticipants = participants - payer
            var othersSum = 0L
            var allOthersValid = true

            for (other in otherParticipants) {
                val text = customSharesText[other]?.trim().orEmpty()
                val (minor, error) = parseShare(text, isPayer = false, currency = currency)
                if (minor == null || error != null || minor <= 0L) {
                    allOthersValid = false
                    break
                }
                if (Long.MAX_VALUE - othersSum < minor) {
                    allOthersValid = false
                    break
                }
                othersSum += minor
            }

            if (allOthersValid && othersSum <= Money.MAX_AMOUNT_MINOR) {
                val rem = totalMinor - othersSum
                if (rem in 0L..Money.MAX_AMOUNT_MINOR) {
                    canApply = true
                    calculatedPayerRemainder = rem
                }
            }
        }

        return CustomSplitSummary(
            totalAmountMinor = totalMinor,
            distributedAmountMinor = distributed,
            remainingAmountMinor = remaining,
            isBalanced = isBalanced,
            isLongOverflow = isLongOverflow,
            isMoneyMaxExceeded = isMoneyMaxExceeded,
            hasInvalidShares = hasInvalidShares,
            hasInactiveMember = hasInactiveMember,
            inactiveMemberMessage = inactiveMemberMessage,
            isOverflow = isLongOverflow || isMoneyMaxExceeded,
            hasExcess = hasExcess,
            canApplyPayerRemainder = canApply,
            calculatedPayerRemainderMinor = calculatedPayerRemainder,
        )
    }

    /**
     * Katılımcı paylarını canlı taslak girdilerinden anlık olarak doğrular.
     * Payer değiştiğinde veya yeni tutar girildiğinde anında hata üretir.
     * Aktif olmayan üyeler anında SPLIT_CUSTOM_MEMBER_NOT_ACTIVE hatası alır.
     */
    fun validateDraftParticipantShares(
        currency: Currency,
        payer: EntityId?,
        participants: Set<EntityId>,
        customSharesText: Map<EntityId, String>,
        priorSubmitErrors: Map<EntityId, TransactionFormFieldError> = emptyMap(),
        activeMembers: Set<EntityId> = participants + listOfNotNull(payer),
    ): Map<EntityId, TransactionFormFieldError> {
        val errors = mutableMapOf<EntityId, TransactionFormFieldError>()
        for (p in participants) {
            if (p !in activeMembers) {
                errors[p] = TransactionFormFieldError.SPLIT_CUSTOM_MEMBER_NOT_ACTIVE
                continue
            }
            val text = customSharesText[p]?.trim().orEmpty()
            val isPayer = (p == payer)
            if (text.isEmpty()) {
                if (priorSubmitErrors.containsKey(p) || priorSubmitErrors.isNotEmpty()) {
                    errors[p] = priorSubmitErrors[p] ?: TransactionFormFieldError.SPLIT_CUSTOM_SHARE_REQUIRED
                }
            } else {
                val (_, error) = parseShare(text, isPayer = isPayer, currency = currency)
                if (error != null) {
                    errors[p] = error
                }
            }
        }
        if (payer != null && payer !in activeMembers) {
            errors[payer] = TransactionFormFieldError.SPLIT_CUSTOM_MEMBER_NOT_ACTIVE
        }
        return errors
    }

    /**
     * "Kalan tutarı ödeyene aktar" eylemini uygular ve güncel pay haritasını döndürür.
     * Payer veya herhangi bir katılımcı aktif değilse değişiklik yapmaz.
     */
    fun applyPayerRemainder(
        amountText: String,
        currency: Currency,
        payer: EntityId?,
        participants: Set<EntityId>,
        customSharesText: Map<EntityId, String>,
        activeMembers: Set<EntityId> = participants + listOfNotNull(payer),
    ): Map<EntityId, String> {
        val summary = calculateSplitSummary(
            amountText = amountText,
            currency = currency,
            payer = payer,
            participants = participants,
            customSharesText = customSharesText,
            activeMembers = activeMembers,
        )
        val remainderMinor = summary.calculatedPayerRemainderMinor
        if (!summary.canApplyPayerRemainder || payer == null || remainderMinor == null) {
            return customSharesText
        }
        val formatted = MoneyFormatter.formatMinorUnitsToInputText(remainderMinor, currency)
        return customSharesText + (payer to formatted)
    }
}

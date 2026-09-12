package com.feniqo.mobile.domain.model

/** Gelir/gider bütçe kategorisi olmayan, para hareketini açıklayan kanonik sistem kategorisi. */
data class SystemTransactionCategoryDefinition(
    val key: CategoryIcon,
    val turkishName: String,
    val englishName: String,
    val iconMeaningKey: String,
    val purpose: String,
)

object SystemTransactionCategoryCatalog {
    val definitions: List<SystemTransactionCategoryDefinition> = listOf(
        definition("account_transfer", "Hesaplar Arası Transfer", "Account Transfer", "bidirectional_arrows", "Kullanıcının kendi hesapları arasındaki hareket"),
        definition("savings_contribution", "Birikime Aktarım", "Savings Contribution", "piggy_bank", "Birikim hesabı veya hedefe aktarılan para"),
        definition("investment_transfer", "Yatırıma Aktarım", "Investment Transfer", "chart_arrow", "Yatırım hesabına aktarılan ana para"),
        definition("credit_card_payment", "Kredi Kartı Ödemesi", "Credit Card Payment", "card_check", "Kart borcu ödeme hareketi"),
        definition("cash_withdrawal", "Nakit Çekim", "Cash Withdrawal", "atm_cash", "Hesaptan nakde dönüşüm"),
        definition("cash_deposit", "Nakit Yatırma", "Cash Deposit", "cash_plus", "Nakit paranın hesaba yatırılması"),
        definition("balance_adjustment", "Bakiye Düzeltmesi", "Balance Adjustment", "sliders", "Başlangıç bakiyesi ve düzeltme işlemleri"),
    )

    private val byKey = definitions.associateBy { it.key.key }

    fun find(key: CategoryIcon): SystemTransactionCategoryDefinition? = byKey[key.key]

    private fun definition(
        key: String,
        turkishName: String,
        englishName: String,
        iconMeaningKey: String,
        purpose: String,
    ) = SystemTransactionCategoryDefinition(
        key = CategoryIcon(key),
        turkishName = turkishName,
        englishName = englishName,
        iconMeaningKey = iconMeaningKey,
        purpose = purpose,
    )
}

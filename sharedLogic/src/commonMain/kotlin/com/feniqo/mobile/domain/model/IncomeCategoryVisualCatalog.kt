package com.feniqo.mobile.domain.model

/** Gelir kategorilerinin platform ikon paketlerinden bağımsız kanonik görsel sözleşmesi. */
data class IncomeCategoryVisualDefinition(
    val key: CategoryIcon,
    val turkishName: String,
    val englishName: String,
    val iconMeaningKey: String,
    val color: CategoryColor,
    val examples: String,
)

object IncomeCategoryVisualCatalog {
    val definitions: List<IncomeCategoryVisualDefinition> = listOf(
        definition("salary", "Maaş", "Salary", "briefcase", "#16A34A", "Düzenli maaş ödemesi"),
        definition("freelance", "Serbest Çalışma", "Freelance", "laptop", "#059669", "Proje ve danışmanlık gelirleri"),
        definition("business_income", "İşletme Geliri", "Business Income", "store", "#0D9488", "Satış ve ticari faaliyet gelirleri"),
        definition("investment_income", "Yatırım Geliri", "Investment Income", "trending_up", "#10B981", "Temettü, faiz ve yatırım kazancı"),
        definition("rental_income", "Kira Geliri", "Rental Income", "key_home", "#22C55E", "Gayrimenkul kira gelirleri"),
        definition("refund_reimbursement", "İade & Geri Ödeme", "Refund & Reimbursement", "return_arrow", "#14B8A6", "Ürün iadesi, masraf geri ödemesi"),
        definition("scholarship_support", "Burs & Destek", "Scholarship & Support", "graduation_cap", "#34D399", "Burs, aile desteği ve yardım"),
        definition("gift_income", "Hediye Geliri", "Gift Income", "gift", "#2DD4BF", "Kişisel para hediyeleri"),
        definition("other_income", "Diğer Gelir", "Other Income", "plus", "#6B7280", "Diğer gelirler"),
    )

    private val byKey = definitions.associateBy { it.key.key }

    fun find(key: CategoryIcon): IncomeCategoryVisualDefinition? = byKey[key.key]

    private fun definition(
        key: String,
        turkishName: String,
        englishName: String,
        iconMeaningKey: String,
        color: String,
        examples: String,
    ) = IncomeCategoryVisualDefinition(
        key = CategoryIcon(key),
        turkishName = turkishName,
        englishName = englishName,
        iconMeaningKey = iconMeaningKey,
        color = CategoryColor(color),
        examples = examples,
    )
}

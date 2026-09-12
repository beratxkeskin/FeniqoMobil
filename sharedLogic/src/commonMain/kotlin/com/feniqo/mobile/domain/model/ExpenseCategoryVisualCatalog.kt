package com.feniqo.mobile.domain.model

/** Gider kategorilerinin platform ikon paketlerinden bağımsız kanonik görsel sözleşmesi. */
data class ExpenseCategoryVisualDefinition(
    val key: CategoryIcon,
    val turkishName: String,
    val englishName: String,
    val iconMeaningKey: String,
    val color: CategoryColor,
    val examples: String,
)

object ExpenseCategoryVisualCatalog {
    val definitions: List<ExpenseCategoryVisualDefinition> = listOf(
        definition("food_dining", "Yeme & İçme", "Food & Dining", "fork_knife", "#F97316", "Restoran, kafe, yemek siparişi"),
        definition("groceries", "Market", "Groceries", "shopping_cart", "#EF4444", "Market, bakkal, temel tüketim"),
        definition("housing", "Ev & Kira", "Housing", "home", "#2563EB", "Kira, aidat, ev bakımı"),
        definition("utilities", "Faturalar", "Utilities", "invoice", "#0D9488", "Elektrik, su, doğal gaz, internet"),
        definition("transportation", "Ulaşım", "Transportation", "car", "#0284C7", "Toplu taşıma, taksi, otopark"),
        definition("fuel", "Akaryakıt", "Fuel", "fuel_pump", "#3B82F6", "Benzin, motorin, şarj istasyonu"),
        definition("healthcare", "Sağlık", "Healthcare", "health", "#E11D48", "Hastane, doktor, ilaç, diş"),
        definition("personal_care", "Kişisel Bakım", "Personal Care", "person_sparkle", "#DB2777", "Kuaför, kozmetik, bakım"),
        definition("shopping", "Alışveriş", "Shopping", "shopping_bag", "#D946EF", "Giyim, elektronik, ev eşyası"),
        definition("entertainment", "Eğlence", "Entertainment", "ticket", "#7C3AED", "Sinema, oyun, etkinlik, hobi"),
        definition("subscriptions", "Abonelikler", "Subscriptions", "recurring", "#6366F1", "Netflix, Spotify, dijital üyelikler"),
        definition("education", "Eğitim", "Education", "book", "#8B5CF6", "Kurs, okul, kitap, eğitim yazılımı"),
        definition("travel", "Seyahat", "Travel", "airplane", "#0891B2", "Uçak, otel, tatil harcamaları"),
        definition("family_pets", "Aile & Evcil Hayvan", "Family & Pets", "heart_paw", "#EA580C", "Çocuk, veteriner, evcil hayvan"),
        definition("financial_expenses", "Borç & Finansman", "Debt & Finance", "percent", "#475569", "Kredi faizi, kredi ödemesi, finansman"),
        definition("taxes_fees", "Vergi & Ücretler", "Taxes & Fees", "official_document", "#64748B", "Vergi, banka komisyonu, ceza"),
        definition("gifts_donations", "Hediye & Bağış", "Gifts & Donations", "gift", "#C026D3", "Hediyeler, yardım ve bağışlar"),
        definition("other_expense", "Diğer Gider", "Other Expense", "ellipsis", "#6B7280", "Başka kategoriye uymayan giderler"),
    )

    private val byKey = definitions.associateBy { it.key.key }

    fun find(key: CategoryIcon): ExpenseCategoryVisualDefinition? = byKey[key.key]

    private fun definition(
        key: String,
        turkishName: String,
        englishName: String,
        iconMeaningKey: String,
        color: String,
        examples: String,
    ) = ExpenseCategoryVisualDefinition(
        key = CategoryIcon(key),
        turkishName = turkishName,
        englishName = englishName,
        iconMeaningKey = iconMeaningKey,
        color = CategoryColor(color),
        examples = examples,
    )
}

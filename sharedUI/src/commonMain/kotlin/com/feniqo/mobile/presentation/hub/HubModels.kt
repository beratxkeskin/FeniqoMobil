package com.feniqo.mobile.presentation.hub

/**
 * Hub menü öğelerinin erişilebilirlik durumu.
 */
enum class HubItemStatus {
    AVAILABLE,
    COMING_SOON,
}

/**
 * Hub ekranlarındaki modül bağlantılarını ve durumlarını temsil eden saf veri modeli.
 */
data class HubMenuItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconSymbol: String,
    val iconKey: String? = null,
    val status: HubItemStatus = HubItemStatus.AVAILABLE,
) {
    val isAvailable: Boolean get() = status == HubItemStatus.AVAILABLE
}

/**
 * Plan sekmesi altındaki modül kayıt defteri.
 */
object PlanHubRegistry {
    val BUDGETS = HubMenuItem(
        id = "budgets",
        title = "Bütçeler",
        subtitle = "Kategori bazlı aylık bütçe ve harcama limitleri",
        iconSymbol = "▥",
        iconKey = "budgets",
        status = HubItemStatus.AVAILABLE,
    )

    val RECURRING_TRANSACTIONS = HubMenuItem(
        id = "recurring_transactions",
        title = "Tekrarlayan İşlemler",
        subtitle = "Kira, maaş gibi otomatik tekrarlanan işlemler",
        iconSymbol = "↻",
        iconKey = "recurring_transactions",
        status = HubItemStatus.AVAILABLE,
    )

    val SUBSCRIPTIONS = HubMenuItem(
        id = "subscriptions",
        title = "Abonelikler",
        subtitle = "Dijital servisler ve düzenli abonelik ödemeleri",
        iconSymbol = "▰",
        iconKey = "subscriptions",
        status = HubItemStatus.AVAILABLE,
    )

    val GOALS = HubMenuItem(
        id = "goals",
        title = "Hedefler",
        subtitle = "Birikim ve tasarruf hedefleri takibi",
        iconSymbol = "◎",
        iconKey = "goals",
        status = HubItemStatus.AVAILABLE,
    )

    val DEBTS = HubMenuItem(
        id = "debts",
        title = "Borç / Alacak",
        subtitle = "Kişi ve kurum bazlı borç ve alacak takibi",
        iconSymbol = "⇄",
        iconKey = "debts",
        status = HubItemStatus.AVAILABLE,
    )


    val items: List<HubMenuItem> = listOf(
        BUDGETS,
        RECURRING_TRANSACTIONS,
        SUBSCRIPTIONS,
        GOALS,
        DEBTS,
    )

    val availableItems: List<HubMenuItem> get() = items.filter { it.isAvailable }
    val comingSoonItems: List<HubMenuItem> get() = items.filter { !it.isAvailable }
}

/**
 * Daha Fazla sekmesi altındaki modül kayıt defteri.
 */
object MoreHubRegistry {
    val ASSETS = HubMenuItem(
        id = "assets",
        title = "Varlıklar",
        subtitle = "Banka, nakit ve yatırım varlıkları",
        iconSymbol = "A",
        status = HubItemStatus.AVAILABLE,
    )

    val GOALS = HubMenuItem(
        id = "goals",
        title = "Hedefler",
        subtitle = "Birikim ve tasarruf hedefleri",
        iconSymbol = "H",
        status = HubItemStatus.AVAILABLE,
    )

    val DEBTS = HubMenuItem(
        id = "debts",
        title = "Borç ve Alacaklar",
        subtitle = "Borç, alacak ve ödeme takibi",
        iconSymbol = "B",
        status = HubItemStatus.AVAILABLE,
    )

    val SUBSCRIPTIONS = HubMenuItem(
        id = "subscriptions",
        title = "Abonelikler",
        subtitle = "Düzenli abonelik ödemeleri",
        iconSymbol = "S",
        status = HubItemStatus.AVAILABLE,
    )

    val RECURRING_TRANSACTIONS = HubMenuItem(
        id = "recurring_transactions",
        title = "Tekrarlayan İşlemler",
        subtitle = "Planlı gelir ve gider kuralları",
        iconSymbol = "T",
        status = HubItemStatus.AVAILABLE,
    )

    val CATEGORIES = HubMenuItem(
        id = "categories",
        title = "Kategoriler",
        subtitle = "Gelir ve gider kategorilerini düzenle",
        iconSymbol = "K",
        status = HubItemStatus.AVAILABLE,
    )

    val REPORTS = HubMenuItem(
        id = "reports",
        title = "Raporlar",
        subtitle = "Aylık ve yıllık detaylı finansal analizler",
        iconSymbol = "R",
        status = HubItemStatus.COMING_SOON,
    )

    val SHARED_SPACES = HubMenuItem(
        id = "shared_spaces",
        title = "Ortak Alanlar",
        subtitle = "Aile ve ekip paylaşımlı bütçe takibi",
        iconSymbol = "O",
        status = HubItemStatus.AVAILABLE,
    )

    val SETTINGS = HubMenuItem(
        id = "settings",
        title = "Uygulama Ayarları",
        subtitle = "Görünüm, güvenlik ve veri araçları",
        iconSymbol = "A",
        status = HubItemStatus.AVAILABLE,
    )

    val wealthItems = listOf(ASSETS, GOALS, DEBTS)
    val moneyManagementItems = listOf(SUBSCRIPTIONS, RECURRING_TRANSACTIONS, CATEGORIES)
    val collaborationItems = listOf(SHARED_SPACES)
    val insightItems = listOf(REPORTS)
    val settingsItems = listOf(SETTINGS)

    val items: List<HubMenuItem> =
        wealthItems + moneyManagementItems + collaborationItems + insightItems + settingsItems

    val availableItems: List<HubMenuItem> get() = items.filter { it.isAvailable }
    val comingSoonItems: List<HubMenuItem> get() = items.filter { !it.isAvailable }
}

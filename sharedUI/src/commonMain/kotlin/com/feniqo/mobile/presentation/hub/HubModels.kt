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
        status = HubItemStatus.AVAILABLE,
    )

    val RECURRING_TRANSACTIONS = HubMenuItem(
        id = "recurring_transactions",
        title = "Tekrarlayan İşlemler",
        subtitle = "Kira, maaş gibi otomatik tekrarlanan işlemler",
        iconSymbol = "↻",
        status = HubItemStatus.AVAILABLE,
    )

    val SUBSCRIPTIONS = HubMenuItem(
        id = "subscriptions",
        title = "Abonelikler",
        subtitle = "Dijital servisler ve düzenli abonelik ödemeleri",
        iconSymbol = "▰",
        status = HubItemStatus.AVAILABLE,
    )

    val GOALS = HubMenuItem(
        id = "goals",
        title = "Hedefler",
        subtitle = "Birikim ve tasarruf hedefleri takibi",
        iconSymbol = "◎",
        status = HubItemStatus.AVAILABLE,
    )

    val DEBTS = HubMenuItem(
        id = "debts",
        title = "Borç / Alacak",
        subtitle = "Kişi ve kurum bazlı borç ve alacak takibi",
        iconSymbol = "⇄",
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
    val CATEGORIES = HubMenuItem(
        id = "categories",
        title = "Kategoriler",
        subtitle = "Gelir ve gider kategorilerini düzenle",
        iconSymbol = "◇",
        status = HubItemStatus.AVAILABLE,
    )

    val SETTINGS = HubMenuItem(
        id = "settings",
        title = "Ayarlar",
        subtitle = "Görünüm, tema ve uygulama tercihleri",
        iconSymbol = "⚙",
        status = HubItemStatus.AVAILABLE,
    )

    val ASSETS = HubMenuItem(
        id = "assets",
        title = "Varlıklar",
        subtitle = "Banka, nakit ve yatırım varlıkları",
        iconSymbol = "◈",
        status = HubItemStatus.COMING_SOON,
    )

    val REPORTS = HubMenuItem(
        id = "reports",
        title = "Raporlar",
        subtitle = "Aylık ve yıllık detaylı finansal analizler",
        iconSymbol = "⌁",
        status = HubItemStatus.COMING_SOON,
    )

    val SHARED_SPACES = HubMenuItem(
        id = "shared_spaces",
        title = "Ortak Alanlar",
        subtitle = "Aile ve ekip paylaşımlı bütçe takibi",
        iconSymbol = "◉",
        status = HubItemStatus.COMING_SOON,
    )

    val BANKS = HubMenuItem(
        id = "banks",
        title = "Bankalar",
        subtitle = "Otomatik banka ve kart entegrasyonları",
        iconSymbol = "▤",
        status = HubItemStatus.COMING_SOON,
    )

    val NOTIFICATIONS = HubMenuItem(
        id = "notifications",
        title = "Bildirimler",
        subtitle = "Ödeme hatırlatıcıları ve bütçe uyarıları",
        iconSymbol = "♢",
        status = HubItemStatus.COMING_SOON,
    )

    val items: List<HubMenuItem> = listOf(
        CATEGORIES,
        SETTINGS,
        ASSETS,
        REPORTS,
        SHARED_SPACES,
        BANKS,
        NOTIFICATIONS,
    )

    val availableItems: List<HubMenuItem> get() = items.filter { it.isAvailable }
    val comingSoonItems: List<HubMenuItem> get() = items.filter { !it.isAvailable }
}

package com.feniqo.mobile.domain.usecase

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.TransactionType

/**
 * İşlem kategorisine duyarlı, saf ve deterministik merchant aday çözücüsü.
 *
 * Sözleşme kuralları:
 * - Kategori adını (lokalize metin) asla okumaz; kararı yalnız tipli [TransactionType] ve
 *   semantik [CategoryIcon] anahtarından [MerchantCategoryRecommendationIndex] üzerinden verir.
 * - Yalnız [TransactionType.EXPENSE] işlemleri için aday üretir; [TransactionType.INCOME]
 *   her zaman boş liste döner.
 * - Null, boş, bilinmeyen veya merchantsız kategoriler için boş liste döner.
 * - Tüm aday çözümleri ve uyumluluk kontrolleri için tek doğruluk kaynağı [MerchantCategoryRecommendationIndex]'tir.
 * - Paralel eşleme tablosu veya merchant nesnesi üzerindeki categoryIconKey'e güvenen kontrol içermez.
 */
class CategoryMerchantCandidateResolver {
    /**
     * Tipli transaction type ve semantik ikon anahtarına göre önerilen merchant adaylarını döner (en fazla 8).
     */
    fun resolveSuggestedCandidates(
        transactionType: TransactionType,
        categoryIconKey: String?,
    ): List<Merchant> =
        MerchantCategoryRecommendationIndex.resolveSuggestedMerchants(
            transactionType = transactionType,
            categoryIconKey = categoryIconKey,
        )

    fun resolveSuggestedCandidates(
        transactionType: TransactionType,
        categoryIcon: CategoryIcon?,
    ): List<Merchant> =
        resolveSuggestedCandidates(
            transactionType = transactionType,
            categoryIconKey = categoryIcon?.key,
        )

    fun resolveSuggestedCandidates(category: Category?): List<Merchant> =
        if (category == null) {
            emptyList()
        } else {
            resolveSuggestedCandidates(
                transactionType = category.type,
                categoryIconKey = category.icon?.key,
            )
        }

    /**
     * Tipli transaction type ve semantik ikon anahtarına göre aranabilir merchant adaylarını döner.
     */
    fun resolveSearchableCandidates(
        transactionType: TransactionType,
        categoryIconKey: String?,
    ): List<Merchant> =
        MerchantCategoryRecommendationIndex.resolveSearchableMerchants(
            transactionType = transactionType,
            categoryIconKey = categoryIconKey,
        )

    fun resolveSearchableCandidates(
        transactionType: TransactionType,
        categoryIcon: CategoryIcon?,
    ): List<Merchant> =
        resolveSearchableCandidates(
            transactionType = transactionType,
            categoryIconKey = categoryIcon?.key,
        )

    fun resolveSearchableCandidates(category: Category?): List<Merchant> =
        if (category == null) {
            emptyList()
        } else {
            resolveSearchableCandidates(
                transactionType = category.type,
                categoryIconKey = category.icon?.key,
            )
        }

    /**
     * Geriye uyumluluk için aranabilir adaylara delege eder.
     */
    fun resolveCandidates(
        transactionType: TransactionType,
        categoryIconKey: String?,
    ): List<Merchant> =
        resolveSearchableCandidates(
            transactionType = transactionType,
            categoryIconKey = categoryIconKey,
        )

    fun resolveCandidates(
        transactionType: TransactionType,
        categoryIcon: CategoryIcon?,
    ): List<Merchant> =
        resolveSearchableCandidates(
            transactionType = transactionType,
            categoryIcon = categoryIcon,
        )

    fun resolveCandidates(category: Category?): List<Merchant> = resolveSearchableCandidates(category)

    /**
     * Kategori ile merchant arasındaki semantik uyumluluğu tek merkezden doğrular.
     */
    fun isCompatible(
        categoryIconKey: String?,
        merchant: Merchant?,
    ): Boolean = Companion.isCompatible(categoryIconKey, merchant)

    fun isCompatible(
        categoryIcon: CategoryIcon?,
        merchant: Merchant?,
    ): Boolean = Companion.isCompatible(categoryIcon, merchant)

    fun isCompatible(
        category: Category?,
        merchant: Merchant?,
    ): Boolean = Companion.isCompatible(category, merchant)

    companion object {
        private val DEFAULT = CategoryMerchantCandidateResolver()

        fun resolveSuggestedCandidates(
            transactionType: TransactionType,
            categoryIconKey: String?,
        ): List<Merchant> = DEFAULT.resolveSuggestedCandidates(transactionType, categoryIconKey)

        fun resolveSuggestedCandidates(
            transactionType: TransactionType,
            categoryIcon: CategoryIcon?,
        ): List<Merchant> = DEFAULT.resolveSuggestedCandidates(transactionType, categoryIcon)

        fun resolveSuggestedCandidates(category: Category?): List<Merchant> = DEFAULT.resolveSuggestedCandidates(category)

        fun resolveSearchableCandidates(
            transactionType: TransactionType,
            categoryIconKey: String?,
        ): List<Merchant> = DEFAULT.resolveSearchableCandidates(transactionType, categoryIconKey)

        fun resolveSearchableCandidates(
            transactionType: TransactionType,
            categoryIcon: CategoryIcon?,
        ): List<Merchant> = DEFAULT.resolveSearchableCandidates(transactionType, categoryIcon)

        fun resolveSearchableCandidates(category: Category?): List<Merchant> = DEFAULT.resolveSearchableCandidates(category)

        fun resolveCandidates(
            transactionType: TransactionType,
            categoryIconKey: String?,
        ): List<Merchant> = DEFAULT.resolveCandidates(transactionType, categoryIconKey)

        fun resolveCandidates(
            transactionType: TransactionType,
            categoryIcon: CategoryIcon?,
        ): List<Merchant> = DEFAULT.resolveCandidates(transactionType, categoryIcon)

        fun resolveCandidates(category: Category?): List<Merchant> = DEFAULT.resolveCandidates(category)

        /**
         * Kategori ile merchant arasındaki semantik uyumluluğu tek merkezden doğrular.
         *
         * Kurallar:
         * - Null, boş veya bilinmeyen girdilerde fail-closed false döner.
         * - [merchant] stable ID'si [StarterMerchantCatalog.merchants] içinde yoksa false döner.
         * - Çağıranın merchant nesnesi üzerindeki categoryIconKey yerine [StarterMerchantCatalog]
         *   içindeki kanonik ID ve [MerchantCategoryRecommendationIndex] searchable profili esas alınır.
         * - Aynı merchant birden fazla kategori profilinde bulunabilir (örn. THY hem travel hem transportation).
         * - Localized kategori adlarını asla okumaz.
         */
        fun isCompatible(
            categoryIconKey: String?,
            merchant: Merchant?,
        ): Boolean {
            if (merchant == null) return false
            val cleanId = merchant.id.trim()
            if (cleanId.isEmpty()) return false
            val canonicalMerchant =
                StarterMerchantCatalog.merchants.firstOrNull { it.id == cleanId } ?: return false
            val profile =
                MerchantCategoryRecommendationIndex.getProfile(
                    transactionType = TransactionType.EXPENSE,
                    categoryIconKey = categoryIconKey,
                ) ?: return false
            return profile.searchableMerchantIds.any { it.value == canonicalMerchant.id }
        }

        fun isCompatible(
            categoryIcon: CategoryIcon?,
            merchant: Merchant?,
        ): Boolean = isCompatible(categoryIcon?.key, merchant)

        fun isCompatible(
            category: Category?,
            merchant: Merchant?,
        ): Boolean =
            if (category == null || category.type != TransactionType.EXPENSE) {
                false
            } else {
                isCompatible(category.icon?.key, merchant)
            }
    }
}

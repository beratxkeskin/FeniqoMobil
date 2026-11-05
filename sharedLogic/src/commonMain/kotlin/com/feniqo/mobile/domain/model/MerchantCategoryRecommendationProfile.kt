package com.feniqo.mobile.domain.model

/**
 * Belirli bir gider kategorisine ait sıralı önerilen ve aranabilir merchant kimlikleri profili.
 *
 * Değişmez sözleşme kuralları:
 * - Kategori kimliği olarak localized ad değil, yalnız kararlı [CategoryIcon] semantik anahtarı kullanılır.
 * - [categoryIconKey] [ExpenseCategoryVisualCatalog] içinde tanımlı kararlı bir gider kategorisi olmalıdır.
 * - [suggestedMerchantIds] listesinde en fazla [MAX_SUGGESTED_COUNT] (8) kayıt bulunabilir.
 * - [suggestedMerchantIds] ve [searchableMerchantIds] listelerinde mükerrer kimlik bulunamaz.
 * - [suggestedMerchantIds], [searchableMerchantIds] listesinin bir alt kümesi olmalıdır.
 * - İndeks saf ve deterministiktir; otomatik seçim veya durum mutasyonu üretmez.
 */
data class MerchantCategoryRecommendationProfile(
    val categoryIconKey: CategoryIcon,
    val suggestedMerchantIds: List<EntityId>,
    val searchableMerchantIds: List<EntityId>,
) {
    init {
        require(categoryIconKey.key.isNotBlank()) {
            "categoryIconKey boş olamaz."
        }
        require(ExpenseCategoryVisualCatalog.find(categoryIconKey) != null) {
            "Bilinmeyen kategori ikon anahtarı: ${categoryIconKey.key}"
        }
        require(suggestedMerchantIds.size <= MAX_SUGGESTED_COUNT) {
            "Önerilen merchant sayısı en fazla $MAX_SUGGESTED_COUNT olabilir (mevcut: ${suggestedMerchantIds.size})."
        }
        val suggestedDistinct = suggestedMerchantIds.distinct()
        require(suggestedDistinct.size == suggestedMerchantIds.size) {
            "suggestedMerchantIds mükerrer ID içeremez: $suggestedMerchantIds"
        }
        val searchableDistinct = searchableMerchantIds.distinct()
        require(searchableDistinct.size == searchableMerchantIds.size) {
            "searchableMerchantIds mükerrer ID içeremez: $searchableMerchantIds"
        }
        val searchableSet = searchableMerchantIds.toSet()
        require(suggestedMerchantIds.all { it in searchableSet }) {
            "suggestedMerchantIds, searchableMerchantIds listesinin alt kümesi olmalıdır."
        }
    }

    val hasSuggested: Boolean get() = suggestedMerchantIds.isNotEmpty()

    val hasSearchable: Boolean get() = searchableMerchantIds.isNotEmpty()

    companion object {
        const val MAX_SUGGESTED_COUNT: Int = 8

        fun empty(categoryIconKey: CategoryIcon): MerchantCategoryRecommendationProfile =
            MerchantCategoryRecommendationProfile(
                categoryIconKey = categoryIconKey,
                suggestedMerchantIds = emptyList(),
                searchableMerchantIds = emptyList(),
            )
    }
}

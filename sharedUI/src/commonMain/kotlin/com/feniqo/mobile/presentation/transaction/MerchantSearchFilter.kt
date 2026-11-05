package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.TurkishDescriptionNormalizer
import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog

/**
 * Merchant seçici için saf ve deterministik katalog arama/filtreleme yardımcısıdır.
 * Stable merchantId ile çalışır; görünür ad veya yerelleştirilmiş metin kimlik olarak kullanılmaz.
 */
object MerchantSearchFilter {

    private val merchantComparator = Comparator<Merchant> { m1, m2 ->
        val norm1 = TurkishDescriptionNormalizer.normalize(m1.displayName)
        val norm2 = TurkishDescriptionNormalizer.normalize(m2.displayName)
        val nameComparison = norm1.compareTo(norm2)
        if (nameComparison != 0) {
            nameComparison
        } else {
            m1.id.compareTo(m2.id)
        }
    }

    fun filter(
        query: String,
        catalog: List<Merchant> = StarterMerchantCatalog.merchants,
    ): List<MerchantSelectorItem> {
        val cleanQuery = query.trim()
        val filtered = if (cleanQuery.isEmpty()) {
            catalog
        } else {
            val normalizedQuery = TurkishDescriptionNormalizer.normalize(cleanQuery).lowercase()
            catalog.filter { merchant ->
                val normalizedDisplayName = TurkishDescriptionNormalizer.normalize(merchant.displayName).lowercase()
                normalizedDisplayName.contains(normalizedQuery) ||
                    merchant.aliases.any { alias ->
                        TurkishDescriptionNormalizer.normalize(alias.value).lowercase().contains(normalizedQuery)
                    }
            }
        }

        return filtered.sortedWith(merchantComparator).map { merchant ->
            MerchantSelectorItem(
                id = EntityId(merchant.id),
                displayName = merchant.displayName,
                categoryIconKey = merchant.categoryIconKey.key,
                verifiedLogoKey = merchant.verifiedLogoKey,
            )
        }
    }

    private val selectorItemComparator = Comparator<MerchantSelectorItem> { i1, i2 ->
        val norm1 = TurkishDescriptionNormalizer.normalize(i1.displayName)
        val norm2 = TurkishDescriptionNormalizer.normalize(i2.displayName)
        val nameComparison = norm1.compareTo(norm2)
        if (nameComparison != 0) {
            nameComparison
        } else {
            i1.id.value.compareTo(i2.id.value)
        }
    }

    fun filterCandidates(
        query: String,
        candidates: List<MerchantSelectorItem>,
    ): List<MerchantSelectorItem> {
        val cleanQuery = query.trim()
        val filtered = if (cleanQuery.isEmpty()) {
            candidates
        } else {
            val normalizedQuery = TurkishDescriptionNormalizer.normalize(cleanQuery).lowercase()
            candidates.filter { item ->
                val normalizedDisplayName = TurkishDescriptionNormalizer.normalize(item.displayName).lowercase()
                normalizedDisplayName.contains(normalizedQuery)
            }
        }

        return filtered.sortedWith(selectorItemComparator)
    }
}

package com.feniqo.mobile.domain.model

/**
 * Yeni kategori bütçesi oluşturma komutu.
 * UI'dan yalnızca kullanıcı tarafından belirlenen değerleri taşır;
 * oturum (ownerId), workspaceId veya senkronizasyon metadata'sı repository katmanında atanır.
 */
data class CreateBudgetCommand(
    val categoryId: EntityId,
    val month: YearMonth,
    val limit: Money,
)

/**
 * Mevcut bütçe limitini güncelleme komutu.
 */
data class UpdateBudgetCommand(
    val id: EntityId,
    val limit: Money,
)

/**
 * Bütçe silme (soft-delete) komutu.
 */
data class DeleteBudgetCommand(
    val id: EntityId,
)

/**
 * Kaynak aydaki bütçeleri hedef aya kopyalama komutu.
 */
data class CopyBudgetsCommand(
    val sourceMonth: YearMonth,
    val targetMonth: YearMonth,
) {
    init {
        require(sourceMonth != targetMonth) { "Kaynak ve hedef ay aynı olamaz." }
    }
}

/**
 * Bütçe kopyalama işleminin tipli sonuç özeti.
 */
data class CopyBudgetsResult(
    val copiedCount: Int,
    val skippedCategoryIds: List<EntityId> = emptyList(),
) {
    val skippedCount: Int get() = skippedCategoryIds.size

    init {
        require(copiedCount >= 0) { "Kopyalanan bütçe sayısı negatif olamaz: $copiedCount" }
    }
}

package com.feniqo.mobile.data.local.database

import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.SyncMetadata
import com.feniqo.mobile.domain.model.SyncStatus
import com.feniqo.mobile.domain.model.ExpenseCategoryVisualCatalog
import com.feniqo.mobile.domain.model.IncomeCategoryVisualCatalog

/** Canonical sistem kategorilerini ağdan bağımsız olarak Room'da hazır tutar. */
class DefaultCategorySeeder(
    private val remoteSyncDao: RemoteSyncDao,
    private val nowEpochMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun seed() {
        val now = nowEpochMillis()
        val sync = SyncMetadata(
            syncStatus = SyncStatus.SYNCED.name,
            updatedAtEpochMillis = 0L,
            localUpdatedAtEpochMillis = now,
            deletedAtEpochMillis = null,
            version = 1L,
            baseVersion = 1L,
            lastSyncError = null,
        )
        remoteSyncDao.ensureDefaultCategoryRows(
            DEFAULT_CATEGORIES.map { category ->
                CategoryEntity(
                    id = category.id,
                    ownerId = null,
                    workspaceId = null,
                    scopeKey = DEFAULT_SCOPE_KEY,
                    name = category.name,
                    normalizedName = category.normalizedName,
                    slug = category.slug,
                    typeCode = category.typeCode,
                    colorHex = category.colorHex,
                    iconKey = category.iconKey,
                    isDefault = true,
                    createdAtEpochMillis = 0L,
                    sync = sync,
                )
            },
        )
        remoteSyncDao.hideLegacyDefaultCategoryRows(LEGACY_AMBIGUOUS_CATEGORY_IDS, now)
    }

    private data class DefaultCategory(
        val id: String,
        val name: String,
        val normalizedName: String,
        val slug: String,
        val typeCode: String,
        val colorHex: String,
        val iconKey: String,
    )

    private companion object {
        const val DEFAULT_SCOPE_KEY = "system"

        val INCOME_IDS = listOf(101, 102, 106, 104, 107, 108, 103, 109, 105)
        val EXPENSE_IDS = listOf(111, 112, 114, 115, 113, 123, 118, 124, 125, 116, 119, 117, 126, 127, 128, 129, 130, 122)

        val DEFAULT_CATEGORIES =
            IncomeCategoryVisualCatalog.definitions.zip(INCOME_IDS).map { (visual, suffix) ->
                definition(
                    suffix = suffix,
                    name = visual.turkishName,
                    key = visual.key.key,
                    typeCode = "INCOME",
                    colorHex = visual.color.hex,
                )
            } + ExpenseCategoryVisualCatalog.definitions.zip(EXPENSE_IDS).map { (visual, suffix) ->
                definition(
                    suffix = suffix,
                    name = visual.turkishName,
                    key = visual.key.key,
                    typeCode = "EXPENSE",
                    colorHex = visual.color.hex,
                )
            }

        val LEGACY_AMBIGUOUS_CATEGORY_IDS = listOf(
            "11111111-1111-4111-8111-111111111120",
            "11111111-1111-4111-8111-111111111121",
        )

        fun definition(suffix: Int, name: String, key: String, typeCode: String, colorHex: String) = DefaultCategory(
            id = "11111111-1111-4111-8111-${suffix.toString().padStart(12, '1')}",
            name = name,
            normalizedName = name.lowercase(),
            slug = key.replace('_', '-'),
            typeCode = typeCode,
            colorHex = colorHex,
            iconKey = key,
        )
    }
}

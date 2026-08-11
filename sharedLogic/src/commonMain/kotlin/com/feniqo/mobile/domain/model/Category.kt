package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

enum class TransactionType {
    INCOME,
    EXPENSE,
}

/** Platform ikonuna değil, Android ve iOS'un ayrı ayrı eşleyeceği semantik anahtara dayanır. */
data class CategoryIcon(val key: String) {
    init {
        require(key.isNotBlank()) { "Kategori ikon anahtarı boş olamaz." }
    }
}

data class CategoryColor(val hex: String) {
    init {
        require(HEX_COLOR.matches(hex)) { "Kategori rengi #RRGGBB biçiminde olmalıdır." }
    }

    private companion object {
        val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    }
}

/** Sistem ve kullanıcı kategorilerinin ortak, senkronizasyondan bağımsız iş modeli. */
data class Category(
    val id: EntityId,
    val ownerId: EntityId?,
    val workspaceId: EntityId?,
    val name: String,
    val type: TransactionType,
    val color: CategoryColor,
    val icon: CategoryIcon?,
    val isDefault: Boolean,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Kategori adı boş olamaz." }
        require(!isDefault || ownerId == null) {
            "Sistem varsayılan kategorisinin kullanıcı sahibi olamaz."
        }
    }
}

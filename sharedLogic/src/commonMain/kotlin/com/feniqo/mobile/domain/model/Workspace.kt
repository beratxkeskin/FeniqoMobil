package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

/** Ortak çalışma alanındaki tek rol sözlüğü; yetki kontrolünün temelidir. */
enum class WorkspaceRole {
    OWNER,
    EDITOR,
    VIEWER,
}

/** V2 ortak finans kayıtlarının ait olacağı çalışma alanı. */
data class Workspace(
    val id: EntityId,
    val name: String,
    val ownerId: EntityId,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Çalışma alanı adı boş olamaz." }
    }
}

/** Bir kullanıcının çalışma alanındaki rolünü temsil eden ilişki modeli. */
data class WorkspaceMember(
    val workspaceId: EntityId,
    val userId: EntityId,
    val role: WorkspaceRole,
    val joinedAt: Instant,
)

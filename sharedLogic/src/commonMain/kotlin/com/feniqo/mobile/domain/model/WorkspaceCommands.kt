package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

/**
 * Çalışma alanı türü: Kişisel veya Ortak.
 */
enum class WorkspaceType {
    PERSONAL,
    SHARED,
}

/**
 * Yeni çalışma alanı oluşturma komutu / taslağı.
 * UI'dan kullanıcı girdilerini taşır; id, ownerId ve createdAt repository katmanında atanır.
 */
data class CreateWorkspaceCommand(
    val name: String,
    val type: WorkspaceType = WorkspaceType.SHARED,
    val currency: Currency = Currency.TRY,
    val description: String? = null,
)

/**
 * Mevcut çalışma alanını güncelleme komutu.
 */
data class UpdateWorkspaceCommand(
    val id: EntityId,
    val name: String,
    val type: WorkspaceType,
    val currency: Currency,
    val description: String? = null,
)

/**
 * Güvenli davet metadata modeli.
 * Düz davet kodunu (plaintext token/code) taşımaz; hash ve metadata sunucuda tutulur.
 * Davet rolü OWNER olamaz; sahiplik devri ayrı bir iş akışıdır.
 */
data class WorkspaceInvitation(
    val id: EntityId,
    val workspaceId: EntityId,
    val inviterId: EntityId,
    val role: WorkspaceRole,
    val createdAt: Instant,
    val expiresAt: Instant,
    val maxUses: Int,
    val usesCount: Int,
) {
    init {
        require(role != WorkspaceRole.OWNER) { "Davet rolü OWNER olamaz; sahiplik devri ayrı bir iş akışıdır." }
        require(maxUses > 0) { "Maksimum kullanım sayısı pozitif olmalıdır." }
        require(usesCount >= 0) { "Kullanım sayısı negatif olamaz." }
        require(usesCount <= maxUses) { "Kullanım sayısı maksimum kullanım sayısını aşamaz." }
        require(expiresAt > createdAt) { "Son kullanım tarihi oluşturulma tarihinden sonra olmalıdır." }
    }
}

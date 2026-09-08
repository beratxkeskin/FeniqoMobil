package com.feniqo.mobile.data.util

/**
 * WORKSPACE_MEMBER outbox `entity_id` için tekil ve deterministik canonical kodlama yardımcıları.
 *
 * Sözleşme:
 * - Format: `<workspaceId>:<userId>`
 * - Her iki bileşen de non-blank canonical UUID formatında olmalıdır.
 */
object WorkspaceMemberEntityId {

    const val SEPARATOR = ":"
    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * `workspaceId` ve `userId` bileşenlerini canonical `workspaceId:userId` dizesine dönüştürür.
     */
    fun encode(workspaceId: String, userId: String): String {
        val cleanWorkspaceId = workspaceId.trim()
        val cleanUserId = userId.trim()
        require(cleanWorkspaceId.isNotEmpty() && UUID_REGEX.matches(cleanWorkspaceId)) {
            "Geçersiz workspaceId formatı: '$workspaceId'"
        }
        require(cleanUserId.isNotEmpty() && UUID_REGEX.matches(cleanUserId)) {
            "Geçersiz userId formatı: '$userId'"
        }
        return "$cleanWorkspaceId$SEPARATOR$cleanUserId"
    }

    /**
     * Canonical `workspaceId:userId` dizesini ayrıştırır ve bileşenleri doğrular.
     */
    fun decode(entityId: String): Pair<String, String> {
        val parts = entityId.split(SEPARATOR)
        require(parts.size == 2) {
            "Geçersiz WORKSPACE_MEMBER entity_id formatı. Beklenen: 'workspaceId:userId', alınan: '$entityId'"
        }
        val workspaceId = parts[0].trim()
        val userId = parts[1].trim()
        require(workspaceId.isNotEmpty() && UUID_REGEX.matches(workspaceId)) {
            "Geçersiz workspaceId formatı: '$workspaceId' ($entityId)"
        }
        require(userId.isNotEmpty() && UUID_REGEX.matches(userId)) {
            "Geçersiz userId formatı: '$userId' ($entityId)"
        }
        return workspaceId to userId
    }

    /**
     * Verilen `entityId` değerinin geçerli canonical WORKSPACE_MEMBER formatında olup olmadığını kontrol eder.
     */
    fun isValid(entityId: String): Boolean {
        return runCatching { decode(entityId) }.isSuccess
    }
}

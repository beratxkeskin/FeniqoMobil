package com.feniqo.mobile.domain.model

import kotlinx.datetime.Instant

/** Uygulama dilini UI katmanından bağımsız biçimde taşır. */
enum class AppLanguage {
    TR,
    EN,
}

/** Kullanıcının kalıcı tema tercihi; Compose temasına doğrudan bağımlı değildir. */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Oturum açmış kullanıcının sunucu ile eşitlenen profil ve tercih bilgileri.
 * Rol burada tutulmaz; rol, V2'de çalışma alanı üyeliğine aittir.
 */
data class UserProfile(
    val id: EntityId,
    val email: String,
    val fullName: String?,
    val currency: Currency,
    val themePreference: ThemePreference,
    val language: AppLanguage,
    val activeWorkspaceId: EntityId?,
    val createdAt: Instant,
)

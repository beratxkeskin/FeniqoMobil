package com.feniqo.mobile.navigation

/**
 * Kök navigasyon akışının oturum durumudur.
 * Yalnızca oturumun belirlenme ve varlık durumunu temsil eder;
 * kullanıcı kimliği veya ham oturum detaylarını taşımaz.
 */
sealed interface AppAuthState {
    data object Checking : AppAuthState
    data object Authenticated : AppAuthState
    data object Unauthenticated : AppAuthState
}

package com.feniqo.mobile.data.remote.auth

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SupabaseAuthSessionStatusResolutionTest {

    private val testUser = UserInfo(
        aud = "authenticated",
        id = "test-user-id-1234",
        email = "testuser@feniqo.com",
    )

    private val testSession = UserSession(
        accessToken = "test-access-token",
        refreshToken = "test-refresh-token",
        expiresIn = 3600,
        tokenType = "bearer",
        user = testUser,
    )

    private val defaultSessionManager: SessionManager = MemorySessionManager(testSession)

    @Test
    fun initializing_does_not_emit_unauthenticated_or_null() = runTest {
        // Kontrat 1: SessionStatus.Initializing null/Unauthenticated emisyonu üretmemeli.
        // Navigation Checking durumunda kalabilmeli; Welcome ekranı erken açılmamalıdır.
        val flow = flowOf(SessionStatus.Initializing)
        val emissions = flow.mapToRemoteAuthSession(defaultSessionManager).toList()

        assertTrue(
            emissions.isEmpty(),
            "SessionStatus.Initializing emisyon üretmemeli (Checking durumunu korumalı), ancak ${emissions.size} adet üretildi.",
        )
    }

    @Test
    fun authenticated_maps_to_remote_auth_session() = runTest {
        // Kontrat 2: SessionStatus.Authenticated SDK session'ını normal RemoteAuthSession olarak üretmelidir.
        val flow = flowOf(SessionStatus.Authenticated(testSession))
        val emissions = flow.mapToRemoteAuthSession(defaultSessionManager).toList()

        assertEquals(1, emissions.size)
        val result = emissions.first()
        assertEquals("test-user-id-1234", result?.userId)
        assertEquals("testuser@feniqo.com", result?.email)
        assertEquals(testSession.expiresAt.epochSeconds, result?.expiresAtEpochSeconds)
    }

    @Test
    fun refreshFailure_with_persisted_session_resolves_local_auth_session() = runTest {
        // Kontrat 3: Transient RefreshFailure (örn. offline cold-start) durumunda
        // SessionManager içinde şifreli kayıtlı session varsa yerel kullanıcı kimliğini üretmelidir.
        val sessionManager: SessionManager = MemorySessionManager(testSession)
        val flow = flowOf(
            SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(Exception("Network unavailable offline")))
        )
        val emissions = flow.mapToRemoteAuthSession(sessionManager).toList()

        assertEquals(1, emissions.size)
        val result = emissions.first()
        assertEquals("test-user-id-1234", result?.userId)
        assertEquals("testuser@feniqo.com", result?.email)
        assertEquals(testSession.expiresAt.epochSeconds, result?.expiresAtEpochSeconds)
    }

    @Test
    fun refreshFailure_without_persisted_session_fails_closed_to_null() = runTest {
        // Kontrat 5: Persisted session okunamıyorsa / yoksa fail-closed davranmalı; null üretmelidir.
        val sessionManager: SessionManager = MemorySessionManager(null)
        val flow = flowOf(
            SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(Exception("Network unavailable offline")))
        )
        val emissions = flow.mapToRemoteAuthSession(sessionManager).toList()

        assertEquals(1, emissions.size)
        assertEquals(null, emissions.first())
    }

    @Test
    fun notAuthenticated_emits_null_without_persisted_fallback() = runTest {
        // Kontrat 4: SessionStatus.NotAuthenticated persisted fallback kullanmadan kesin null üretmeli;
        // gerçek sign-out veya geçersiz/revoke oturum Welcome ekranına dönmelidir.
        val sessionManager: SessionManager = MemorySessionManager(testSession)
        val flow = flowOf(SessionStatus.NotAuthenticated(isSignOut = true))
        val emissions = flow.mapToRemoteAuthSession(sessionManager).toList()

        assertEquals(1, emissions.size)
        assertEquals(null, emissions.first())
    }

    @Test
    fun refreshFailure_when_loadSession_throws_ordinary_exception_fails_closed_to_null() = runTest {
        // Kontrat 6: SessionManager loadSession sırasında normal bir Exception (örn. şifre çözme veya disk hatası)
        // fırlatırsa fail-closed olarak tek bir null üretmeli, çökmemelidir.
        val throwingSessionManager = object : SessionManager {
            override suspend fun saveSession(session: UserSession) {}
            override suspend fun loadSession(): UserSession = throw IllegalStateException("Simulated storage read error")
            override suspend fun deleteSession() {}
        }
        val flow = flowOf(
            SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(Exception("Network unavailable offline")))
        )
        val emissions = flow.mapToRemoteAuthSession(throwingSessionManager).toList()

        assertEquals(1, emissions.size)
        assertEquals(null, emissions.first())
    }

    @Test
    fun refreshFailure_when_loadSession_throws_cancellation_exception_is_rethrown() = runTest {
        // Kontrat 7: SessionManager loadSession sırasında CancellationException fırlatırsa
        // coroutine cancellation yutulmamalı; exception yukarıya iletilmelidir.
        val cancellingSessionManager = object : SessionManager {
            override suspend fun saveSession(session: UserSession) {}
            override suspend fun loadSession(): UserSession = throw CancellationException("Simulated coroutine cancellation")
            override suspend fun deleteSession() {}
        }
        val flow = flowOf(
            SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(Exception("Network unavailable offline")))
        )

        assertFailsWith<CancellationException> {
            flow.mapToRemoteAuthSession(cancellingSessionManager).toList()
        }
    }
}

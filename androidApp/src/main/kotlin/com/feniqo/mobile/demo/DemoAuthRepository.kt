package com.feniqo.mobile.demo

import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toDomain
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.data.mapper.toPendingUpdate
import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.domain.repository.*
import kotlinx.coroutines.flow.*
import kotlin.time.Instant

/** Yalnız ayrı demo paketinde kullanılan, uzak kimlik bilgisi taşımayan yerel oturum. */
class DemoAuthRepository(
    private val profiles: ProfileDao,
    private val queue: OfflineWriteQueue,
) : AuthRepository {
    private val session = MutableStateFlow<AuthSession?>(null)

    suspend fun open() {
        if (profiles.observeById(USER_ID.value).first() == null) {
            val now = System.currentTimeMillis()
            val profile = UserProfile(USER_ID, "demo@example.invalid", "Deniz Demo", Currency.TRY,
                ThemePreference.SYSTEM, AppLanguage.TR, null, Instant.fromEpochMilliseconds(now))
            queue.enqueueProfile(profile.toEntity(newSyncMetadata(now)), OutboxOperationType.CREATE)
        }
        session.value = AuthSession(USER_ID, "demo@example.invalid", Instant.parse("2099-01-01T00:00:00Z"))
    }

    override fun observeSession(): Flow<AuthSession?> = session
    override fun observeCurrentProfile(): Flow<UserProfile?> =
        profiles.observeById(USER_ID.value).map { it?.toDomain() }
    override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = unsupported()
    override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = unsupported()
    override suspend fun refreshSession(): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    override suspend fun signOut(): RepositoryResult<Unit> {
        session.value = null
        return RepositoryResult.Success(Unit)
    }
    override suspend fun updateFullName(fullName: String?): RepositoryResult<Unit> {
        val existing = profiles.observeById(USER_ID.value).first() ?: return unsupported()
        val updated = existing.toDomain().copy(fullName = fullName)
        queue.enqueueProfile(updated.toEntity(existing.sync.toPendingUpdate(System.currentTimeMillis())),
            OutboxOperationType.UPDATE)
        return RepositoryResult.Success(Unit)
    }
    private fun unsupported() = RepositoryResult.Failure(AppError.Authentication("demo_local_only"))

    companion object {
        val USER_ID = EntityId("de000000-0000-4000-8000-000000000001")
    }
}

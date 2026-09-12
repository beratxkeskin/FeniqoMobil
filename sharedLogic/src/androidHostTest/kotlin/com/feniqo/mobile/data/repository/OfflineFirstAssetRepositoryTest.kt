package com.feniqo.mobile.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.domain.model.AppError
import com.feniqo.mobile.domain.model.AssetType
import com.feniqo.mobile.domain.model.CreateAssetCommand
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.EntityIdGenerator
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.UpdateAssetCommand
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.AuthSession
import com.feniqo.mobile.domain.repository.RepositoryResult
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineFirstAssetRepositoryTest {
    @Test
    fun create_update_delete_are_local_first_and_owner_scoped() = runTest {
        val db = database()
        try {
            val repository = repository(db, SessionAuth(USER_ID))
            val created = assertIs<RepositoryResult.Success<EntityId>>(repository.create(validCreate()))
            assertEquals(ASSET_ID, created.value.value)
            assertEquals("Altın", repository.observeAssets().first().single().name)
            assertEquals(1, db.syncOperationDao().observePendingCount().first())

            assertIs<RepositoryResult.Success<Unit>>(repository.update(validUpdate(created.value)))
            assertEquals("Altın Birikimi", repository.observeAsset(created.value).first()?.name)
            assertEquals(1, db.syncOperationDao().observePendingCount().first(), "Pending CREATE güncellemeyi coalesce etmelidir")

            assertIs<RepositoryResult.Failure>(repository(db, SessionAuth(OTHER_USER)).update(validUpdate(created.value)))
            assertIs<RepositoryResult.Success<Unit>>(repository.softDelete(created.value))
            assertNull(repository.observeAsset(created.value).first())
            assertEquals(0, db.syncOperationDao().observePendingCount().first(), "Pending CREATE silinince entity ve outbox hard-delete edilmelidir")
        } finally {
            db.close()
        }
    }

    @Test
    fun missing_session_and_validation_fail_without_local_write() = runTest {
        val db = database()
        try {
            val noSession = repository(db, SessionAuth(null))
            val authFailure = assertIs<RepositoryResult.Failure>(noSession.create(validCreate()))
            assertIs<AppError.Authentication>(authFailure.error)

            val repository = repository(db, SessionAuth(USER_ID))
            val invalid = assertIs<RepositoryResult.Failure>(repository.create(validCreate().copy(name = " ")))
            assertIs<AppError.Validation>(invalid.error)
            assertEquals(0, db.syncOperationDao().observePendingCount().first())
            assertEquals(emptyList(), repository.observeAssets().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun cancellation_is_rethrown() = runTest {
        val db = database()
        try {
            val auth = object : AuthRepository by SessionAuth(null) {
                override fun observeSession(): Flow<AuthSession?> = flow { throw CancellationException("cancel") }
            }
            assertFailsWith<CancellationException> { repository(db, auth).create(validCreate()) }
        } finally {
            db.close()
        }
    }

    private fun repository(db: FeniqoDatabase, auth: AuthRepository) = OfflineFirstAssetRepository(
        authRepository = auth,
        assetDao = db.assetDao(),
        offlineWriteQueue = OfflineWriteQueue(db.localMutationDao(), db.syncOperationDao(), nowEpochMillisProvider = { NOW }),
        entityIdGenerator = EntityIdGenerator { EntityId(ASSET_ID) },
        nowEpochMillisProvider = { NOW },
    )

    private fun validCreate() = CreateAssetCommand(
        name = "Altın",
        type = AssetType.PRECIOUS_METALS,
        currentValue = Money(100_000, Currency.TRY),
        trackingSymbol = "XAU",
        autoTrack = true,
    )

    private fun validUpdate(id: EntityId) = UpdateAssetCommand(
        id = id,
        name = "Altın Birikimi",
        type = AssetType.PRECIOUS_METALS,
        currentValue = Money(110_000, Currency.TRY),
        trackingSymbol = "XAU",
        autoTrack = true,
    )

    private fun database(): FeniqoDatabase = Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
        context = ApplicationProvider.getApplicationContext<Context>(),
        factory = { FeniqoDatabaseConstructor.initialize() },
    ).setQueryCoroutineContext(Dispatchers.Default).build()

    private class SessionAuth(private val userId: String?) : AuthRepository {
        override fun observeSession(): Flow<AuthSession?> = flowOf(userId?.let {
            AuthSession(EntityId(it), "asset@feniqo.app", Instant.fromEpochMilliseconds(NOW + 60_000))
        })
        override fun observeCurrentProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun signIn(email: String, password: String): RepositoryResult<Unit> = error("N/A")
        override suspend fun signUp(email: String, password: String, fullName: String?): RepositoryResult<EntityId> = error("N/A")
        override suspend fun refreshSession(): RepositoryResult<Unit> = error("N/A")
        override suspend fun signOut(): RepositoryResult<Unit> = error("N/A")
    }

    private companion object {
        const val USER_ID = "11111111-1111-1111-1111-111111111111"
        const val OTHER_USER = "22222222-2222-2222-2222-222222222222"
        const val ASSET_ID = "33333333-3333-3333-3333-333333333333"
        const val NOW = 1_800_000_000_000L
    }
}

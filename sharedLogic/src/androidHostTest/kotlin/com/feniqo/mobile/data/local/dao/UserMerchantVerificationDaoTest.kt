package com.feniqo.mobile.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UserMerchantVerificationDaoTest {
    private lateinit var database: FeniqoDatabase
    private lateinit var dao: UserMerchantVerificationDao

    private val userAId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val userBId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    private val scopeA = "user:$userAId"
    private val scopeB = "user:$userBId"

    private val verifA1 = "10000000-0000-4000-8000-000000000001"
    private val verifASpotify = "10000000-0000-4000-8000-000000000002"
    private val verifBSpotify = "20000000-0000-4000-8000-000000000001"
    private val verifDeleted = "10000000-0000-4000-8000-00000000000d"
    private val verifList1 = "10000000-0000-4000-8000-000000000011"
    private val verifList2 = "10000000-0000-4000-8000-000000000012"
    private val verifList3 = "20000000-0000-4000-8000-000000000013"
    private val verifList4 = "10000000-0000-4000-8000-000000000014"
    private val verifIdA = "10000000-0000-4000-8000-0000000000aa"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builder =
            Room.inMemoryDatabaseBuilder<FeniqoDatabase>(
                context = context,
                factory = { FeniqoDatabaseConstructor.initialize() },
            )
        database =
            builder
                .setQueryCoroutineContext(Dispatchers.Default)
                .build()

        dao = database.userMerchantVerificationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun insertFixture(
        id: String,
        ownerId: String,
        scopeKey: String,
        normalizedAlias: String,
        decisionCode: String,
        merchantId: String?,
        deletedAtEpochMs: Long? = null,
        version: Long = 1L,
    ) {
        val merchantSql = if (merchantId != null) "'$merchantId'" else "NULL"
        val deletedSql = if (deletedAtEpochMs != null) "$deletedAtEpochMs" else "NULL"

        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO user_merchant_verifications (
                id, owner_id, workspace_id, scope_key, normalized_alias,
                decision_code, merchant_id, scope_code, created_at_epoch_ms,
                sync_status, updated_at_epoch_ms, local_updated_at_epoch_ms,
                deleted_at_epoch_ms, version, base_version, last_sync_error
            ) VALUES (
                '$id', '$ownerId', NULL, '$scopeKey', '$normalizedAlias',
                '$decisionCode', $merchantSql, 'PERSONAL', 1000,
                'SYNCED', 1000, 1000,
                $deletedSql, $version, ${version - 1}, NULL
            )
            """.trimIndent(),
        )
    }

    @Test
    fun getActiveByScopeAndAlias_returnsRecordForMatchingUserAndAlias() =
        runTest {
            insertFixture(
                id = verifA1,
                ownerId = userAId,
                scopeKey = scopeA,
                normalizedAlias = "NETFLIX",
                decisionCode = "ASSIGN",
                merchantId = "netflix",
            )

            val result = dao.getActiveByScopeAndAlias(scopeA, "NETFLIX")
            assertNotNull(result)
            assertEquals(verifA1, result.id)
            assertEquals("netflix", result.merchantId)
            assertEquals("ASSIGN", result.decisionCode)
        }

    @Test
    fun getActiveByScopeAndAlias_isolatesBetweenUsersWithSameAlias() =
        runTest {
            insertFixture(
                id = verifASpotify,
                ownerId = userAId,
                scopeKey = scopeA,
                normalizedAlias = "SPOTIFY",
                decisionCode = "ASSIGN",
                merchantId = "spotify",
            )
            insertFixture(
                id = verifBSpotify,
                ownerId = userBId,
                scopeKey = scopeB,
                normalizedAlias = "SPOTIFY",
                decisionCode = "BLOCK",
                merchantId = null,
            )

            val userAResult = dao.getActiveByScopeAndAlias(scopeA, "SPOTIFY")
            val userBResult = dao.getActiveByScopeAndAlias(scopeB, "SPOTIFY")

            assertNotNull(userAResult)
            assertEquals(verifASpotify, userAResult.id)
            assertEquals("ASSIGN", userAResult.decisionCode)
            assertEquals("spotify", userAResult.merchantId)

            assertNotNull(userBResult)
            assertEquals(verifBSpotify, userBResult.id)
            assertEquals("BLOCK", userBResult.decisionCode)
            assertNull(userBResult.merchantId)

            // User A cannot read User B's record even with same alias
            val leakCheck = dao.getActiveByScopeAndAlias(scopeA, "NONEXISTENT")
            assertNull(leakCheck)
        }

    @Test
    fun tombstonedRecord_isExcludedFromActiveQueries() =
        runTest {
            insertFixture(
                id = verifDeleted,
                ownerId = userAId,
                scopeKey = scopeA,
                normalizedAlias = "DISNEY",
                decisionCode = "ASSIGN",
                merchantId = "disney",
                deletedAtEpochMs = 2000L,
            )

            val aliasResult = dao.getActiveByScopeAndAlias(scopeA, "DISNEY")
            assertNull(aliasResult)

            val idResult = dao.getActiveByIdAndScope(verifDeleted, scopeA)
            assertNull(idResult)

            val listResult = dao.observeActiveByScope(scopeA).first()
            assertTrue(listResult.isEmpty())
        }

    @Test
    fun observeActiveByScope_emitsOnlyActiveRecordsForGivenScope() =
        runTest {
            insertFixture(verifList1, userAId, scopeA, "AMAZON", "ASSIGN", "amazon")
            insertFixture(verifList2, userAId, scopeA, "YEMEKSEPETI", "BLOCK", null)
            insertFixture(verifList3, userBId, scopeB, "TRENDYOL", "ASSIGN", "trendyol")
            insertFixture(verifList4, userAId, scopeA, "TOMBSTONED", "ASSIGN", "x", deletedAtEpochMs = 5000L)

            val userAList = dao.observeActiveByScope(scopeA).first()
            val userBList = dao.observeActiveByScope(scopeB).first()

            assertEquals(2, userAList.size)
            assertEquals(listOf("AMAZON", "YEMEKSEPETI"), userAList.map { it.normalizedAlias })

            assertEquals(1, userBList.size)
            assertEquals(listOf("TRENDYOL"), userBList.map { it.normalizedAlias })
        }

    @Test
    fun getActiveByIdAndScope_requiresMatchingScopeAndActiveState() =
        runTest {
            insertFixture(verifIdA, userAId, scopeA, "MIGROS", "ASSIGN", "migros")

            val match = dao.getActiveByIdAndScope(verifIdA, scopeA)
            assertNotNull(match)
            assertEquals("MIGROS", match.normalizedAlias)

            val wrongScope = dao.getActiveByIdAndScope(verifIdA, scopeB)
            assertNull(wrongScope)
        }
}

package com.feniqo.mobile.demo

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.feniqo.mobile.data.local.database.DefaultCategorySeeder
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.database.FeniqoDatabaseConstructor
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.repository.RoomActiveWorkspaceScope
import com.feniqo.mobile.data.sync.SyncScopeKey
import com.feniqo.mobile.data.util.RandomUuidEntityIdGenerator
import com.feniqo.mobile.di.RepositoryModule
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DemoDataRepositoryTest {
    @Test
    fun `complete scenario persists through repositories and reopening never duplicates or restores deleted rows`() =
        runBlocking {
            val base = ApplicationProvider.getApplicationContext<Context>()
            val context =
                object : ContextWrapper(base) {
                    override fun getPackageName() = "com.feniqo.mobile.demo"
                }
            context
                .getSharedPreferences("demo_fixture", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            val db =
                Room
                    .inMemoryDatabaseBuilder<FeniqoDatabase>(
                        context,
                        factory = FeniqoDatabaseConstructor::initialize,
                    ).setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            try {
                val queue = OfflineWriteQueue(db.localMutationDao(), db.syncOperationDao())
                val auth = DemoAuthRepository(db.profileDao(), queue)
                val scope = RoomActiveWorkspaceScope(db.workspaceDao())
                val ids = RandomUuidEntityIdGenerator()
                val verifications =
                    RepositoryModule.provideUserMerchantVerificationRepository(
                        auth,
                        db.userMerchantVerificationDao(),
                        queue,
                    )
                val categories =
                    RepositoryModule.provideCategoryRepository(
                        auth,
                        db.categoryDao(),
                        queue,
                        scope,
                    )
                val txRepo =
                    RepositoryModule.provideOfflineFirstTransactionRepository(
                        auth,
                        db.transactionDao(),
                        db.categoryDao(),
                        queue,
                        db.workspaceDao(),
                        scope,
                        verifications,
                    )
                val transactions = RepositoryModule.provideTransactionRepository(txRepo)
                val budgets =
                    RepositoryModule.provideBudgetRepository(
                        auth,
                        db.budgetDao(),
                        db.categoryDao(),
                        queue,
                        ids,
                        scope,
                    )
                val goals =
                    RepositoryModule.provideGoalRepository(
                        auth,
                        db.goalDao(),
                        queue,
                        ids,
                        scope,
                    )
                val debts =
                    RepositoryModule.provideDebtRepository(
                        auth,
                        db.debtDao(),
                        queue,
                        ids,
                        scope,
                    )
                val subscriptions =
                    RepositoryModule.provideSubscriptionRepository(
                        auth,
                        db.categoryDao(),
                        db.subscriptionDao(),
                        db.subscriptionPriceHistoryDao(),
                        db.subscriptionPaymentDao(),
                        queue,
                        ids,
                        scope,
                    )
                val recurring =
                    RepositoryModule.provideRecurringTransactionRepository(
                        auth,
                        db.categoryDao(),
                        db.recurringTransactionDao(),
                        queue,
                        ids,
                        scope,
                        verifications,
                    )
                val assets =
                    RepositoryModule.provideAssetRepository(
                        auth,
                        db.assetDao(),
                        queue,
                        ids,
                    )
                val remote =
                    Proxy.newProxyInstance(
                        CoreRemoteDataSource::class.java.classLoader,
                        arrayOf(CoreRemoteDataSource::class.java),
                    ) { _, _, _ -> error("Demo must never call remote") } as CoreRemoteDataSource
                val workspaces =
                    RepositoryModule.provideWorkspaceRepository(
                        auth,
                        db.workspaceDao(),
                        db.localMutationDao(),
                        db.profileDao(),
                        remote,
                        db.remoteSyncDao(),
                    )
                val repository =
                    DemoDataRepository(
                        context = context,
                        auth = auth,
                        defaults = DefaultCategorySeeder(db.remoteSyncDao()),
                        categories = categories,
                        transactions = transactions,
                        budgets = budgets,
                        goals = goals,
                        debts = debts,
                        subscriptions = subscriptions,
                        recurring = recurring,
                        assets = assets,
                        workspaces = workspaces,
                        queue = queue,
                    )
                repository.prepare()
                val rows = transactions.observeTransactions().first()
                assertTrue(rows.size > 150)
                val today = java.time.LocalDate.now()
                val currentLocalDate = LocalDate.parse(today.toString())
                val currentYearMonth = YearMonth.from(currentLocalDate)
                assertEquals(5, budgets.observeBudgets(currentYearMonth).first().size)
                assertEquals(3, goals.observeGoals().first().size)
                val totalContributions =
                    goals
                        .observeGoals()
                        .first()
                        .sumOf { goal ->
                            goals
                                .observeContributions(goal.id)
                                .first()
                                .size
                        }
                assertEquals(9, totalContributions)
                assertEquals(3, debts.observeDebts().first().size)
                assertEquals(5, subscriptions.observeSubscriptions().first().size)
                assertEquals(18, subscriptions.observePayments().first().size)
                assertEquals(3, subscriptions.observePriceHistories().first().size)
                assertEquals(3, recurring.observeRecurringTransactions().first().size)
                assertEquals(5, assets.observeAssets().first().size)
                assertEquals(1, workspaces.observeWorkspaces().first().size)
                val singleWorkspace =
                    workspaces
                        .observeWorkspaces()
                        .first()
                        .single()
                val workspaceId = singleWorkspace.id
                assertEquals(3, workspaces.observeMembers(workspaceId).first().size)
                workspaces.setActive(workspaceId).requireSuccess()
                assertEquals(3, transactions.observeTransactions().first().size)
                workspaces.setActive(null).requireSuccess()
                val syncScopeKey = SyncScopeKey.user(DemoAuthRepository.USER_ID.value).rawValue
                val pending = queue.observePendingCount(syncScopeKey).first()
                assertTrue(pending >= rows.size)
                val sync = DemoSyncRepository(queue)
                assertEquals(SyncPhase.OFFLINE, sync.observeOverview().first().phase)
                assertTrue(sync.requestSync() is RepositoryResult.Failure)
                assertEquals(pending, queue.observePendingCount(syncScopeKey).first())
                repository.prepare()
                assertEquals(rows.size, transactions.observeTransactions().first().size)
                assertEquals(pending, queue.observePendingCount(syncScopeKey).first())
                transactions.softDelete(rows.first().id).requireSuccess()
                auth.signOut()
                repository.prepare()
                assertNotNull(auth.observeSession().first())
                assertEquals(rows.size - 1, transactions.observeTransactions().first().size)
                assertTrue(auth.signIn("anything", "anything") is RepositoryResult.Failure)
                context
                    .getSharedPreferences("demo_fixture", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("state", 1)
                    .commit()
                var failed = false
                try {
                    repository.prepare()
                } catch (_: IllegalStateException) {
                    failed = true
                }
                assertTrue(failed)
                assertEquals(rows.size - 1, transactions.observeTransactions().first().size)
            } finally {
                db.close()
            }
        }
}

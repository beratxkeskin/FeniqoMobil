package com.feniqo.mobile.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.feniqo.mobile.data.local.dao.SubscriptionPaymentReminderReceiptDao
import com.feniqo.mobile.data.local.entity.SubscriptionPaymentReminderReceiptEntity
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.SetSubscriptionActiveCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionPaymentReminderCandidate
import com.feniqo.mobile.domain.model.SubscriptionReminderKind
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.repository.SubscriptionRepository
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionsUseCase
import com.feniqo.mobile.domain.usecase.PlanSubscriptionPaymentRemindersUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubscriptionPaymentReminderWorkerTest {

    private lateinit var context: Context

    private val fixedToday = LocalDate(2026, 9, 1)
    private val fixedInstant = Instant.fromEpochMilliseconds(1725148800000L)

    private class FakeRecurringTimeProvider(
        var localToday: LocalDate,
        var currentInstant: Instant,
    ) : RecurringTransactionTimeProvider {
        override fun currentLocalDate(): LocalDate = localToday
        override fun currentInstant(): Instant = currentInstant
    }

    private class FakeReceiptDao : SubscriptionPaymentReminderReceiptDao {
        val claimedReceipts = mutableMapOf<String, SubscriptionPaymentReminderReceiptEntity>()
        var claimCallCount = 0

        override suspend fun insertIgnore(entity: SubscriptionPaymentReminderReceiptEntity): Long {
            return if (claimedReceipts.containsKey(entity.stableKey)) {
                -1L
            } else {
                claimedReceipts[entity.stableKey] = entity
                1L
            }
        }

        override suspend fun claim(entity: SubscriptionPaymentReminderReceiptEntity): Boolean {
            claimCallCount++
            return insertIgnore(entity) != -1L
        }

        override suspend fun getByStableKey(stableKey: String): SubscriptionPaymentReminderReceiptEntity? {
            return claimedReceipts[stableKey]
        }

        override suspend fun isClaimed(stableKey: String): Boolean {
            return claimedReceipts.containsKey(stableKey)
        }
    }

    private class FakeSubscriptionPaymentReminderNotifier(
        var hasPermission: Boolean = true,
        var shouldThrowOnNotify: Boolean = false,
    ) : SubscriptionPaymentReminderNotifier {
        val notifiedCandidates = mutableListOf<SubscriptionPaymentReminderCandidate>()

        override fun canPostNotifications(): Boolean = hasPermission

        override suspend fun notifyReminder(candidate: SubscriptionPaymentReminderCandidate) {
            if (shouldThrowOnNotify) {
                throw RuntimeException("Bildirim servisi hatası")
            }
            notifiedCandidates.add(candidate)
        }
    }

    private class FakeSubscriptionRepository(
        var subscriptionsFlow: Flow<List<Subscription>> = flowOf(emptyList()),
    ) : SubscriptionRepository {
        override fun observeSubscriptions(): Flow<List<Subscription>> = subscriptionsFlow
        override fun observeSubscription(id: EntityId): Flow<Subscription?> = error("Test kapsamı dışı")
        override suspend fun create(command: CreateSubscriptionCommand) = error("Test kapsamı dışı")
        override suspend fun update(command: UpdateSubscriptionCommand) = error("Test kapsamı dışı")
        override suspend fun setActive(command: SetSubscriptionActiveCommand) = error("Test kapsamı dışı")
        override suspend fun advanceRenewal(id: EntityId) = error("Test kapsamı dışı")
        override suspend fun softDelete(id: EntityId) = error("Test kapsamı dışı")
    }

    private fun testSubscription(
        id: String,
        name: String = "Spotify",
        nextRenewalDate: LocalDate = fixedToday,
        isActive: Boolean = true,
    ): Subscription {
        return Subscription(
            id = EntityId(id),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = name,
            amount = Money(4999L, Currency.TRY),
            categoryId = null,
            renewalRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = LocalDate(2026, 8, 1),
                endDate = null,
            ),
            nextRenewalDate = nextRenewalDate,
            isActive = isActive,
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun buildWorker(
        observeSubscriptionsUseCase: ObserveSubscriptionsUseCase,
        planSubscriptionPaymentRemindersUseCase: PlanSubscriptionPaymentRemindersUseCase,
        receiptDao: SubscriptionPaymentReminderReceiptDao,
        notifier: SubscriptionPaymentReminderNotifier,
        timeProvider: RecurringTransactionTimeProvider,
    ): SubscriptionPaymentReminderWorker {
        return TestListenableWorkerBuilder<SubscriptionPaymentReminderWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker {
                    return SubscriptionPaymentReminderWorker(
                        appContext = appContext,
                        params = workerParameters,
                        observeSubscriptionsUseCase = observeSubscriptionsUseCase,
                        planSubscriptionPaymentRemindersUseCase = planSubscriptionPaymentRemindersUseCase,
                        receiptDao = receiptDao,
                        notifier = notifier,
                        timeProvider = timeProvider,
                    )
                }
            })
            .build()
    }

    @Test
    fun `when notification permission is granted, due today candidate is claimed once and one notification is posted`() = runTest {
        val subscription = testSubscription("sub-1", name = "Netflix", nextRenewalDate = fixedToday)
        val fakeRepo = FakeSubscriptionRepository(flowOf(listOf(subscription)))
        val observeUseCase = ObserveSubscriptionsUseCase(fakeRepo)
        val planUseCase = PlanSubscriptionPaymentRemindersUseCase()
        val receiptDao = FakeReceiptDao()
        val notifier = FakeSubscriptionPaymentReminderNotifier(hasPermission = true)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)

        val worker = buildWorker(observeUseCase, planUseCase, receiptDao, notifier, timeProvider)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, receiptDao.claimCallCount)
        assertTrue(receiptDao.isClaimed("sub-1_2026-09-01_DUE_TODAY"))
        assertEquals(1, notifier.notifiedCandidates.size)
        val notified = notifier.notifiedCandidates.first()
        assertEquals(EntityId("sub-1"), notified.subscriptionId)
        assertEquals("Netflix", notified.subscriptionName)
        assertEquals(SubscriptionReminderKind.DUE_TODAY, notified.reminderKind)
    }

    @Test
    fun `when worker runs second time with same data, existing receipt prevents duplicate notification`() = runTest {
        val subscription = testSubscription("sub-1", name = "Netflix", nextRenewalDate = fixedToday)
        val fakeRepo = FakeSubscriptionRepository(flowOf(listOf(subscription)))
        val observeUseCase = ObserveSubscriptionsUseCase(fakeRepo)
        val planUseCase = PlanSubscriptionPaymentRemindersUseCase()
        val receiptDao = FakeReceiptDao()
        val notifier = FakeSubscriptionPaymentReminderNotifier(hasPermission = true)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)

        val worker1 = buildWorker(observeUseCase, planUseCase, receiptDao, notifier, timeProvider)
        val result1 = worker1.doWork()
        assertEquals(ListenableWorker.Result.success(), result1)
        assertEquals(1, notifier.notifiedCandidates.size)

        // İkinci çalıştırma (aynı veri, aynı receiptDao)
        val worker2 = buildWorker(observeUseCase, planUseCase, receiptDao, notifier, timeProvider)
        val result2 = worker2.doWork()
        assertEquals(ListenableWorker.Result.success(), result2)

        // DAO claim ikinci kez çağrılır ama false döner; yeni bildirim gönderilmez (toplam hala 1)
        assertEquals(2, receiptDao.claimCallCount)
        assertEquals(1, notifier.notifiedCandidates.size)
    }

    @Test
    fun `when notification permission is not granted, no claim and no notify is performed and worker returns success`() = runTest {
        val subscription = testSubscription("sub-1", name = "Netflix", nextRenewalDate = fixedToday)
        val fakeRepo = FakeSubscriptionRepository(flowOf(listOf(subscription)))
        val observeUseCase = ObserveSubscriptionsUseCase(fakeRepo)
        val planUseCase = PlanSubscriptionPaymentRemindersUseCase()
        val receiptDao = FakeReceiptDao()
        val notifier = FakeSubscriptionPaymentReminderNotifier(hasPermission = false)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)

        val worker = buildWorker(observeUseCase, planUseCase, receiptDao, notifier, timeProvider)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // İzin yokken claim yazılmaz ki kullanıcı daha sonra izin verirse hatırlatıcı kaybolmasın
        assertEquals(0, receiptDao.claimCallCount)
        assertFalse(receiptDao.isClaimed("sub-1_2026-09-01_DUE_TODAY"))
        assertTrue(notifier.notifiedCandidates.isEmpty())
    }

    @Test
    fun `when notifier throws exception, claim is preserved and worker returns failure for at-most-once semantics`() = runTest {
        val subscription = testSubscription("sub-1", name = "Netflix", nextRenewalDate = fixedToday)
        val fakeRepo = FakeSubscriptionRepository(flowOf(listOf(subscription)))
        val observeUseCase = ObserveSubscriptionsUseCase(fakeRepo)
        val planUseCase = PlanSubscriptionPaymentRemindersUseCase()
        val receiptDao = FakeReceiptDao()
        val notifier = FakeSubscriptionPaymentReminderNotifier(hasPermission = true, shouldThrowOnNotify = true)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)

        val worker = buildWorker(observeUseCase, planUseCase, receiptDao, notifier, timeProvider)

        val result = worker.doWork()

        // Hata fırlatıldığında Worker failure döner
        assertEquals(ListenableWorker.Result.failure(), result)
        // Claim-before-dispatch gereği claim Room'da yazılmıştır ve korunur (at-most-once / flood önleme)
        assertTrue(receiptDao.isClaimed("sub-1_2026-09-01_DUE_TODAY"))
    }

    @Test
    fun `when coroutine is cancelled then cancellation exception is rethrown`() = runTest {
        val throwingRepo = FakeSubscriptionRepository(
            flow {
                throw CancellationException("worker_cancelled")
            }
        )
        val observeUseCase = ObserveSubscriptionsUseCase(throwingRepo)
        val planUseCase = PlanSubscriptionPaymentRemindersUseCase()
        val receiptDao = FakeReceiptDao()
        val notifier = FakeSubscriptionPaymentReminderNotifier(hasPermission = true)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)

        val worker = buildWorker(observeUseCase, planUseCase, receiptDao, notifier, timeProvider)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                worker.doWork()
            }
        }
    }

    @Test
    fun `when multiple candidates exist for upcoming and due today, each is claimed and notified independently`() = runTest {
        val subDueToday = testSubscription("sub-due", name = "iCloud", nextRenewalDate = fixedToday)
        val subUpcoming = testSubscription("sub-up", name = "Gym", nextRenewalDate = LocalDate(2026, 9, 8)) // exactly 7 days
        val subNotDue = testSubscription("sub-far", name = "Yearly", nextRenewalDate = LocalDate(2026, 10, 1))

        val fakeRepo = FakeSubscriptionRepository(flowOf(listOf(subDueToday, subUpcoming, subNotDue)))
        val observeUseCase = ObserveSubscriptionsUseCase(fakeRepo)
        val planUseCase = PlanSubscriptionPaymentRemindersUseCase()
        val receiptDao = FakeReceiptDao()
        val notifier = FakeSubscriptionPaymentReminderNotifier(hasPermission = true)
        val timeProvider = FakeRecurringTimeProvider(fixedToday, fixedInstant)

        val worker = buildWorker(observeUseCase, planUseCase, receiptDao, notifier, timeProvider)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(2, receiptDao.claimCallCount)
        assertTrue(receiptDao.isClaimed("sub-due_2026-09-01_DUE_TODAY"))
        assertTrue(receiptDao.isClaimed("sub-up_2026-09-08_UPCOMING"))
        assertFalse(receiptDao.isClaimed("sub-far_2026-10-01_DUE_TODAY"))

        assertEquals(2, notifier.notifiedCandidates.size)
        assertEquals(EntityId("sub-due"), notifier.notifiedCandidates[0].subscriptionId)
        assertEquals(EntityId("sub-up"), notifier.notifiedCandidates[1].subscriptionId)
    }
}

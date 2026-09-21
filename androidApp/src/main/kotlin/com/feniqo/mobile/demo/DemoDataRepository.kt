package com.feniqo.mobile.demo

import android.content.Context
import com.feniqo.mobile.BuildConfig
import com.feniqo.mobile.data.local.database.DefaultCategorySeeder
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.data.local.outbox.OutboxOperationType
import com.feniqo.mobile.data.mapper.newSyncMetadata
import com.feniqo.mobile.data.mapper.toEntity
import com.feniqo.mobile.domain.model.*
import com.feniqo.mobile.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Finansal fixture'lar normal repository doğrulaması ve atomik entity + outbox yolunu kullanır. */
@Singleton
class DemoDataRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val defaults: DefaultCategorySeeder,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val budgets: BudgetRepository,
    private val goals: GoalRepository,
    private val debts: DebtRepository,
    private val subscriptions: SubscriptionRepository,
    private val recurring: RecurringTransactionRepository,
    private val assets: AssetRepository,
    private val workspaces: WorkspaceRepository,
    private val queue: OfflineWriteQueue,
) {
    private val mutex = Mutex()

    suspend fun prepare() = mutex.withLock {
        check(BuildConfig.DEMO && context.packageName == "com.feniqo.mobile.demo")
        val store = context.getSharedPreferences("demo_fixture", Context.MODE_PRIVATE)
        (auth as DemoAuthRepository).open()
        when (store.getInt("state", 0)) {
            2 -> return@withLock
            1 -> error("demo_reset_required")
        }
        check(store.edit().putInt("state", 1).commit())
        defaults.seed()
        seed(DemoScenario(java.time.LocalDate.now()))
        check(store.edit().putInt("state", 2).commit())
    }

    private suspend fun seed(s: DemoScenario) {
        val categoryIds = categories.observeCategories().first().associate { it.icon?.key.orEmpty() to it.id }
        val rows = s.transactions(categoryIds)
        rows.filter { it.installment == null }.forEach { transactions.create(it).requireSuccess() }
        transactions.createInstallmentGroup(rows.filter { it.installment != null }).requireSuccess()
        for (offset in 0L..5L) {
            val month = s.firstMonth.plusMonths(offset)
            val period = YearMonth.from(month.year, month.monthValue)
            for ((key, limit) in listOf("groceries" to 900_000L, "food_dining" to 400_000L,
                "housing" to 1_900_000L, "transportation" to 300_000L, "entertainment" to 350_000L)) {
                budgets.create(CreateBudgetCommand(categoryIds.getValue(key), period, s.money(limit))).requireSuccess()
            }
        }
        for ((name, target, contributions) in listOf(
            Triple("Yaz tatili", 6_000_000L, listOf(800_000L, 900_000L, 1_100_000L)),
            Triple("Acil durum birikimi", 15_000_000L, listOf(2_000_000L, 1_500_000L, 2_500_000L)),
            Triple("Yeni bisiklet", 2_400_000L, listOf(800_000L, 800_000L, 800_000L)),
        )) {
            val id = goals.create(CreateGoalCommand(name, s.money(target), targetDate = s.date(s.today.plusMonths(4)),
                color = CategoryColor("#2D5A43"), icon = CategoryIcon("travel"))).requireSuccess()
            contributions.forEachIndexed { i, amount ->
                goals.addContribution(AddGoalContributionCommand(id, s.money(amount),
                    occurredOn = s.date(s.today.minusDays((60 - i * 20).toLong())), note = "Aylık birikim")).requireSuccess()
            }
        }
        for ((index, spec) in listOf(
            Triple("Ev eşyası borcu", DebtType.DEBT, 3_000_000L),
            Triple("Deniz'e verilen borç", DebtType.RECEIVABLE, 1_200_000L),
            Triple("Kapanmış eğitim borcu", DebtType.DEBT, 600_000L),
        ).withIndex()) {
            val id = debts.create(CreateDebtCommand(spec.first, s.money(spec.third), spec.second,
                s.date(s.today.plusDays(if (index == 0) 5 else 15)), "Kurgusal demo kaydı")).requireSuccess()
            debts.addPayment(AddDebtPaymentCommand(id, s.money(if (index == 2) spec.third else spec.third / 3),
                s.date(s.today.minusDays(7)))).requireSuccess()
        }
        val subscriptionCategory = categoryIds.getValue("subscriptions")
        val monthlyStart = s.firstMonth.withDayOfMonth(1)
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, startDate = s.date(monthlyStart), endDate = null)
        for ((name, amount) in listOf("Film ve dizi" to 22900L, "Müzik üyeliği" to 9990L, "Bulut depolama" to 5990L)) {
            val id = subscriptions.create(CreateSubscriptionCommand(name, s.money(amount), subscriptionCategory,
                rule, s.date(monthlyStart), reminderEnabled = false)).requireSuccess()
            repeat(6) { subscriptions.advanceRenewal(id).requireSuccess() }
            subscriptions.update(UpdateSubscriptionCommand(id, name, s.money(amount + 3000L),
                subscriptionCategory, rule, reminderEnabled = false)).requireSuccess()
        }
        for ((name, status) in listOf("Spor üyeliği" to SubscriptionLifecycleStatus.PAUSED,
            "Dil öğrenme denemesi" to SubscriptionLifecycleStatus.TRIAL)) {
            val start = s.date(s.today)
            subscriptions.create(CreateSubscriptionCommand(name, s.money(19900L), subscriptionCategory,
                RecurrenceRule(RecurrenceFrequency.MONTHLY, startDate = start, endDate = null),
                s.date(s.today.plusDays(3)), lifecycleStatus = status,
                trialEndDate = if (status == SubscriptionLifecycleStatus.TRIAL) s.date(s.today.plusDays(3)) else null,
                reminderEnabled = false)).requireSuccess()
        }
        for ((key, title, amount) in listOf(Triple("salary", "Maaş", 7_000_000L),
            Triple("housing", "Kira ödemesi", 1_800_000L), Triple("utilities", "İnternet faturası", 65_000L))) {
            recurring.create(CreateRecurringTransactionCommand(s.money(amount),
                if (key == "salary") TransactionType.INCOME else TransactionType.EXPENSE,
                categoryIds.getValue(key), title, PaymentMethod.BANK_TRANSFER,
                RecurrenceRule(RecurrenceFrequency.MONTHLY, startDate = s.date(s.today.plusDays(7)), endDate = null))).requireSuccess()
        }
        for (command in listOf(
            CreateAssetCommand("Vadesiz banka hesabı", AssetType.CASH, s.money(8_500_000L)),
            CreateAssetCommand("Nakit cüzdan", AssetType.CASH, s.money(350_000L)),
            CreateAssetCommand("Altın birikimi", AssetType.PRECIOUS_METALS, s.money(5_500_000L),
                AssetQuantity(10, 0), s.money(450_000L)),
            CreateAssetCommand("Örnek hisse portföyü", AssetType.STOCKS, s.money(2_700_000L),
                AssetQuantity(100, 0), s.money(30_000L)),
            CreateAssetCommand("Döviz hesabı", AssetType.CASH, s.money(150_000L, Currency.USD)),
        )) assets.create(command).requireSuccess()
        seedSharedWorkspace(s, rows)
    }

    private suspend fun seedSharedWorkspace(s: DemoScenario, personalRows: List<Transaction>) {
        val id = workspaces.createWorkspace(CreateWorkspaceCommand("Ev arkadaşları · Demo",
            description = "Üç kurgusal üyeli yerel örnek alan")).requireSuccess()
        val workspace = workspaces.observeWorkspaces().first().single { it.id == id }
        val users = listOf(DemoAuthRepository.USER_ID, EntityId("de000000-0000-4000-8000-000000000002"),
            EntityId("de000000-0000-4000-8000-000000000003"))
        val now = System.currentTimeMillis()
        // Uzak davet taklidi yapılmaz; fixture üyeleri de aynı atomik Room/outbox sınırından yazılır.
        queue.enqueueWorkspace(workspace.toEntity(newSyncMetadata(now)), users.mapIndexed { i, user ->
            WorkspaceMember(id, user, if (i == 0) WorkspaceRole.OWNER else WorkspaceRole.EDITOR,
                workspace.createdAt).toEntity(newSyncMetadata(now))
        }, OutboxOperationType.UPDATE)
        workspaces.setActive(id).requireSuccess()
        try {
            personalRows.filter { it.type == TransactionType.EXPENSE && it.amount.currency == Currency.TRY }
                .takeLast(3).forEachIndexed { index, original ->
                    transactions.create(original.copy(id = EntityId(java.util.UUID.randomUUID().toString()),
                        workspaceId = id, description = "Ortak ev harcaması ${index + 1}",
                        amount = s.money((index + 1) * 90_000L), installment = null,
                        paidByUserId = users[index], participantUserIds = users)).requireSuccess()
                }
        } finally {
            workspaces.setActive(null).requireSuccess()
        }
    }
}

internal fun <T> RepositoryResult<T>.requireSuccess(): T = when (this) {
    is RepositoryResult.Success -> value
    is RepositoryResult.Failure -> error("demo_seed_failed:${error.code}")
}

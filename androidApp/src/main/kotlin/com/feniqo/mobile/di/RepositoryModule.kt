package com.feniqo.mobile.di

import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.local.dao.TransactionDao
import com.feniqo.mobile.data.remote.auth.AuthRemoteDataSource
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter
import com.feniqo.mobile.data.remote.realtime.RealtimeInvalidationSource
import com.feniqo.mobile.data.repository.OfflineFirstAuthRepository
import com.feniqo.mobile.data.repository.OfflineFirstCategoryRepository
import com.feniqo.mobile.data.repository.OfflineFirstSyncRepository
import com.feniqo.mobile.data.repository.OfflineFirstTransactionRepository
import com.feniqo.mobile.data.sync.InitialRemoteSync
import com.feniqo.mobile.data.sync.IncrementalRemoteSync
import com.feniqo.mobile.data.sync.OutboxProcessor
import com.feniqo.mobile.data.sync.RealtimeSyncCoordinator
import com.feniqo.mobile.data.sync.RoomOutboxQueue
import com.feniqo.mobile.data.sync.V1OutboxOperationExecutor
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.SyncRepository
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.sync.BackgroundSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Repository sözleşmelerini gerçek offline-first uygulamalarına bağlar. */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        remoteDataSource: AuthRemoteDataSource,
        profileDao: ProfileDao,
        syncScheduler: com.feniqo.mobile.domain.sync.BackgroundSyncScheduler,
    ): AuthRepository = OfflineFirstAuthRepository(
        remoteDataSource = remoteDataSource,
        profileDao = profileDao,
        syncScheduler = syncScheduler,
    )

    @Provides
    @Singleton
    fun provideTransactionRepository(
        authRepository: AuthRepository,
        transactionDao: TransactionDao,
        offlineWriteQueue: OfflineWriteQueue,
    ): TransactionRepository = OfflineFirstTransactionRepository(
        authRepository = authRepository,
        transactionDao = transactionDao,
        offlineWriteQueue = offlineWriteQueue,
    )

    @Provides
    @Singleton
    fun provideCategoryRepository(
        authRepository: AuthRepository,
        categoryDao: CategoryDao,
        offlineWriteQueue: OfflineWriteQueue,
    ): CategoryRepository = OfflineFirstCategoryRepository(
        authRepository = authRepository,
        categoryDao = categoryDao,
        offlineWriteQueue = offlineWriteQueue,
    )

    @Provides
    @Singleton
    fun provideBudgetRepository(
        authRepository: AuthRepository,
        budgetDao: com.feniqo.mobile.data.local.dao.BudgetDao,
        categoryDao: CategoryDao,
        offlineWriteQueue: OfflineWriteQueue,
        entityIdGenerator: com.feniqo.mobile.domain.model.EntityIdGenerator,
    ): com.feniqo.mobile.domain.repository.BudgetRepository = com.feniqo.mobile.data.repository.OfflineFirstBudgetRepository(
        authRepository = authRepository,
        budgetDao = budgetDao,
        categoryDao = categoryDao,
        offlineWriteQueue = offlineWriteQueue,
        entityIdGenerator = entityIdGenerator,
    )

    @Provides
    @Singleton
    fun provideRecurringTransactionRepository(
        authRepository: AuthRepository,
        categoryDao: CategoryDao,
        recurringTransactionDao: com.feniqo.mobile.data.local.dao.RecurringTransactionDao,
        offlineWriteQueue: OfflineWriteQueue,
        entityIdGenerator: com.feniqo.mobile.domain.model.EntityIdGenerator,
    ): com.feniqo.mobile.domain.repository.RecurringTransactionRepository =
        com.feniqo.mobile.data.repository.OfflineFirstRecurringTransactionRepository(
            authRepository = authRepository,
            categoryDao = categoryDao,
            recurringTransactionDao = recurringTransactionDao,
            offlineWriteQueue = offlineWriteQueue,
            entityIdGenerator = entityIdGenerator,
        )


    @Provides
    @Singleton
    fun provideInitialRemoteSync(
        remoteDataSource: CoreRemoteDataSource,
        remoteSyncDao: RemoteSyncDao,
    ): InitialRemoteSync = InitialRemoteSync(
        remote = remoteDataSource,
        remoteSyncDao = remoteSyncDao,
        nowEpochMillisProvider = { System.currentTimeMillis() },
    )

    @Provides
    @Singleton
    fun provideIncrementalRemoteSync(
        remoteDataSource: CoreRemoteDataSource,
        remoteSyncDao: RemoteSyncDao,
        syncStateDao: SyncStateDao,
    ): IncrementalRemoteSync = IncrementalRemoteSync(
        remote = remoteDataSource,
        remoteSyncDao = remoteSyncDao,
        syncStateDao = syncStateDao,
        nowEpochMillisProvider = { System.currentTimeMillis() },
    )

    @Provides
    @Singleton
    fun provideV1OutboxOperationExecutor(
        writer: ConditionalRemoteWriter,
        remoteSyncDao: RemoteSyncDao,
    ): V1OutboxOperationExecutor = V1OutboxOperationExecutor(
        writer = writer,
        remoteSyncDao = remoteSyncDao,
        nowEpochMillisProvider = { System.currentTimeMillis() },
    )

    @Provides
    @Singleton
    fun provideV2OutboxOperationExecutor(
        writer: com.feniqo.mobile.data.remote.core.IdempotentConditionalRemoteWriter,
    ): com.feniqo.mobile.data.sync.V2OutboxOperationExecutor = com.feniqo.mobile.data.sync.V2OutboxOperationExecutor(
        writer = writer,
        nowEpochMillisProvider = { System.currentTimeMillis() },
    )

    @Provides
    @Singleton
    fun provideProtocolAwareOutboxOperationExecutor(
        v1Executor: V1OutboxOperationExecutor,
        v2Executor: com.feniqo.mobile.data.sync.V2OutboxOperationExecutor,
    ): com.feniqo.mobile.data.sync.ProtocolAwareOutboxOperationExecutor = com.feniqo.mobile.data.sync.ProtocolAwareOutboxOperationExecutor(
        v1Executor = v1Executor,
        v2Executor = v2Executor,
    )

    @Provides
    @Singleton
    fun provideOutboxProcessor(
        queue: OfflineWriteQueue,
        executor: com.feniqo.mobile.data.sync.ProtocolAwareOutboxOperationExecutor,
    ): OutboxProcessor = OutboxProcessor(RoomOutboxQueue(queue), executor)

    @Provides
    @Singleton
    fun provideConflictRecoveryService(
        syncStateDao: SyncStateDao,
        syncOperationDao: com.feniqo.mobile.data.local.dao.SyncOperationDao,
        localMutationDao: com.feniqo.mobile.data.local.dao.LocalMutationDao,
    ): com.feniqo.mobile.data.sync.ConflictRecoveryService = com.feniqo.mobile.data.sync.ConflictRecoveryService(
        syncStateDao = syncStateDao,
        syncOperationDao = syncOperationDao,
        localMutationDao = localMutationDao,
        nowEpochMillisProvider = { System.currentTimeMillis() },
    )

    @Provides
    @Singleton
    fun provideSyncRepository(
        authRepository: AuthRepository,
        initialRemoteSync: InitialRemoteSync,
        outboxProcessor: OutboxProcessor,
        incrementalRemoteSync: IncrementalRemoteSync,
        queue: OfflineWriteQueue,
        syncStateDao: SyncStateDao,
        remoteSyncDao: RemoteSyncDao,
        conflictRecoveryService: com.feniqo.mobile.data.sync.ConflictRecoveryService,
    ): SyncRepository = OfflineFirstSyncRepository(
        authRepository = authRepository,
        initialRemoteSync = initialRemoteSync,
        outboxProcessor = outboxProcessor,
        incrementalRemoteSync = incrementalRemoteSync,
        offlineWriteQueue = queue,
        syncStateDao = syncStateDao,
        remoteSyncDao = remoteSyncDao,
        conflictRecoveryService = conflictRecoveryService,
        nowEpochMillisProvider = { System.currentTimeMillis() },
    )

    @Provides
    @Singleton
    fun provideRealtimeSyncCoordinator(
        authRepository: AuthRepository,
        invalidationSource: RealtimeInvalidationSource,
        syncRepository: SyncRepository,
        backgroundSyncScheduler: BackgroundSyncScheduler,
    ): RealtimeSyncCoordinator = RealtimeSyncCoordinator(
        authRepository = authRepository,
        invalidationSource = invalidationSource,
        syncRepository = syncRepository,
        syncScheduler = backgroundSyncScheduler,
    )
}

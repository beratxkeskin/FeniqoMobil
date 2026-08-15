package com.feniqo.mobile.di

import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.local.dao.RemoteSyncDao
import com.feniqo.mobile.data.local.dao.SyncStateDao
import com.feniqo.mobile.data.remote.auth.AuthRemoteDataSource
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.ConditionalRemoteWriter
import com.feniqo.mobile.data.remote.realtime.RealtimeInvalidationSource
import com.feniqo.mobile.data.repository.OfflineFirstAuthRepository
import com.feniqo.mobile.data.repository.OfflineFirstSyncRepository
import com.feniqo.mobile.data.sync.InitialRemoteSync
import com.feniqo.mobile.data.sync.IncrementalRemoteSync
import com.feniqo.mobile.data.sync.OutboxProcessor
import com.feniqo.mobile.data.sync.RealtimeSyncCoordinator
import com.feniqo.mobile.data.sync.RoomOutboxQueue
import com.feniqo.mobile.data.sync.V1OutboxOperationExecutor
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.SyncRepository
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
    ): AuthRepository = OfflineFirstAuthRepository(remoteDataSource, profileDao)

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
    fun provideOutboxProcessor(
        queue: OfflineWriteQueue,
        executor: V1OutboxOperationExecutor,
    ): OutboxProcessor = OutboxProcessor(RoomOutboxQueue(queue), executor)

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
    ): SyncRepository = OfflineFirstSyncRepository(
        authRepository = authRepository,
        initialRemoteSync = initialRemoteSync,
        outboxProcessor = outboxProcessor,
        incrementalRemoteSync = incrementalRemoteSync,
        offlineWriteQueue = queue,
        syncStateDao = syncStateDao,
        remoteSyncDao = remoteSyncDao,
        nowEpochMillisProvider = { System.currentTimeMillis() },
    )

    @Provides
    @Singleton
    fun provideRealtimeSyncCoordinator(
        authRepository: AuthRepository,
        invalidationSource: RealtimeInvalidationSource,
        syncRepository: SyncRepository,
    ): RealtimeSyncCoordinator = RealtimeSyncCoordinator(
        authRepository = authRepository,
        invalidationSource = invalidationSource,
        syncRepository = syncRepository,
    )
}

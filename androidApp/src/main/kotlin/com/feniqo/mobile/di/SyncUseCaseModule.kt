package com.feniqo.mobile.di

import com.feniqo.mobile.domain.repository.SyncRepository
import com.feniqo.mobile.domain.usecase.ObserveSyncOverviewUseCase
import com.feniqo.mobile.domain.usecase.RequestManualSyncUseCase
import com.feniqo.mobile.domain.usecase.RetryFailedSyncOperationsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncUseCaseModule {

    @Provides
    @Singleton
    fun provideObserveSyncOverviewUseCase(
        syncRepository: SyncRepository,
    ): ObserveSyncOverviewUseCase = ObserveSyncOverviewUseCase(syncRepository)

    @Provides
    @Singleton
    fun provideRequestManualSyncUseCase(
        syncRepository: SyncRepository,
    ): RequestManualSyncUseCase = RequestManualSyncUseCase(syncRepository)

    @Provides
    @Singleton
    fun provideRetryFailedSyncOperationsUseCase(
        syncRepository: SyncRepository,
    ): RetryFailedSyncOperationsUseCase = RetryFailedSyncOperationsUseCase(syncRepository)
}

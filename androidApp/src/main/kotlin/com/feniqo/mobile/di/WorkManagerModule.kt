package com.feniqo.mobile.di

import android.content.Context
import androidx.work.WorkManager
import com.feniqo.mobile.domain.sync.BackgroundSyncScheduler
import com.feniqo.mobile.sync.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * WorkManager ve arka plan senkronizasyon yöneticisini Hilt grafiğine bağlar.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkManagerModule {

    @Binds
    @Singleton
    abstract fun bindBackgroundSyncScheduler(
        scheduler: WorkManagerSyncScheduler,
    ): BackgroundSyncScheduler

    companion object {
        @Provides
        @Singleton
        fun provideWorkManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)
    }
}

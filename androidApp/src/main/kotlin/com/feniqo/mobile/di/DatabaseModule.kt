package com.feniqo.mobile.di

import android.content.Context
import com.feniqo.mobile.data.local.dao.BudgetDao
import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.dao.TagDao
import com.feniqo.mobile.data.local.dao.TransactionDao
import com.feniqo.mobile.data.local.dao.WorkspaceDao
import com.feniqo.mobile.data.local.database.AndroidDatabaseKeyManager
import com.feniqo.mobile.data.local.database.AndroidFeniqoDatabaseFactory
import com.feniqo.mobile.data.local.database.FeniqoDatabase
import com.feniqo.mobile.data.local.outbox.OfflineWriteQueue
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Uygulama boyunca tek bir şifreli Room veritabanı ve DAO grafiği sağlar. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context,
    ): AndroidDatabaseKeyManager = AndroidDatabaseKeyManager(context)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: AndroidDatabaseKeyManager,
    ): FeniqoDatabase = AndroidFeniqoDatabaseFactory(context).create(keyManager)

    @Provides fun provideProfileDao(database: FeniqoDatabase): ProfileDao = database.profileDao()
    @Provides fun provideWorkspaceDao(database: FeniqoDatabase): WorkspaceDao = database.workspaceDao()
    @Provides fun provideCategoryDao(database: FeniqoDatabase): CategoryDao = database.categoryDao()
    @Provides fun provideTransactionDao(database: FeniqoDatabase): TransactionDao = database.transactionDao()
    @Provides fun provideBudgetDao(database: FeniqoDatabase): BudgetDao = database.budgetDao()
    @Provides fun provideTagDao(database: FeniqoDatabase): TagDao = database.tagDao()
    @Provides fun provideSyncOperationDao(database: FeniqoDatabase): SyncOperationDao = database.syncOperationDao()
    @Provides fun provideLocalMutationDao(database: FeniqoDatabase): LocalMutationDao = database.localMutationDao()

    @Provides
    @Singleton
    fun provideOfflineWriteQueue(
        mutationDao: LocalMutationDao,
        operationDao: SyncOperationDao,
    ): OfflineWriteQueue = OfflineWriteQueue(mutationDao, operationDao)
}

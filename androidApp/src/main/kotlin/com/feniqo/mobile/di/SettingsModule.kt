package com.feniqo.mobile.di

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feniqo.mobile.data.backup.PersonalBackupExporter
import com.feniqo.mobile.domain.repository.CategoryRepository
import com.feniqo.mobile.domain.repository.TransactionRepository
import com.feniqo.mobile.domain.repository.UserSettingsRepository
import com.feniqo.mobile.settings.AndroidUserSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideUserSettingsRepository(
        @ApplicationContext context: Context,
    ): UserSettingsRepository = AndroidUserSettingsRepository(
        PreferenceDataStoreFactory.create {
            context.dataStoreFile("feniqo_user_settings.preferences_pb")
        },
    )

    @Provides
    @Singleton
    fun providePersonalBackupExporter(
        categoryRepository: CategoryRepository,
        transactionRepository: TransactionRepository,
    ): PersonalBackupExporter = PersonalBackupExporter(
        categoryRepository = categoryRepository,
        transactionRepository = transactionRepository,
    )
}

package com.feniqo.mobile.di

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feniqo.mobile.domain.repository.SecurityRepository
import com.feniqo.mobile.security.AndroidSecurityRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun provideSecurityRepository(
        @ApplicationContext context: Context,
    ): SecurityRepository = AndroidSecurityRepository(
        PreferenceDataStoreFactory.create {
            context.dataStoreFile("feniqo_security.preferences_pb")
        },
    )
}

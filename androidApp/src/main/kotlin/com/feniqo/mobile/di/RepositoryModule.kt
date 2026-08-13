package com.feniqo.mobile.di

import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.remote.auth.AuthRemoteDataSource
import com.feniqo.mobile.data.repository.OfflineFirstAuthRepository
import com.feniqo.mobile.domain.repository.AuthRepository
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
}

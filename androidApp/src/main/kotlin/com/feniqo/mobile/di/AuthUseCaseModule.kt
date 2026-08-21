package com.feniqo.mobile.di

import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.usecase.ObserveAuthSessionUseCase
import com.feniqo.mobile.domain.usecase.SignInUseCase
import com.feniqo.mobile.domain.usecase.SignUpUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Kimlik doğrulama domain use-case bağımlılıklarını sağlar.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthUseCaseModule {

    @Provides
    @Singleton
    fun provideObserveAuthSessionUseCase(
        authRepository: AuthRepository,
    ): ObserveAuthSessionUseCase = ObserveAuthSessionUseCase(authRepository)

    @Provides
    @Singleton
    fun provideSignInUseCase(
        authRepository: AuthRepository,
    ): SignInUseCase = SignInUseCase(authRepository)

    @Provides
    @Singleton
    fun provideSignUpUseCase(
        authRepository: AuthRepository,
    ): SignUpUseCase = SignUpUseCase(authRepository)
}

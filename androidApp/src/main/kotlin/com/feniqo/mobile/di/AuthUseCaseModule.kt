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

    @Provides
    @Singleton
    fun provideSendPasswordResetEmailUseCase(
        authRepository: AuthRepository,
    ): com.feniqo.mobile.domain.usecase.SendPasswordResetEmailUseCase =
        com.feniqo.mobile.domain.usecase.SendPasswordResetEmailUseCase(authRepository)

    @Provides
    @Singleton
    fun provideResendEmailConfirmationUseCase(
        authRepository: AuthRepository,
    ): com.feniqo.mobile.domain.usecase.ResendEmailConfirmationUseCase =
        com.feniqo.mobile.domain.usecase.ResendEmailConfirmationUseCase(authRepository)

    @Provides
    @Singleton
    fun provideResetPasswordUseCase(
        authRepository: AuthRepository,
    ): com.feniqo.mobile.domain.usecase.ResetPasswordUseCase =
        com.feniqo.mobile.domain.usecase.ResetPasswordUseCase(authRepository)

    @Provides
    @Singleton
    fun provideObserveRecoveryStateUseCase(
        authRepository: AuthRepository,
    ): com.feniqo.mobile.domain.usecase.ObserveRecoveryStateUseCase =
        com.feniqo.mobile.domain.usecase.ObserveRecoveryStateUseCase(authRepository)

    @Provides
    @Singleton
    fun provideHandleAuthDeepLinkUseCase(
        authRepository: AuthRepository,
    ): com.feniqo.mobile.domain.usecase.HandleAuthDeepLinkUseCase =
        com.feniqo.mobile.domain.usecase.HandleAuthDeepLinkUseCase(authRepository)

    @Provides
    @Singleton
    fun provideClearRecoveryStateUseCase(
        authRepository: AuthRepository,
    ): com.feniqo.mobile.domain.usecase.ClearRecoveryStateUseCase =
        com.feniqo.mobile.domain.usecase.ClearRecoveryStateUseCase(authRepository)
}

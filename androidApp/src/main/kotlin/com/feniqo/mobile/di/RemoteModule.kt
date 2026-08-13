package com.feniqo.mobile.di

import android.content.Context
import com.feniqo.mobile.BuildConfig
import com.feniqo.mobile.data.remote.auth.AuthRemoteDataSource
import com.feniqo.mobile.data.remote.auth.SupabaseAuthRemoteDataSource
import com.feniqo.mobile.data.remote.core.CoreRemoteDataSource
import com.feniqo.mobile.data.remote.core.SupabaseCoreRemoteDataSource
import com.feniqo.mobile.data.remote.storage.ReceiptStorageDataSource
import com.feniqo.mobile.data.remote.storage.SupabaseReceiptStorageDataSource
import com.feniqo.mobile.data.remote.supabase.AndroidSupabaseSessionManager
import com.feniqo.mobile.data.remote.supabase.FeniqoSupabaseClientFactory
import com.feniqo.mobile.data.remote.supabase.SupabaseConnectionConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SessionManager
import javax.inject.Singleton

/** Supabase istemcisini build configuration ile kurar; UI doğrudan bu istemciyi kullanmaz. */
@Module
@InstallIn(SingletonComponent::class)
object RemoteModule {

    @Provides
    @Singleton
    fun provideSupabaseConnectionConfig(): SupabaseConnectionConfig = SupabaseConnectionConfig(
        projectUrl = BuildConfig.SUPABASE_URL,
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    )

    @Provides
    @Singleton
    fun provideSupabaseSessionManager(
        @ApplicationContext context: Context,
    ): SessionManager = AndroidSupabaseSessionManager(context)

    @Provides
    @Singleton
    fun provideSupabaseClient(
        config: SupabaseConnectionConfig,
        sessionManager: SessionManager,
    ): SupabaseClient = FeniqoSupabaseClientFactory.create(config, sessionManager)

    @Provides
    @Singleton
    fun provideAuthRemoteDataSource(
        client: SupabaseClient,
    ): AuthRemoteDataSource = SupabaseAuthRemoteDataSource(client)

    @Provides
    @Singleton
    fun provideCoreRemoteDataSource(
        client: SupabaseClient,
    ): CoreRemoteDataSource = SupabaseCoreRemoteDataSource(client)

    @Provides
    @Singleton
    fun provideReceiptStorageDataSource(
        client: SupabaseClient,
    ): ReceiptStorageDataSource = SupabaseReceiptStorageDataSource(client)
}

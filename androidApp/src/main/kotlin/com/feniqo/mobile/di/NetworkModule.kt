package com.feniqo.mobile.di

import android.content.Context
import android.net.ConnectivityManager
import com.feniqo.mobile.domain.network.NetworkConnectivityObserver
import com.feniqo.mobile.network.AndroidNetworkConnectivityObserver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindNetworkConnectivityObserver(
        impl: AndroidNetworkConnectivityObserver,
    ): NetworkConnectivityObserver

    companion object {
        @Provides
        @Singleton
        fun provideConnectivityManager(
            @ApplicationContext context: Context,
        ): ConnectivityManager {
            return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }
    }
}

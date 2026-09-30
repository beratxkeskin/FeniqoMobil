package com.feniqo.mobile.diagnostic

import com.feniqo.mobile.data.local.database.FeniqoDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProbeDatabaseEntryPoint {
    fun database(): FeniqoDatabase
}

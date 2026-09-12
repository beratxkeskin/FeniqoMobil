package com.feniqo.mobile.di

import com.feniqo.mobile.ocr.MlKitReceiptOcrService
import com.feniqo.mobile.ocr.ReceiptOcrService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {
    @Binds
    @Singleton
    abstract fun bindReceiptOcrService(implementation: MlKitReceiptOcrService): ReceiptOcrService
}

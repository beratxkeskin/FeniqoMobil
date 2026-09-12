package com.feniqo.mobile.ocr

import android.content.Context
import android.net.Uri
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.ReceiptOcrResult
import com.feniqo.mobile.domain.validation.ReceiptOcrParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitReceiptOcrService @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReceiptOcrService {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(
        imageUri: Uri,
        currency: Currency,
    ): ReceiptOcrResult {
        val image = InputImage.fromFilePath(context, imageUri)
        val recognizedText = suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result.text)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }
        return ReceiptOcrParser.parse(recognizedText, currency)
    }
}

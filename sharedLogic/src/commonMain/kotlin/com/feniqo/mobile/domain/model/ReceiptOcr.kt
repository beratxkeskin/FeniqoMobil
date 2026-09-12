package com.feniqo.mobile.domain.model

enum class OcrCandidateConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

data class OcrCandidate<T>(
    val value: T,
    val confidence: OcrCandidateConfidence,
)

/**
 * Cihaz içi OCR sonucundan üretilen geçici form adaylarıdır.
 * Bu model bir Transaction değildir ve kullanıcı onayı olmadan kalıcı katmana yazılamaz.
 */
data class ReceiptOcrDraft(
    val merchantName: OcrCandidate<String>?,
    val total: OcrCandidate<Money>?,
    val transactionDate: OcrCandidate<LocalDate>?,
)

sealed interface ReceiptOcrResult {
    data class Candidates(val draft: ReceiptOcrDraft) : ReceiptOcrResult
    data object NoCandidates : ReceiptOcrResult
    data object InputTooLarge : ReceiptOcrResult
}

package com.feniqo.mobile.presentation.transaction

/**
 * Makbuz OCR ürün kabulü tamamlanana kadar kullanıcı girişlerini fail-closed tutar.
 * İç OCR implementasyonu korunur; yeniden yayınlama bu tek kapı ve kabul testleri üzerinden yapılır.
 */
const val RECEIPT_OCR_USER_ENTRY_ENABLED: Boolean = false

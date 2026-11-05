package com.feniqo.mobile.presentation.component

/**
 * Merchant adı veya işlem açıklamasından güvenli, Türkçe uyumlu ve tam Unicode code-point destekli
 * tek bir baş harf çıkarır.
 *
 * Sözleşme:
 * - İlk Unicode harf (BMP veya Supplementary Plane code point) kullanılır.
 * - Platformun [MerchantUnicodeSupport] sözleşmesiyle harf sınıflandırması yapılır; elle yazılmış
 *   aralık veya script tabloları kullanılmaz.
 * - Geçerli high+low surrogate çiftleri tek bir code point olarak taranır; surrogate çifti parçalanmaz.
 * - İzole/bozuk high veya low surrogate karakterler harf kabul edilmez ve güvenle atlanır.
 * - Baştaki rakamlar, boşluklar, semboller, noktalama ve emojiler harf olmadıkları için atlanır (ör. "101 Market" -> "M").
 * - Yalnız rakam, yalnız sembol, boş veya null girdilerde null döner (ör. "123" -> null).
 * - Türkçe i/ı harfleri için büyük dönüşümü yerel alfabeye uygun yapılır (istanbul -> İ, ışık -> I).
 * - Diğer tüm harflerde platformun Unicode uppercase dönüşümü uygulanır.
 */
object MerchantInitialHelper {

    fun extractInitial(label: String?): String? {
        val trimmed = label?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        var i = 0
        while (i < trimmed.length) {
            val c1 = trimmed[i]
            if (c1.isHighSurrogate()) {
                if (i + 1 < trimmed.length && trimmed[i + 1].isLowSurrogate()) {
                    val c2 = trimmed[i + 1]
                    val codePoint = 0x10000 + ((c1.code - 0xD800) shl 10) + (c2.code - 0xDC00)
                    if (MerchantUnicodeSupport.isLetter(codePoint)) {
                        val upperCodePoint = MerchantUnicodeSupport.uppercase(codePoint)
                        return MerchantUnicodeSupport.codePointToString(upperCodePoint)
                    }
                    i += 2
                } else {
                    // İzole high surrogate -> harf değil, atla
                    i++
                }
            } else if (c1.isLowSurrogate()) {
                // İzole low surrogate -> harf değil, atla
                i++
            } else {
                // BMP karakteri
                val codePoint = c1.code
                if (MerchantUnicodeSupport.isLetter(codePoint)) {
                    return when (c1) {
                        'i' -> "İ"
                        'ı' -> "I"
                        'İ' -> "İ"
                        'I' -> "I"
                        else -> {
                            val upperCodePoint = MerchantUnicodeSupport.uppercase(codePoint)
                            MerchantUnicodeSupport.codePointToString(upperCodePoint)
                        }
                    }
                }
                i++
            }
        }

        return null
    }
}

package com.feniqo.mobile.presentation.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * MerchantLogo bileşeninin görsel temsil türünü belirten tipli sonuç türü.
 */
enum class MerchantLogoVisualKind {
    PACKAGED_LOGO,
    CATEGORY_ICON,
    INITIAL,
    GENERIC_TRANSACTION_ICON,
}

/**
 * Saf fallback çözümleme kararının sonucu.
 */
data class MerchantLogoFallbackDecision(
    val kind: MerchantLogoVisualKind,
    val initial: String? = null,
    val iconVector: ImageVector? = null,
    val logoKey: String? = null,
) {
    /** Geriye dönük uyumluluk erişimcisi. */
    val categoryVector: ImageVector? get() = iconVector
}

/**
 * Saf, yan etkisiz ve çökmeyen MerchantLogo fallback karar motoru.
 */
object MerchantLogoFallbackResolver {
    /** Genel işlem/fiş ikonu (genel kategori ikonundan farklıdır). */
    val GENERIC_TRANSACTION_ICON_VECTOR: ImageVector = Icons.AutoMirrored.Outlined.ReceiptLong

    fun resolve(
        verifiedLogoKey: String?,
        packagedLogoAvailable: Boolean,
        categoryIconKey: String?,
        fallbackLabel: String?,
    ): MerchantLogoFallbackDecision {
        val trimmedLogoKey = verifiedLogoKey?.trim().orEmpty()

        // 1. Kural: packagedLogoAvailable true ve logo anahtarı non-blank ise -> PACKAGED_LOGO
        if (packagedLogoAvailable && trimmedLogoKey.isNotEmpty()) {
            return MerchantLogoFallbackDecision(
                kind = MerchantLogoVisualKind.PACKAGED_LOGO,
                logoKey = trimmedLogoKey,
            )
        }

        // 2. Kural: Geçerli semantik kategori anahtarı varsa -> CATEGORY_ICON
        val categoryVector = CategorySemanticIconResolver.resolveOrNull(categoryIconKey)
        if (categoryVector != null) {
            return MerchantLogoFallbackDecision(
                kind = MerchantLogoVisualKind.CATEGORY_ICON,
                iconVector = categoryVector,
            )
        }

        // 3. Kural: Kategori yok veya bilinmiyorsa, boş olmayan fallback etiketinden Türkçe uyumlu ilk harf -> INITIAL
        val initial = MerchantInitialHelper.extractInitial(fallbackLabel)
        if (initial != null) {
            return MerchantLogoFallbackDecision(
                kind = MerchantLogoVisualKind.INITIAL,
                initial = initial,
            )
        }

        // 4. Kural: Baş harf üretilemiyorsa -> GENERIC_TRANSACTION_ICON
        return MerchantLogoFallbackDecision(
            kind = MerchantLogoVisualKind.GENERIC_TRANSACTION_ICON,
            iconVector = GENERIC_TRANSACTION_ICON_VECTOR,
        )
    }
}

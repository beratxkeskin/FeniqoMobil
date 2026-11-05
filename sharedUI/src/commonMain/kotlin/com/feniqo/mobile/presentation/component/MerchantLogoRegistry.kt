package com.feniqo.mobile.presentation.component

import org.jetbrains.compose.resources.DrawableResource

/**
 * Kullanım hakkı ve lisansı doğrulanmış paketli logo kaynaklarının merkezi registry sözleşmesidir.
 *
 * Güvenlik ve telif kuralı gereği, henüz dağıtım izni kanıtlanmış gerçek ticari logo bulunmadığından
 * üretim kaydı tamamen boştur. Bilinmeyen veya null anahtarlar null döndürür ve fallback zincirini tetikler.
 */
object MerchantLogoRegistry {
    // Dağıtım hakkı doğrulandığında buraya tek bir merkezden eklenir:
    // private val packagedLogos = mapOf<String, DrawableResource>(...)
    private val packagedLogos: Map<String, DrawableResource> = emptyMap()

    fun resolve(verifiedLogoKey: String?): DrawableResource? {
        val key = verifiedLogoKey?.trim().orEmpty()
        if (key.isEmpty()) return null
        return packagedLogos[key]
    }

    fun hasLogo(verifiedLogoKey: String?): Boolean = resolve(verifiedLogoKey) != null

    fun size(): Int = packagedLogos.size
}

package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource

/**
 * Tüm işlem yüzeylerinde (Liste, Detay, Dashboard, Başarı, Yeni İşlem Formu) ortak kullanılan,
 * çökmeyen ve katı kurallı tekil MerchantLogo bileşeni.
 *
 * Aktif karar ve fallback zinciri:
 * 1. Paketli doğrulanmış logo (güven >= 75 ve varlığı registry'de kanıtlanmışsa)
 * 2. Başarılı Brandfetch uzak logo (canShowMerchantLogo=true, non-blank key, geçerli provider config)
 * 3. Kategori ikonu (semantik kategori anahtarı geçerliyse)
 * 4. Locale-aware baş harf (Türkçe İ/I duyarlı ilk Unicode harf)
 * 5. Genel işlem ikonu (fiş/receipt vektörü)
 *
 * Uzak Logo Kuralları:
 * - Uzak istek denemesi yalnızca canShowMerchantLogo == true, verifiedLogoKey non-blank ve
 *   remoteLogoResolver != null iken yapılır.
 * - Loading, hata (404/403/offline/decode failure) durumlarında ekran hata state'ine düşmez;
 *   doğrudan yerel fallback zinciri (kategori -> initial -> generic) kesintisiz görünür.
 * - Başarılı durumda uzak logo daire şeklinde kırpılarak render edilir.
 *
 * Erişilebilirlik (Semantics):
 * - [contentDescription] null ise: Bileşen tamamen dekoratiftir; içindeki Text/Icon/Image
 *   semantik ağaçtan bütünüyle temizlenir (clearAndSetSemantics).
 * - [contentDescription] non-null ise: Yalnızca verilen tekil açıklama okunur; altındaki
 *   görsel Text (initial) veya ikon etiketleri temizlenerek çift anons oluşması engellenir.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun MerchantLogo(
    verifiedLogoKey: String?,
    canShowMerchantLogo: Boolean,
    categoryIconKey: String?,
    categoryColor: Color,
    fallbackLabel: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
    remoteLogoResolver: RemoteMerchantLogoResolver? = LocalRemoteMerchantLogoResolver.current,
    remoteLogoRenderer: RemoteMerchantLogoRenderer = LocalRemoteMerchantLogoRenderer.current,
) {
    val normalizedLogoKey = verifiedLogoKey?.trim()?.takeIf { it.isNotEmpty() }
    val packagedLogoResource =
        if (canShowMerchantLogo && normalizedLogoKey != null) {
            MerchantLogoRegistry.resolve(normalizedLogoKey)
        } else {
            null
        }

    val remoteRequest =
        if (canShowMerchantLogo && normalizedLogoKey != null && remoteLogoResolver != null) {
            remember(canShowMerchantLogo, normalizedLogoKey, remoteLogoResolver) {
                remoteLogoResolver.resolve(normalizedLogoKey)
            }
        } else {
            null
        }

    val decision =
        MerchantLogoFallbackResolver.resolve(
            verifiedLogoKey = normalizedLogoKey,
            packagedLogoAvailable = packagedLogoResource != null,
            categoryIconKey = categoryIconKey,
            fallbackLabel = fallbackLabel,
        )

    val semanticsModifier =
        if (contentDescription == null) {
            Modifier.clearAndSetSemantics { }
        } else {
            Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
        }

    @Composable
    fun renderFallbackContent() {
        when (decision.kind) {
            MerchantLogoVisualKind.PACKAGED_LOGO -> {
                if (packagedLogoResource != null) {
                    Image(
                        painter = painterResource(packagedLogoResource),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(size)
                                .clip(CircleShape),
                    )
                }
            }
            MerchantLogoVisualKind.CATEGORY_ICON -> {
                val iconVector = decision.iconVector ?: CategorySemanticIconResolver.resolve(categoryIconKey)
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
            MerchantLogoVisualKind.INITIAL -> {
                val initialText = decision.initial.orEmpty()
                Text(
                    text = initialText,
                    color = categoryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.45f).sp,
                )
            }
            MerchantLogoVisualKind.GENERIC_TRANSACTION_ICON -> {
                val genericVector = decision.iconVector ?: MerchantLogoFallbackResolver.GENERIC_TRANSACTION_ICON_VECTOR
                Icon(
                    imageVector = genericVector,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        }
    }

    Box(
        modifier =
            modifier
                .size(size)
                .then(semanticsModifier)
                .background(categoryColor.copy(alpha = 0.12f), CircleShape)
                .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (packagedLogoResource != null) {
            renderFallbackContent()
        } else if (remoteRequest != null) {
            remoteLogoRenderer.render(
                request = remoteRequest,
                modifier =
                    Modifier
                        .size(size)
                        .clip(CircleShape),
                fallbackContent = { renderFallbackContent() },
            )
        } else {
            renderFallbackContent()
        }
    }
}

package com.feniqo.mobile.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Ortak ekran ve bileşen boşlukları için 4dp tabanlı ölçek. */
object FeniqoSpacing {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val ExtraLarge = 24.dp
    val Screen = 32.dp
}

/** Feniqo arayüzündeki yuvarlak köşe değerleri. */
object FeniqoRadius {
    val Small = 12.dp
    val Medium = 16.dp
    val Large = 24.dp
}

/** Finansal durumları ifade eden, iş kuralından bağımsız görsel renkler. */
object FeniqoStatusColor {
    val Success = FeniqoEmerald
    val Warning = PhoenixGold
    val Error = Color(0xFFBA1A1A)
    val Info = Color(0xFF2563EB)
}

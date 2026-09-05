package com.feniqo.mobile.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColorScheme = lightColorScheme(
    primary = FeniqoEmerald,
    onPrimary = Color(0xFF052E22),
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = PhoenixGold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = PhoenixOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF380D00),
    background = FeniqoLightBackground,
    onBackground = FeniqoTextPrimary,
    surface = FeniqoLightSurface,
    onSurface = FeniqoTextPrimary,
    surfaceVariant = Color(0xFFE7F1EB),
    onSurfaceVariant = FeniqoTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F7F3),
    surfaceContainer = Color(0xFFEAF3EE),
    surfaceContainerHigh = Color(0xFFE3EEE8),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFD7E2DC),
    error = FeniqoExpense,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColorScheme = darkColorScheme(
    primary = FeniqoEmeraldLight,
    onPrimary = Color(0xFF052E22),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = PhoenixGold,
    onSecondary = Color(0xFF422006),
    secondaryContainer = Color(0xFF573500),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFFFFB68D),
    onTertiary = Color(0xFF512300),
    tertiaryContainer = Color(0xFF713700),
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = FeniqoDarkBackground,
    onBackground = FeniqoTextPrimaryDark,
    surface = FeniqoDarkSurface,
    onSurface = FeniqoTextPrimaryDark,
    surfaceVariant = Color(0xFF1E3428),
    onSurfaceVariant = Color(0xFFB8C8BF),
    surfaceContainerLowest = Color(0xFF08110D),
    surfaceContainerLow = Color(0xFF101D16),
    surfaceContainer = FeniqoDarkSurface,
    surfaceContainerHigh = Color(0xFF1B3024),
    outline = Color(0xFF6F8377),
    outlineVariant = Color(0xFF2B4436),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val FeniqoTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Tutar gösterimlerinde rakamların kolonlar arasında hizalı kalmasını sağlar. */
val FeniqoTabularNumberStyle = TextStyle(fontFeatureSettings = "tnum")

/**
 * Feniqo'nun ortak Material 3 temasını uygular.
 * Tema tercihi sonraki adımda platforma özel olarak kalıcılaştırılacaktır.
 */
@Composable
fun FeniqoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = FeniqoTypography,
        content = content,
    )
}

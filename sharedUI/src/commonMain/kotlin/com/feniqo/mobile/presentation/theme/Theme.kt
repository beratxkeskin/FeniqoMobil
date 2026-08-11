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
    primary = Color(0xFF006C4E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF7FF8C4),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF735B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDF93),
    onSecondaryContainer = Color(0xFF241A00),
    tertiary = PhoenixOrange,
    onTertiary = Color.White,
    background = Color(0xFFF7FBF8),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF7FBF8),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF404943),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF63DBA7),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFF7FF8C4),
    secondary = Color(0xFFF4C900),
    onSecondary = Color(0xFF3C2F00),
    secondaryContainer = Color(0xFF574500),
    onSecondaryContainer = Color(0xFFFFDF93),
    tertiary = Color(0xFFFFB68D),
    onTertiary = Color(0xFF512300),
    background = Color(0xFF0B1410),
    onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF0B1410),
    onSurface = Color(0xFFDFE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val FeniqoTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

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

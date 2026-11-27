package com.manhwatranslate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Terracotta = Color(0xFFB4463B)
val TerracottaDeep = Color(0xFF7C2E27)
val Violet = Color(0xFF6B4E9B)
val Gold = Color(0xFFE0A458)
val Paper = Color(0xFFFBF7F4)
val Ink = Color(0xFF221C1A)
val MutedInk = Color(0xFF6F625C)

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7DCD6),
    onPrimaryContainer = Color(0xFF40100B),
    secondary = Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7DEF8),
    onSecondaryContainer = Color(0xFF231040),
    tertiary = Gold,
    onTertiary = Color(0xFF3B2600),
    tertiaryContainer = Color(0xFFFFE3B8),
    onTertiaryContainer = Color(0xFF2A1A00),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFDFC),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1E8E3),
    onSurfaceVariant = MutedInk,
    surfaceContainer = Color(0xFFF6EEEA),
    surfaceContainerHigh = Color(0xFFF0E5E0),
    outline = Color(0xFF9C8B84),
    outlineVariant = Color(0xFFDECFC8),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2A79D),
    onPrimary = Color(0xFF450C07),
    primaryContainer = Color(0xFF6B231C),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFCBB6F2),
    onSecondary = Color(0xFF2B1544),
    secondaryContainer = Color(0xFF432A63),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFFF0C68B),
    onTertiary = Color(0xFF3F2B00),
    tertiaryContainer = Color(0xFF5B4000),
    onTertiaryContainer = Color(0xFFFFE3B8),
    background = Color(0xFF120F12),
    onBackground = Color(0xFFF4E9E4),
    surface = Color(0xFF1A1519),
    onSurface = Color(0xFFF4E9E4),
    surfaceVariant = Color(0xFF3A2F2B),
    onSurfaceVariant = Color(0xFFD6C3BB),
    surfaceContainer = Color(0xFF201A1E),
    surfaceContainerHigh = Color(0xFF2A2226),
    outline = Color(0xFF9E8D86),
    outlineVariant = Color(0xFF4A3D38),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
)

@Composable
fun ManhwaTranslateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

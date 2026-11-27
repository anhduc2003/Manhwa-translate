package com.manhwatranslate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Terracotta = Color(0xFFB4463B)
val TerracottaDeep = Color(0xFF7C2E27)
val Violet = Color(0xFF6B4E9B)
val Gold = Color(0xFFE0A458)
val Paper = Color(0xFFFAF6F3)
val Ink = Color(0xFF221C1A)
val MutedInk = Color(0xFF6F625C)

/** Colours the Material roles do not carry: the hero gradient and the highlight used on images. */
@Immutable
data class AccentColors(
    val heroStart: Color,
    val heroMiddle: Color,
    val heroEnd: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val heroButton: Color,
    val onHeroButton: Color,
    val selection: Color,
    val imageScrim: Color,
)

private val LightAccents = AccentColors(
    heroStart = Color(0xFFB4463B),
    heroMiddle = Color(0xFF8E3B62),
    heroEnd = Color(0xFF5B4795),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xE6FFFFFF),
    heroButton = Color(0xFFFFFFFF),
    onHeroButton = Color(0xFFA63D33),
    selection = Color(0xFFE0A458),
    imageScrim = Color(0x00000000),
)

private val DarkAccents = AccentColors(
    heroStart = Color(0xFF6E2A24),
    heroMiddle = Color(0xFF552A41),
    heroEnd = Color(0xFF362A5A),
    onHero = Color(0xFFFBE9E5),
    onHeroMuted = Color(0xD9FBE9E5),
    heroButton = Color(0xFFF3E2DD),
    onHeroButton = Color(0xFF5C1C15),
    selection = Color(0xFFF0C68B),
    imageScrim = Color(0x33000000),
)

val LocalAccents = staticCompositionLocalOf { LightAccents }

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF8DDD8),
    onPrimaryContainer = Color(0xFF3F0F0A),
    secondary = Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE2FA),
    onSecondaryContainer = Color(0xFF241143),
    tertiary = Color(0xFF9A6A1F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7C4),
    onTertiaryContainer = Color(0xFF2C1C00),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFDFB),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF2E9E4),
    onSurfaceVariant = MutedInk,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF8F5),
    surfaceContainer = Color(0xFFF7EFEA),
    surfaceContainerHigh = Color(0xFFF1E7E1),
    surfaceContainerHighest = Color(0xFFEADFD9),
    outline = Color(0xFF9B8A83),
    outlineVariant = Color(0xFFE7D9D2),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF332B28),
    inverseOnSurface = Color(0xFFF9EFEA),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF4A79C),
    onPrimary = Color(0xFF4A1109),
    primaryContainer = Color(0xFF6B231C),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFCDB8F5),
    onSecondary = Color(0xFF2C1547),
    secondaryContainer = Color(0xFF432A63),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFFF0C68B),
    onTertiary = Color(0xFF3F2B00),
    tertiaryContainer = Color(0xFF5B4000),
    onTertiaryContainer = Color(0xFFFFE3B8),
    background = Color(0xFF0D0A0D),
    onBackground = Color(0xFFF4E9E4),
    surface = Color(0xFF1D171D),
    onSurface = Color(0xFFF4E9E4),
    surfaceVariant = Color(0xFF302730),
    onSurfaceVariant = Color(0xFFCDBBB3),
    surfaceContainerLowest = Color(0xFF090709),
    surfaceContainerLow = Color(0xFF171217),
    surfaceContainer = Color(0xFF221B22),
    surfaceContainerHigh = Color(0xFF2B232B),
    surfaceContainerHighest = Color(0xFF352C35),
    outline = Color(0xFFA6958E),
    outlineVariant = Color(0xFF4A3C44),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF4E9E4),
    inverseOnSurface = Color(0xFF332B28),
    scrim = Color(0xFF000000),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun ManhwaTranslateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAccents provides if (darkTheme) DarkAccents else LightAccents,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

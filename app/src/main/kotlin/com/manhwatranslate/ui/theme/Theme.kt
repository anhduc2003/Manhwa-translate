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

val Terracotta = Color(0xFFB54166)
val TerracottaDeep = Color(0xFF7A285B)
val Violet = Color(0xFF8B46FF)
val Gold = Color(0xFFE0A458)
val Paper = Color(0xFFFAF6F3)
val Ink = Color(0xFF221C1A)
val MutedInk = Color(0xFF6F625C)

/** Màu mở rộng ngoài bảng màu Material: Hero gradient, viền phát sáng, badge và bottom bar. */
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
    val glowStart: Color,
    val glowEnd: Color,
    val accentStart: Color,
    val accentEnd: Color,
    val statusSuccessBg: Color,
    val statusSuccessText: Color,
    val navBarBg: Color,
    val navIndicator: Color,
)

private val LightAccents = AccentColors(
    heroStart = Color(0xFF53338F),
    heroMiddle = Color(0xFF8E336E),
    heroEnd = Color(0xFFC44972),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xE8FFFFFF),
    heroButton = Color(0xFFFFF5F8),
    onHeroButton = Color(0xFF4A152E),
    selection = Color(0xFFE0A458),
    imageScrim = Color(0x00000000),
    glowStart = Color(0x668B46FF),
    glowEnd = Color(0x55EC4899),
    accentStart = Color(0xFF7C3AED),
    accentEnd = Color(0xFFA855F7),
    statusSuccessBg = Color(0xFFDCFCE7),
    statusSuccessText = Color(0xFF15803D),
    navBarBg = Color(0xF5FFFDFB),
    navIndicator = Color(0xFFEDE2FF),
)

private val DarkAccents = AccentColors(
    heroStart = Color(0xFF3E266B),
    heroMiddle = Color(0xFF7A285B),
    heroEnd = Color(0xFFB54166),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xDDFCEEF4),
    heroButton = Color(0xFFF7E4EA),
    onHeroButton = Color(0xFF3B1325),
    selection = Color(0xFFF0C68B),
    imageScrim = Color(0x26000000),
    glowStart = Color(0x789C5CFF),
    glowEnd = Color(0x59FF5E9C),
    accentStart = Color(0xFF7E3FF2),
    accentEnd = Color(0xFFA855F7),
    statusSuccessBg = Color(0xFF153427),
    statusSuccessText = Color(0xFF4ADE80),
    navBarBg = Color(0xF515101A),
    navIndicator = Color(0xFF42344D),
)

val LocalAccents = staticCompositionLocalOf { DarkAccents }

private val LightColors = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE2FF),
    onPrimaryContainer = Color(0xFF2B0E5C),
    secondary = Color(0xFFDB2777),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF3B146B),
    tertiary = Color(0xFF9A6A1F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7C4),
    onTertiaryContainer = Color(0xFF2C1C00),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFDFB),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF2EAF7),
    onSurfaceVariant = MutedInk,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF8FA),
    surfaceContainer = Color(0xFFF7EFF6),
    surfaceContainerHigh = Color(0xFFF0E6F2),
    surfaceContainerHighest = Color(0xFFE9DCEB),
    outline = Color(0xFF9B8A99),
    outlineVariant = Color(0xFFE3D5E6),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF2A2130),
    inverseOnSurface = Color(0xFFF9F1FA),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA86BFF),
    onPrimary = Color(0xFF1E083D),
    primaryContainer = Color(0xFF442473),
    onPrimaryContainer = Color(0xFFF0E2FF),
    secondary = Color(0xFFFF6B9E),
    onSecondary = Color(0xFF470B24),
    secondaryContainer = Color(0xFF2A1D38),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = Color(0xFFF0C68B),
    onTertiary = Color(0xFF3F2B00),
    tertiaryContainer = Color(0xFF5B4000),
    onTertiaryContainer = Color(0xFFFFE3B8),
    background = Color(0xFF0D0A0D),
    onBackground = Color(0xFFF6F1F8),
    surface = Color(0xFF19131F),
    onSurface = Color(0xFFF6F1F8),
    surfaceVariant = Color(0xFF271E31),
    onSurfaceVariant = Color(0xFFA99DB2),
    surfaceContainerLowest = Color(0xFF09070B),
    surfaceContainerLow = Color(0xFF140F19),
    surfaceContainer = Color(0xFF1D1624),
    surfaceContainerHigh = Color(0xFF261D2F),
    surfaceContainerHighest = Color(0xFF32273D),
    outline = Color(0xFF8C7D99),
    outlineVariant = Color(0xFF3B2F47),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF4ECF7),
    inverseOnSurface = Color(0xFF261E2B),
    scrim = Color(0xFF000000),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
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
        fontSize = 14.5.sp,
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
        fontSize = 11.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.3.sp,
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

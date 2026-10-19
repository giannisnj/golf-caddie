package com.golfcaddie.app.ui.theme

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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.golfcaddie.app.R

/*
 * The redesign's theme (Golf Caddie design canvas, October 2026). Light is "Sunlight", built for
 * reading in direct sun; dark is "Evening". Green is the caddie talking, amber means watch out,
 * red means something is broken, blue is you (and water).
 */

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

/**
 * Every style uses tabular figures, so numbers don't jiggle sideways as they update, and centres
 * text in its line height the way the design's CSS does (the hero is 104 sp in an 86 sp line).
 */
private fun style(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
    fontFamily = Barlow,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.em,
    fontFeatureSettings = "tnum",
    lineHeightStyle = CssLineHeight,
)

val CssLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** The hero distance: 104/86 bold. Not a Material role, so it lives here. */
val HeroDistance = style(104, 86, FontWeight.Bold, -0.02)

/** The type scale, sized for a 2–3 second glance: big numbers, medium-weight body, no thin weights. */
val GolfTypography = Typography(
    displayLarge = HeroDistance,
    displayMedium = style(64, 58, FontWeight.Bold, -0.02),
    displaySmall = style(36, 40, FontWeight.Bold), // club name
    headlineLarge = style(30, 34, FontWeight.Bold),
    headlineMedium = style(30, 34, FontWeight.SemiBold), // front / back, plays like
    headlineSmall = style(24, 30, FontWeight.Bold), // sheet titles
    titleLarge = style(26, 30, FontWeight.Bold), // hole, tab titles
    titleMedium = style(18, 24, FontWeight.Bold), // card titles
    titleSmall = style(17, 22, FontWeight.Bold),
    bodyLarge = style(17, 22, FontWeight.Medium),
    bodyMedium = style(16, 21, FontWeight.Medium), // supporting
    bodySmall = style(14, 19, FontWeight.Medium), // list secondary
    labelLarge = style(18, 22, FontWeight.Bold), // buttons
    labelMedium = style(13, 16, FontWeight.Bold, 0.08), // overlines, shown in capitals
    labelSmall = style(11, 14, FontWeight.Medium), // map credits
)

/** Corner radii: tags 6, small chips 10, fields and grid buttons 16, cards 20, sheets 28. */
val GolfShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F4D3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2ECDD),
    onPrimaryContainer = Color(0xFF062A1E),
    secondary = Color(0xFF45604F),
    secondaryContainer = Color(0xFFDDE9E0),
    onSecondaryContainer = Color(0xFF13261B),
    tertiary = Color(0xFF1C5D8E),
    tertiaryContainer = Color(0xFFD3E6F6),
    onTertiaryContainer = Color(0xFF062033),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFADEDB),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFEEEDE7),
    onBackground = Color(0xFF101511),
    surface = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEEEDE7),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F6F2),
    surfaceContainer = Color(0xFFF2F1EC),
    surfaceContainerHigh = Color(0xFFE9E8E2),
    surfaceContainerHighest = Color(0xFFDEDDD6),
    surfaceVariant = Color(0xFFE9E8E2),
    onSurface = Color(0xFF101511),
    onSurfaceVariant = Color(0xFF3A433D),
    outline = Color(0xFF6A736C),
    outlineVariant = Color(0xFFD3D7D0),
    inverseSurface = Color(0xFF1B221D),
    inverseOnSurface = Color(0xFFEEF2EC),
    inversePrimary = Color(0xFF8ED8AC),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8ED8AC),
    onPrimary = Color(0xFF00381F),
    primaryContainer = Color(0xFF1B5440),
    onPrimaryContainer = Color(0xFFBDF2D2),
    secondary = Color(0xFFB4CDBC),
    secondaryContainer = Color(0xFF2C3F33),
    onSecondaryContainer = Color(0xFFD0E9D8),
    tertiary = Color(0xFF9DCBF4),
    tertiaryContainer = Color(0xFF154A72),
    onTertiaryContainer = Color(0xFFD2E7FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B100D),
    onBackground = Color(0xFFE9EEE9),
    surface = Color(0xFF141A16),
    surfaceDim = Color(0xFF0B100D),
    surfaceBright = Color(0xFF29322C),
    surfaceContainerLowest = Color(0xFF0B100D),
    surfaceContainerLow = Color(0xFF161C18),
    surfaceContainer = Color(0xFF181F1A),
    surfaceContainerHigh = Color(0xFF1F2721),
    surfaceContainerHighest = Color(0xFF29322C),
    surfaceVariant = Color(0xFF1F2721),
    onSurface = Color(0xFFE9EEE9),
    onSurfaceVariant = Color(0xFFB9C3BB),
    outline = Color(0xFF87918A),
    outlineVariant = Color(0xFF333C36),
    inverseSurface = Color(0xFFE3E9E4),
    inverseOnSurface = Color(0xFF16201A),
    inversePrimary = Color(0xFF0F4D3A),
)

/** The roles Material 3 doesn't have: hazard amber, "good" green, the hole bar, the club card, the map. */
@Immutable
data class GolfColors(
    val dark: Boolean,
    val hazard: Color,
    val hazardContainer: Color,
    val onHazardContainer: Color,
    val hazardBorder: Color,
    val hazardTitle: Color,
    val hazardChip: Color,
    val good: Color,
    val goodContainer: Color,
    val onGoodContainer: Color,
    val flag: Color,
    val holeBar: Color,
    val onHoleBar: Color,
    val onHoleBarVariant: Color,
    val holeBarButton: Color,
    val clubCard: Color,
    val onClubCard: Color,
    val onClubCardVariant: Color,
    val clubChip: Color,
    val navBar: Color,
    val navIndicator: Color,
    val control: Color,
    val onControl: Color,
    val controlSelected: Color,
    val onControlSelected: Color,
    val shadow: Color,
    // The course plan, drawn by the app from OpenStreetMap shapes.
    val planRough: Color,
    val planFairway: Color,
    val planGreen: Color,
    val planBunker: Color,
    val planBunkerEdge: Color,
    val planWater: Color,
    val planWaterEdge: Color,
    val planTrees: Color,
    // Overlays on a light base (street, light plan); photos and the dark plan use white.
    val lineOnLight: Color,
    val casingOnLight: Color,
    val inPlayOnLight: Color,
    val inPlayFillOnLight: Color,
)

val PlayerBlue = Color(0xFF1A73E8)

private val LightGolf = GolfColors(
    dark = false,
    hazard = Color(0xFF8A3B00), hazardContainer = Color(0xFFFFB547), onHazardContainer = Color(0xFF241300),
    hazardBorder = Color(0xFFF2A33A), hazardTitle = Color(0xFF241300), hazardChip = Color(0x1A241300),
    good = Color(0xFF17703A), goodContainer = Color(0xFFCCEFD5), onGoodContainer = Color(0xFF05290F),
    flag = Color(0xFFC62828),
    holeBar = Color(0xFF0F4D3A), onHoleBar = Color(0xFFFFFFFF), onHoleBarVariant = Color(0xFFCFE6DA),
    holeBarButton = Color(0x24FFFFFF),
    clubCard = Color(0xFF0F4D3A), onClubCard = Color(0xFFFFFFFF), onClubCardVariant = Color(0xFFCFE6DA),
    clubChip = Color(0x29FFFFFF),
    navBar = Color(0xFFF2F1EC), navIndicator = Color(0xFFD2ECDD),
    control = Color(0xFFFFFFFF), onControl = Color(0xFF101511),
    controlSelected = Color(0xFF0F4D3A), onControlSelected = Color(0xFFFFFFFF),
    shadow = Color(0x40000000),
    planRough = Color(0xFFCBDAB1), planFairway = Color(0xFFA2CC86), planGreen = Color(0xFF74C17A),
    planBunker = Color(0xFFF3E4B5), planBunkerEdge = Color(0xFFC2A563),
    planWater = Color(0xFF8FC6E8), planWaterEdge = Color(0xFF4E93C3), planTrees = Color(0xFF86A971),
    lineOnLight = Color(0xFF0F2A1F), casingOnLight = Color(0xBFFFFFFF),
    inPlayOnLight = Color(0xFFC25E00), inPlayFillOnLight = Color(0x4DFFA028),
)

private val DarkGolf = GolfColors(
    dark = true,
    hazard = Color(0xFFFFB547), hazardContainer = Color(0xFF3B2300), onHazardContainer = Color(0xFFFFE2BC),
    hazardBorder = Color(0xFFFFB547), hazardTitle = Color(0xFFFFB547), hazardChip = Color(0x29FFB547),
    good = Color(0xFF7FDC9B), goodContainer = Color(0xFF0F4426), onGoodContainer = Color(0xFFB8F5C9),
    flag = Color(0xFFFF8A80),
    holeBar = Color(0xFF102A20), onHoleBar = Color(0xFFE9EEE9), onHoleBarVariant = Color(0xFFA9C2B4),
    holeBarButton = Color(0x1AFFFFFF),
    clubCard = Color(0xFF1B5440), onClubCard = Color(0xFFE9F8EE), onClubCardVariant = Color(0xFFA9D3BA),
    clubChip = Color(0x1FFFFFFF),
    navBar = Color(0xFF0F1411), navIndicator = Color(0xFF1B5440),
    control = Color(0xFF1F2721), onControl = Color(0xFFE9EEE9),
    controlSelected = Color(0xFF8ED8AC), onControlSelected = Color(0xFF00381F),
    shadow = Color(0x80000000),
    planRough = Color(0xFF18241B), planFairway = Color(0xFF24452C), planGreen = Color(0xFF2F6A3B),
    planBunker = Color(0xFF6E6040), planBunkerEdge = Color(0xFFA88F58),
    planWater = Color(0xFF163C57), planWaterEdge = Color(0xFF3C7DAE), planTrees = Color(0xFF10281A),
    lineOnLight = Color(0xFF0F2A1F), casingOnLight = Color(0xBFFFFFFF),
    inPlayOnLight = Color(0xFFC25E00), inPlayFillOnLight = Color(0x4DFFA028),
)

private val LocalGolfColors = staticCompositionLocalOf { LightGolf }

/** `MaterialTheme.golf.hazardContainer` and friends, next to the Material roles. */
val MaterialTheme.golf: GolfColors
    @Composable @ReadOnlyComposable get() = LocalGolfColors.current

@Composable
fun GolfCaddieTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGolfColors provides if (dark) DarkGolf else LightGolf) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = GolfTypography,
            shapes = GolfShapes,
            content = content,
        )
    }
}

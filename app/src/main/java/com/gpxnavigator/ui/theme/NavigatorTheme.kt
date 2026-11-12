package com.gpxnavigator.ui.theme

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.gpxnavigator.domain.model.AppTheme

@Immutable
data class MapPalette(
    val background: String,
    val landcover: String,
    val landuse: String,
    val water: String,
    val buildings: String,
    val transit: String,
    val roadBorder: String,
    val roads: String,
    val boundaries: String,
    val labels: String,
    val labelHalo: String,
    val track: String,
    val location: String,
    val locationHalo: String,
    val trackPassed: String = "#687783",
    val trackOutline: String = "#FFFFFF",
    val majorRoads: String = roads,
    val trails: String = roadBorder,
    val locationOutline: String = "#FFFFFF",
    val transportDashLength: Float = 2f,
)

val SoftMapPalette = MapPalette(
    background = "#F5F3EB", landcover = "#E1E9D8", landuse = "#E8EBDF",
    water = "#BCDCE7", buildings = "#D9D9D2", transit = "#777F78",
    roadBorder = "#9B9F96", roads = "#FFFFFF", boundaries = "#7B7D88",
    labels = "#27313A", labelHalo = "#FFFFFF", track = "#B4232D",
    location = "#0057A8", locationHalo = "rgba(0, 87, 168, 0.18)",
)

val CyberMapPalette = SoftMapPalette.copy(
    landcover = "#DEE8D3", water = "#B5D9E6", buildings = "#D8D3CB",
    roadBorder = "#99958E", labels = "#1C1C1C", track = "#F2055C",
)

@Immutable
data class NavigatorPalette(
    val scheme: ColorScheme,
    val map: MapPalette,
    val action: Color,
    val onAction: Color,
    val selectionBorder: Color,
    val maneuverBackground: Color,
    val maneuverArrow: Color,
    val followActive: Color,
    val onFollowActive: Color,
    val gpsActive: Color = Color(0xFF0F5B38),
    val warning: Color = Color(0xFF6F4500),
    val danger: Color = Color(0xFF9F1D2D),
    val maneuverDistance: Color = Color.White,
    val stopAction: Color = danger,
    val onStopAction: Color = Color.White,
    val maneuverContent: Color = Color.White,
    val instrumentBorder: Color = scheme.outlineVariant,
    val viewBackground: Color = scheme.surface,
    val onView: Color = scheme.onSurface,
    val homeStopAction: Color = stopAction,
    val onHomeStopAction: Color = onStopAction,
    val recordAction: Color = action,
    val onRecordAction: Color = Color.Black,
)

private val SoftScheme = lightColorScheme(
    primary = Color.Black, onPrimary = Color(0xFFFDFCFF),
    primaryContainer = Color(0xFFF2ACE0), onPrimaryContainer = Color.Black,
    secondary = Color.Black, onSecondary = Color(0xFFFDFCFF),
    secondaryContainer = Color(0xFF9AE1E5), onSecondaryContainer = Color.Black,
    tertiary = Color.Black, onTertiary = Color(0xFFFDFCFF),
    tertiaryContainer = Color(0xFFFCFF8B), onTertiaryContainer = Color.Black,
    background = Color(0xFFF0EEF2), onBackground = Color.Black,
    surface = Color(0xFFFDFCFF), onSurface = Color.Black,
    surfaceVariant = Color(0xFFEFEDF1), onSurfaceVariant = Color(0xFF202020),
    surfaceTint = Color(0xFFFDFCFF),
    surfaceBright = Color(0xFFFDFCFF), surfaceDim = Color(0xFFE5E3E7),
    surfaceContainerLowest = Color(0xFFFDFCFF), surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFDFCFF), surfaceContainerHigh = Color(0xFFF4F2F6),
    surfaceContainerHighest = Color(0xFFEFEDF1),
    error = Color(0xFF650A18), onError = Color.White,
    errorContainer = Color(0xFF9F1D2D), onErrorContainer = Color.White,
    outline = Color(0xFF505050), outlineVariant = Color(0xFF505050),
    inverseSurface = Color.Black, inverseOnSurface = Color(0xFFFDFCFF),
    inversePrimary = Color(0xFFFCFF8B), scrim = Color.Black,
)

private val CyberScheme = darkColorScheme(
    primary = Color(0xFFC5F544), onPrimary = Color(0xFF1C1C1C),
    primaryContainer = Color(0xFF2D0B1B), onPrimaryContainer = Color.White,
    secondary = Color(0xFFB6E536), onSecondary = Color(0xFF1C1C1C),
    secondaryContainer = Color(0xFF34343C), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFF2055C), onTertiary = Color.Black,
    tertiaryContainer = Color(0xFFF2055C), onTertiaryContainer = Color.Black,
    background = Color(0xFF121218), onBackground = Color.White,
    surface = Color(0xFF1C1C24), onSurface = Color.White,
    surfaceVariant = Color(0xFF34343C), onSurfaceVariant = Color(0xFFE0E0E0),
    surfaceTint = Color(0xFF1C1C24),
    surfaceBright = Color(0xFF34343C), surfaceDim = Color(0xFF121218),
    surfaceContainerLowest = Color(0xFF121218), surfaceContainerLow = Color(0xFF292931),
    surfaceContainer = Color(0xFF1C1C24), surfaceContainerHigh = Color(0xFF292931),
    surfaceContainerHighest = Color(0xFF34343C),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF9F1D2D), onErrorContainer = Color.White,
    outline = Color(0xFFA8ADA3), outlineVariant = Color(0xFF7E8379),
    inverseSurface = Color(0xFFF5F6F2), inverseOnSurface = Color(0xFF1C1C1C),
    inversePrimary = Color.Black, scrim = Color.Black,
)

val SoftPalette = NavigatorPalette(
    scheme = SoftScheme, map = SoftMapPalette,
    action = Color(0xFFF2ACE0), onAction = Color.Black,
    selectionBorder = SoftScheme.outline,
    maneuverBackground = Color(0xFFFCFF8B), maneuverArrow = Color.Black,
    maneuverDistance = Color.Black, maneuverContent = Color.Black,
    followActive = Color(0xFFFCFF8B), onFollowActive = Color.Black,
    instrumentBorder = SoftScheme.outline,
    viewBackground = SoftScheme.secondaryContainer, onView = Color.Black,
    homeStopAction = SoftScheme.secondaryContainer, onHomeStopAction = Color.Black,
    recordAction = Color(0xFF9AE1E5),
)

val CyberPalette = NavigatorPalette(
    scheme = CyberScheme, map = CyberMapPalette,
    action = Color(0xFFF2055C), onAction = Color.Black,
    selectionBorder = Color(0xFFF2055C),
    maneuverBackground = Color(0xFF050509), maneuverArrow = Color(0xFFF2055C),
    followActive = Color(0xFFB6E536), onFollowActive = Color(0xFF1C1C1C),
    stopAction = Color(0xFFB6E536), onStopAction = Color(0xFF1C1C1C),
    recordAction = Color(0xFFB6E536),
)

val LocalNavigatorPalette = staticCompositionLocalOf { SoftPalette }
val LocalAppTheme = staticCompositionLocalOf { AppTheme.SOFT }

fun paletteFor(theme: AppTheme): NavigatorPalette = when (theme) {
    AppTheme.SOFT -> SoftPalette
    AppTheme.CYBER -> CyberPalette
}

@Composable
fun NavigatorTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val palette = paletteFor(theme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.let { activity ->
                WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = theme == AppTheme.SOFT
                    isAppearanceLightNavigationBars = theme == AppTheme.SOFT
                }
            }
        }
    }
    CompositionLocalProvider(LocalNavigatorPalette provides palette, LocalAppTheme provides theme) {
        MaterialTheme(colorScheme = palette.scheme, content = content)
    }
}

@Composable
fun navigatorButtonColors() = LocalNavigatorPalette.current.let { palette ->
    ButtonDefaults.buttonColors(containerColor = palette.action, contentColor = palette.onAction,
        disabledContainerColor = palette.scheme.surfaceVariant,
        disabledContentColor = palette.scheme.onSurfaceVariant)
}

@Composable
fun navigatorButtonBorder(): BorderStroke? = if (LocalAppTheme.current == AppTheme.SOFT)
    BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null

@Composable
fun navigatorCardColors() = MaterialTheme.colorScheme.let { colors ->
    CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow,
        contentColor = colors.onSurface, disabledContainerColor = colors.surfaceVariant,
        disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.6f))
}

package com.gpxnavigator.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.gpxnavigator.domain.model.MapAppearance
import com.gpxnavigator.ui.map.navigationMapPalette
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteContrastTest {
    @Test fun recordingAndNavigationHaveDistinctReadableActionsInBothThemes() {
        for (palette in listOf(SoftPalette, CyberPalette)) {
            assertNotEquals(palette.action, palette.recordAction)
            requireContrast("Record GPX label", palette.onRecordAction, palette.recordAction, 4.5)
        }
    }
    private fun contrast(foreground: Color, background: Color): Double {
        val a = foreground.luminance().toDouble()
        val b = background.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun requireContrast(label: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrast(foreground, background)
        assertTrue("$label: $ratio < $minimum", ratio >= minimum)
    }

    @Test fun essentialReadoutsAndAlertsKeepEnhancedContrastInBothThemes() {
        for (palette in listOf(SoftPalette, CyberPalette)) {
            val scheme = palette.scheme
            requireContrast("Surface", scheme.onSurface, scheme.surface, 7.0)
            requireContrast("Instrument value", scheme.primary, scheme.surfaceContainer, 7.0)
            requireContrast("Instrument text", scheme.onSurface, scheme.surfaceContainer, 7.0)
            requireContrast("Instrument label", scheme.onSurfaceVariant, scheme.surfaceContainer, 7.0)
            requireContrast("Background", scheme.onBackground, scheme.background, 7.0)
            requireContrast("Selection", scheme.onPrimaryContainer, scheme.primaryContainer, 7.0)
            requireContrast("Follow", palette.onFollowActive, palette.followActive, 7.0)
            requireContrast("Stop", palette.onStopAction, palette.stopAction, 7.0)
            requireContrast("Home stop", palette.onHomeStopAction, palette.homeStopAction, 7.0)
            requireContrast("GPS", Color.White, palette.gpsActive, 7.0)
            requireContrast("Warning", Color.White, palette.warning, 7.0)
            requireContrast("Error", scheme.onErrorContainer, scheme.errorContainer, 7.0)
            requireContrast("Visuale", palette.onView, palette.viewBackground, 7.0)
            requireContrast("Maneuver text", palette.maneuverContent, palette.maneuverBackground, 7.0)
            requireContrast("Maneuver distance", palette.maneuverDistance, palette.maneuverBackground, 7.0)
        }
    }

    @Test fun fluorescentGraphicsAndActionLabelsRemainReadable() {
        assertEquals(Color.Black, CyberPalette.onAction)
        assertEquals(Color.Black, CyberPalette.scheme.onTertiaryContainer)
        for (palette in listOf(SoftPalette, CyberPalette)) {
            // Pastel and fluorescent action backgrounds both need their own contrasting labels.
            requireContrast("Action label", palette.onAction, palette.action, 4.5)
            requireContrast("Maneuver arrow", palette.maneuverArrow, palette.maneuverBackground, 4.5)
            requireContrast("Tertiary label", palette.scheme.onTertiaryContainer, palette.scheme.tertiaryContainer, 4.5)
        }
    }

    @Test fun secondaryTextRemainsReadableOnCardsAndDialogs() {
        for (palette in listOf(SoftPalette, CyberPalette)) {
            val scheme = palette.scheme
            for (surface in listOf(scheme.surface, scheme.surfaceContainerLow, scheme.surfaceContainerHigh, scheme.surfaceVariant)) {
                requireContrast("Secondary text", scheme.onSurfaceVariant, surface, 4.5)
                requireContrast("Interactive text", scheme.primary, surface, 4.5)
            }
            requireContrast("Error on selected card", scheme.error, scheme.primaryContainer, 7.0)
        }
    }

    @Test fun trackAndLocationContrastWithTheMainVectorMapFills() {
        for (map in listOf(SoftMapPalette, CyberMapPalette)) {
            fun color(value: String) = Color(value.removePrefix("#").toLong(16) or 0xFF000000)
            for (surface in listOf(map.background, map.landcover, map.landuse, map.water, map.buildings)) {
                // The rendered outline separates the fluorescent route from similarly bright map fills.
                val routeContrast = maxOf(contrast(color(map.track), color(surface)),
                    contrast(color(map.track), color(map.trackOutline)))
                assertTrue("GPX/outline over $surface: $routeContrast", routeContrast >= 3.0)
                requireContrast("Passed GPX over $surface", color(map.trackPassed), color(surface), 3.0)
                requireContrast("Location over $surface", color(map.location), color(surface), 3.0)
            }
        }
    }

    @Test fun navigationPalettesSeparateRoadsLabelsAndRoutesFromTheMapFills() {
        fun color(value: String) = Color(value.removePrefix("#").toLong(16) or 0xFF000000)
        for (appearance in MapAppearance.entries) {
            val map = navigationMapPalette(appearance)
            val dark = appearance == MapAppearance.DARK
            val visibleRoad = if (appearance == MapAppearance.LIGHT) map.roadBorder else map.roads
            for (fill in listOf(map.background, map.landcover, map.landuse, map.water, map.buildings)) {
                // Night roads are subdued so the navigation route has higher visual priority.
                requireContrast("Road on $fill", color(visibleRoad), color(fill), if (dark) 1.8 else 3.0)
                val mainRoad = if (appearance == MapAppearance.LIGHT) map.roadBorder else map.majorRoads
                requireContrast("Main road on $fill", color(mainRoad), color(fill), if (dark) 1.8 else 3.0)
                requireContrast("Trail on $fill", color(map.trails), color(fill), 3.0)
                requireContrast("Label on $fill", color(map.labels), color(fill), 7.0)
                requireContrast("GPX on $fill", color(map.track), color(fill), 3.0)
                requireContrast("Passed GPX on $fill", color(map.trackPassed), color(fill), if (dark) 2.0 else 3.0)
                requireContrast("GPS on $fill", color(map.location), color(fill), 3.0)
            }
            requireContrast("Label halo", color(map.labels), color(map.labelHalo), 7.0)
            requireContrast("GPX outline", color(map.track), color(map.trackOutline), 3.0)
            requireContrast("GPS outline", color(map.location), color(map.locationOutline), 3.0)
            if (dark) {
                for (line in listOf(map.roads, map.majorRoads, map.trackPassed)) {
                    requireContrast("Future route over $line", color(map.track), color(line), 2.0)
                    assertTrue(color(map.track).luminance() > color(line).luminance())
                }
                requireContrast("Night roads over ground", color(map.roads), color(map.background), 3.0)
            } else {
                requireContrast("Water and vegetation", color(map.water), color(map.landcover), 1.5)
                requireContrast("Future and passed route", color(map.track), color(map.trackPassed), 1.25)
                assertTrue(color(map.trackPassed).luminance() > color(map.track).luminance())
            }
        }
    }
}

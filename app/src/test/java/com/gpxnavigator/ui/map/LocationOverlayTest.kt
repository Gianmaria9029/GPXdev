package com.gpxnavigator.ui.map

import com.gpxnavigator.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.maplibre.geojson.Point

class LocationOverlayTest {
    private fun location(bearing: Float?, speed: Float?) =
        LocationPoint(43.77, 11.25, 5f, speed, bearing, null)

    @Test fun movingPositionIncludesBearingAndCorrectCoordinates() {
        val feature = locationFeature(location(90f, 2f))
        val point = feature.geometry() as Point
        assertEquals(43.77, point.latitude(), 0.0)
        assertEquals(11.25, point.longitude(), 0.0)
        assertEquals(90.0, feature.getNumberProperty("bearing").toDouble(), 0.0)
    }

    @Test fun stationaryAndUnknownDirectionUseTheCircle() {
        assertFalse(locationFeature(location(120f, 0f)).hasProperty("bearing"))
        assertFalse(locationFeature(location(null, 2f)).hasProperty("bearing"))
        assertFalse(locationFeature(location(Float.NaN, 2f)).hasProperty("bearing"))
    }

    @Test fun bearingWrapsAndDoesNotRequireSpeedWhenUnavailable() {
        assertEquals(270.0, locationFeature(location(-90f, null))
            .getNumberProperty("bearing").toDouble(), 0.0)
        assertEquals(0.0, locationFeature(location(360f, 3f))
            .getNumberProperty("bearing").toDouble(), 0.0)
    }
}

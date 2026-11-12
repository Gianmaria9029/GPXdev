package com.gpxnavigator.qa

import android.app.Application
import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import com.gpxnavigator.ui.map.trackExtent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLngBounds
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class QaAntimeridianMapBoundsTest {
    @Test fun mapLibreAcceptsUnwrappedLocalBoundsForTheOverview() {
        val extent = trackExtent(Track("QA", listOf(TrackSegment(listOf(
            TrackPoint(0.0, 179.999), TrackPoint(0.001, -179.999),
        )))))!!
        val bounds = LatLngBounds.from(extent.north, extent.east, extent.south, extent.west)
        assertEquals(0.002, bounds.longitudeSpan, 0.0000001)
        assertEquals(180.0, bounds.center.longitude, 0.0000001)
        assertEquals(0.0005, bounds.center.latitude, 0.0000001)
    }
}

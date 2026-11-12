package com.gpxnavigator.qa

import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import com.gpxnavigator.ui.map.trackExtent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class QaUiTrackExtentTest {
    @Test fun ordinaryTrackOverviewKeepsTheLocalExtent() {
        val extent = trackExtent(Track("QA local", listOf(TrackSegment(listOf(
            TrackPoint(43.0, 11.0), TrackPoint(43.001, 11.001),
        )))))!!
        assertEquals(0.001, extent.east - extent.west, 0.0000001)
    }

    @Test fun shortTrackCrossingTheAntimeridianKeepsALocalOverview() {
        val extent = trackExtent(Track("QA antimeridian", listOf(TrackSegment(listOf(
            TrackPoint(0.0, 179.999), TrackPoint(0.001, -179.999),
        )))))!!
        println("QA_EXTENT_REPRO west=${extent.west}; east=${extent.east}; longitudeSpan=${extent.east - extent.west}")
        assertTrue("A roughly 250 m track crossing 180 degrees must not produce a nearly 360-degree overview",
            extent.east - extent.west < 1.0)
    }

    private fun track(vararg longitudes: Double) = Track("QA extent", listOf(
        TrackSegment(longitudes.map { TrackPoint(0.0, it) })))

    @Test fun boundsDoNotDependOnTravelDirectionAndKeepGreenwichLocal() {
        assertEquals(trackExtent(track(179.999, -179.999)), trackExtent(track(-179.999, 179.999)))
        val greenwich = trackExtent(track(-0.001, 0.001))!!
        assertEquals(-0.001, greenwich.west, 0.0000001)
        assertEquals(0.001, greenwich.east, 0.0000001)
    }

    @Test fun equivalentDatelineCoordinatesAreOneLocationAndEmptyTrackHasNoBounds() {
        assertTrue(trackExtent(track(180.0, -180.0))!!.isSingleLocation)
        assertNull(trackExtent(track()))
    }

    @Test fun disconnectedSegmentsNearTheDatelineStillHaveLocalBounds() {
        val track = Track("QA segments", listOf(
            TrackSegment(listOf(TrackPoint(0.0, 179.0), TrackPoint(1.0, 179.9))),
            TrackSegment(listOf(TrackPoint(2.0, -179.9), TrackPoint(3.0, -179.0))),
        ))
        val extent = trackExtent(track)!!
        assertEquals(2.0, extent.east - extent.west, 0.0000001)
        assertEquals(3.0, extent.north, 0.0)
        assertEquals(0.0, extent.south, 0.0)
    }

    @Test fun aWorldwideCircuitDoesNotChooseASeamThroughOneOfItsLegs() {
        val extent = trackExtent(track(-170.0, -60.0, 60.0, 170.0, -170.0))!!
        assertEquals(360.0, extent.east - extent.west, 0.0000001)
    }
}

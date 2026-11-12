package com.gpxnavigator.ui.map

import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.LineString

class TrackOverlayTest {
    @Test fun separateSegmentsProduceSeparateLinesAndCompleteBounds() {
        val track = Track(null, listOf(
            TrackSegment(listOf(TrackPoint(43.0, 11.0), TrackPoint(44.0, 12.0))),
            TrackSegment(listOf(TrackPoint(45.0, 13.0), TrackPoint(46.0, 14.0))),
        ))
        val lines = lineFeatures(track).features()!!
        assertEquals(2, lines.size)
        assertEquals(2, (lines[0].geometry() as LineString).coordinates().size)
        assertEquals(45.0, (lines[1].geometry() as LineString).coordinates()[0].latitude(), 0.0)
        assertEquals(TrackExtent(46.0, 14.0, 43.0, 11.0), trackExtent(track))
    }

    @Test fun onePointSegmentBecomesVisiblePoint() {
        val track = Track(null, listOf(TrackSegment(listOf(TrackPoint(43.0, 11.0)))))
        assertTrue(lineFeatures(track).features()!!.isEmpty())
        assertEquals(1, singlePointFeatures(track).features()!!.size)
        assertTrue(trackExtent(track)!!.isSingleLocation)
    }

    @Test fun emptySegmentsDoNotAddGeometry() {
        val track = Track(null, listOf(TrackSegment(emptyList()),
            TrackSegment(listOf(TrackPoint(43.0, 11.0), TrackPoint(44.0, 12.0)))))
        assertEquals(1, lineFeatures(track).features()!!.size)
        assertFalse(trackExtent(track)!!.isSingleLocation)
    }
}

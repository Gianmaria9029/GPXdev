package com.gpxnavigator.ui.map

import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import com.gpxnavigator.navigation.EARTH_RADIUS_METERS
import com.gpxnavigator.navigation.OffTrackStatus
import com.gpxnavigator.navigation.TrackMatchStatus
import com.gpxnavigator.navigation.TrackProgress
import com.gpxnavigator.navigation.TrackTravelDirection
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.geojson.LineString

class TrackProgressOverlayTest {
    private fun point(x: Double, y: Double = 0.0) = TrackPoint(Math.toDegrees(y / EARTH_RADIUS_METERS),
        Math.toDegrees(x / EARTH_RADIUS_METERS))
    private fun track(vararg points: TrackPoint) = Track(null, listOf(TrackSegment(points.toList())))
    private fun valid(distance: Double, segment: Int = 0) = TrackProgress(1000.0, distance,
        1000 - distance, 0.0, segment)

    @Test fun confirmedProgressCanRetreatAndUnreliableStatesFreezeLastPosition() {
        val first = confirmedTrackOverlayPosition(valid(200.0), OffTrackStatus.ON_TRACK, true, null)
        assertEquals(TrackOverlayPosition(200.0, 0), first)
        for (status in listOf(TrackMatchStatus.UNAVAILABLE, TrackMatchStatus.AMBIGUOUS, TrackMatchStatus.SEARCHING)) {
            assertEquals(first, confirmedTrackOverlayPosition(valid(700.0).copy(matchStatus = status),
                OffTrackStatus.ON_TRACK, true, first))
        }
        for (status in listOf(OffTrackStatus.CHECKING, OffTrackStatus.OFF_TRACK, OffTrackStatus.UNAVAILABLE)) {
            assertEquals(first, confirmedTrackOverlayPosition(valid(700.0), status, true, first))
        }
        val retreat = valid(160.0).copy(travelDirection = TrackTravelDirection.BACKWARD)
        assertEquals(TrackOverlayPosition(160.0, 0),
            confirmedTrackOverlayPosition(retreat, OffTrackStatus.ON_TRACK, true, first))
        assertEquals(first, confirmedTrackOverlayPosition(null, OffTrackStatus.UNAVAILABLE, true, first))
        assertNull(confirmedTrackOverlayPosition(valid(200.0), OffTrackStatus.ON_TRACK, false, first))
    }

    @Test fun malformedDistancesNeverProduceANewCursor() {
        val previous = TrackOverlayPosition(100.0, 0)
        for (distance in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 1001.0)) {
            assertEquals(previous, confirmedTrackOverlayPosition(valid(distance), OffTrackStatus.ON_TRACK, true, previous))
        }
        assertEquals(previous, confirmedTrackOverlayPosition(valid(100.0, -1), OffTrackStatus.ON_TRACK, true, previous))
    }

    @Test fun interpolatesBoundaryAndKeepsUnpassedSuffixInOriginalDirection() {
        val prepared = prepareTrackProgress(track(point(0.0), point(100.0), point(200.0)))
        val cursor = TrackOverlayPosition(150.0, 0)
        val passed = prepared.partialPassedCoordinates(cursor)
        assertEquals(3, passed.size)
        assertEquals(point(150.0).longitude, passed.last().longitude(), 0.000001)
        val future = (prepared.partialFutureFeatures(cursor).features()!!.single().geometry() as LineString).coordinates()
        assertEquals(2, future.size)
        assertEquals(passed.last().longitude(), future.first().longitude(), 0.000001)
        assertEquals(point(200.0).longitude, future.last().longitude(), 0.000001)
        assertEquals(200.0, prepared.totalDistance, 0.01)
    }

    @Test fun chunksKeepCumulativeOffsetsAndNeverConnectGaps() {
        val prepared = prepareTrackProgress(Track(null, listOf(
            TrackSegment(listOf(point(0.0), point(100.0))),
            TrackSegment(emptyList()),
            TrackSegment(listOf(point(1000.0), point(1100.0))),
        )))
        assertEquals(2, prepared.chunks.size)
        assertEquals(200.0, prepared.totalDistance, 0.01)
        val first = prepared.chunks[0]
        val second = prepared.chunks[1]
        assertEquals(first.endDistance, second.startDistance, 0.01)
        assertEquals(2, second.segmentIndex)
        val future = (prepared.partialFutureFeatures(TrackOverlayPosition(150.0, 2))
            .features()!!.single().geometry() as LineString).coordinates()
        assertEquals(point(1050.0).longitude, future.first().longitude(), 0.000001)
        assertEquals(point(1100.0).longitude, future.last().longitude(), 0.000001)
    }

    @Test fun longDenseTrackOnlyRebuildsABoundedPartialChunk() {
        val prepared = prepareTrackProgress(track(*(0 until 50_000).map { point(it * 2.0) }.toTypedArray()))
        assertEquals(391, prepared.chunks.size)
        assertTrue(prepared.chunks.all { it.points.size <= 129 })
        val position = TrackOverlayPosition(50_001.0, 0)
        val partial = (prepared.partialFutureFeatures(position).features()!!.single().geometry() as LineString).coordinates()
        assertTrue(partial.size <= 129)
        assertEquals(point(50_001.0).longitude, partial.first().longitude(), 0.000001)
        assertTrue(prepared.features.features()!!.any { it.getNumberProperty(TRACK_CHUNK_START).toDouble() > position.distanceMeters })
    }

    @Test fun exactChunkBoundaryDoesNotCreateAnExtraPartialLine() {
        val prepared = prepareTrackProgress(track(*(0..256).map { point(it * 2.0) }.toTypedArray()))
        val boundary = prepared.chunks[0].endDistance
        assertTrue(prepared.partialFutureFeatures(TrackOverlayPosition(boundary, 0)).features()!!.isEmpty())
        assertEquals(boundary, prepared.chunks[1].startDistance, 0.0)
        assertTrue(prepared.partialFutureFeatures(null).features()!!.isEmpty())
    }

    @Test fun handlesIsolatedRepeatedAndInvalidPositions() {
        for (track in listOf(Track(null, emptyList()), track(point(0.0)), track(point(0.0), point(0.0)))) {
            val prepared = prepareTrackProgress(track)
            assertTrue(prepared.chunks.isEmpty())
            assertTrue(prepared.partialFutureFeatures(TrackOverlayPosition(0.0, 0)).features()!!.isEmpty())
        }
        val prepared = prepareTrackProgress(track(point(0.0), point(100.0)))
        for (cursor in listOf(TrackOverlayPosition(Double.NaN, 0), TrackOverlayPosition(-10.0, 0),
            TrackOverlayPosition(1000.0, 0), TrackOverlayPosition(50.0, 9))) {
            assertTrue(prepared.partialFutureFeatures(cursor).features()!!.isEmpty())
        }
    }

    @Test fun startFinishMarkersCombineOnlyForNearbyEndpointsAndIgnoreEmptySegments() {
        val open = trackMarkers(track(point(0.0), point(500.0))).features()!!
        assertEquals(listOf("start", "finish"), open.map { it.getStringProperty("marker") })
        val loop = trackMarkers(track(point(0.0), point(500.0), point(500.0, 500.0), point(0.0))).features()!!
        assertEquals("start_finish", loop.single().getStringProperty("marker"))
        assertEquals("start_finish", trackMarkers(track(point(0.0))).features()!!.single().getStringProperty("marker"))
        assertTrue(trackMarkers(Track(null, listOf(TrackSegment(emptyList())))).features()!!.isEmpty())
        val gapped = Track(null, listOf(TrackSegment(emptyList()), TrackSegment(listOf(point(0.0), point(100.0))),
            TrackSegment(emptyList()), TrackSegment(listOf(point(1000.0), point(1100.0))), TrackSegment(emptyList())))
        assertEquals(2, trackMarkers(gapped).features()!!.size)
    }
}

package com.gpxnavigator.ui.map

import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.navigation.OffTrackStatus
import com.gpxnavigator.navigation.TrackMatchStatus
import com.gpxnavigator.navigation.TrackProgress
import com.gpxnavigator.navigation.distanceMeters
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal const val TRACK_CHUNK_END = "end_distance"
internal const val TRACK_CHUNK_START = "start_distance"
private const val MAX_CHUNK_LEGS = 128

data class TrackOverlayPosition(val distanceMeters: Double, val segmentIndex: Int)

internal fun confirmedTrackOverlayPosition(progress: TrackProgress?, offTrack: OffTrackStatus,
    navigating: Boolean, previous: TrackOverlayPosition?): TrackOverlayPosition? {
    if (!navigating) return null
    val distance = progress?.travelledDistanceMeters
    val segment = progress?.matchedSegmentIndex
    if (offTrack != OffTrackStatus.ON_TRACK || progress?.matchStatus != TrackMatchStatus.MATCHED ||
        distance == null || !distance.isFinite() || distance < 0 ||
        !progress.totalDistanceMeters.isFinite() || distance > progress.totalDistanceMeters || segment == null || segment < 0)
        return previous
    return TrackOverlayPosition(distance, segment)
}

internal data class TrackProgressChunk(val points: List<Point>, val distances: DoubleArray, val segmentIndex: Int) {
    val startDistance: Double get() = distances.first()
    val endDistance: Double get() = distances.last()
}

internal class PreparedTrackProgress(val chunks: List<TrackProgressChunk>, val totalDistance: Double) {
    private val bySegment = chunks.groupBy { it.segmentIndex }
    val features: FeatureCollection = FeatureCollection.fromFeatures(chunks.map { chunk ->
        Feature.fromGeometry(LineString.fromLngLats(chunk.points)).apply {
            addNumberProperty(TRACK_CHUNK_END, chunk.endDistance)
            addNumberProperty(TRACK_CHUNK_START, chunk.startDistance)
        }
    })

    fun partialPassedCoordinates(position: TrackOverlayPosition?): List<Point> {
        if (position == null) return emptyList()
        if (!position.distanceMeters.isFinite() || position.distanceMeters !in 0.0..totalDistance) return emptyList()
        val segment = bySegment[position.segmentIndex] ?: return emptyList()
        val distance = position.distanceMeters
        var low = 0
        var high = segment.size
        while (low < high) {
            val middle = (low + high) / 2
            if (segment[middle].endDistance < distance) low = middle + 1 else high = middle
        }
        val chunk = segment.getOrNull(low) ?: return emptyList()
        if (distance <= chunk.startDistance || distance >= chunk.endDistance) return emptyList()
        val result = mutableListOf<Point>()
        for (index in chunk.points.indices) {
            if (chunk.distances[index] <= distance) {
                result += chunk.points[index]
            } else {
                val previous = chunk.points[index - 1]
                val next = chunk.points[index]
                val fraction = (distance - chunk.distances[index - 1]) /
                    (chunk.distances[index] - chunk.distances[index - 1])
                var longitudeDelta = next.longitude() - previous.longitude()
                if (longitudeDelta > 180) longitudeDelta -= 360
                if (longitudeDelta < -180) longitudeDelta += 360
                val longitude = (previous.longitude() + longitudeDelta * fraction + 540) % 360 - 180
                result += Point.fromLngLat(longitude,
                    previous.latitude() + (next.latitude() - previous.latitude()) * fraction)
                break
            }
        }
        return result
    }

    fun partialFutureFeatures(position: TrackOverlayPosition?): FeatureCollection {
        if (position == null || !position.distanceMeters.isFinite() || position.distanceMeters !in 0.0..totalDistance)
            return FeatureCollection.fromFeatures(emptyList())
        val segment = bySegment[position.segmentIndex] ?: return FeatureCollection.fromFeatures(emptyList())
        val distance = position.distanceMeters
        var low = 0
        var high = segment.size
        while (low < high) {
            val middle = (low + high) / 2
            if (segment[middle].endDistance < distance) low = middle + 1 else high = middle
        }
        val chunk = segment.getOrNull(low) ?: return FeatureCollection.fromFeatures(emptyList())
        if (distance <= chunk.startDistance || distance >= chunk.endDistance) return FeatureCollection.fromFeatures(emptyList())
        val prefix = partialPassedCoordinates(position)
        val nextIndex = chunk.distances.indexOfFirst { it > distance }
        val coordinates = listOf(prefix.last()) + chunk.points.subList(nextIndex, chunk.points.size)
        return FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(LineString.fromLngLats(coordinates))))
    }
}

internal fun prepareTrackProgress(track: Track): PreparedTrackProgress {
    val chunks = mutableListOf<TrackProgressChunk>()
    var distance = 0.0
    track.segments.forEachIndexed { segmentIndex, segment ->
        val coordinates = segment.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        val distances = DoubleArray(segment.points.size)
        segment.points.forEachIndexed { index, point ->
            if (index > 0) {
                val before = segment.points[index - 1]
                distance += distanceMeters(before.latitude, before.longitude, point.latitude, point.longitude)
            }
            distances[index] = distance
        }
        var start = 0
        while (start < coordinates.lastIndex) {
            val end = minOf(start + MAX_CHUNK_LEGS, coordinates.lastIndex)
            if (distances[end] > distances[start]) chunks += TrackProgressChunk(
                coordinates.subList(start, end + 1).toList(), distances.copyOfRange(start, end + 1), segmentIndex)
            start = end
        }
    }
    return PreparedTrackProgress(chunks, distance)
}

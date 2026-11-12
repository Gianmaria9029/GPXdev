package com.gpxnavigator.domain.model

import java.time.Instant

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timestamp: Instant? = null,
)

data class TrackSegment(val points: List<TrackPoint>)

data class Track(val name: String?, val segments: List<TrackSegment>)

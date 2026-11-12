package com.gpxnavigator.domain.model

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val elevationMeters: Double?,
    val elapsedRealtimeNanos: Long? = null,
    val bearingAccuracyDegrees: Float? = null,
    val timestampMillis: Long? = null,
)

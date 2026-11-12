package com.gpxnavigator.ui.map

import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.domain.model.MapOrientation
import com.gpxnavigator.navigation.MovementBearingEstimator
import com.gpxnavigator.navigation.bearingDifference
import com.gpxnavigator.navigation.normalizeBearing
import kotlin.math.abs

internal class CameraBearingPolicy(initialBearing: Double? = null) {
    private val movement = MovementBearingEstimator()
    private var heading = initialBearing?.takeIf { it.isFinite() }?.let(::normalizeBearing)
    private var previous: LocationPoint? = null

    fun bearing(orientation: MapOrientation, location: LocationPoint?): Double {
        if (location == null) {
            movement.reset()
            previous = null
        } else if (location != previous) {
            previous = location
            val measured = movement.update(location)
            if (measured != null) {
                val old = heading
                heading = if (old == null) measured else {
                    val delta = bearingDifference(old, measured)
                    // Ignore tiny fluctuations; follow the shortest rotation through north.
                    if (abs(delta) < 3) old else normalizeBearing(old + delta * 0.5)
                }
            }
        }
        return if (orientation == MapOrientation.NORTH_UP) 0.0 else heading ?: 0.0
    }
}

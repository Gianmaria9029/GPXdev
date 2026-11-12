package com.gpxnavigator.location

import com.gpxnavigator.domain.model.LocationPoint
import kotlinx.coroutines.flow.StateFlow

enum class LocationStatus { STOPPED, WAITING, ACTIVE, UNAVAILABLE, DISABLED, DENIED, ERROR }

data class LocationState(
    val status: LocationStatus = LocationStatus.STOPPED,
    val point: LocationPoint? = null,
    val precise: Boolean = false,
)

interface LocationProvider {
    val state: StateFlow<LocationState>
    fun hasPermission(): Boolean
    fun hasPrecisePermission(): Boolean = hasPermission()
    fun start()
    fun stop()
}

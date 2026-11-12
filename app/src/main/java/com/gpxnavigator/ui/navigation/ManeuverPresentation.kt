package com.gpxnavigator.ui.navigation

import com.gpxnavigator.navigation.GuidanceStatus
import com.gpxnavigator.navigation.Maneuver
import com.gpxnavigator.navigation.TrackGuidance

internal fun maneuverApproachFraction(guidance: TrackGuidance): Float? {
    if (guidance.status != GuidanceStatus.ACTIVE || guidance.maneuver == null ||
        guidance.maneuver == Maneuver.STRAIGHT) return null
    val distance = guidance.distanceMeters?.takeIf { it.isFinite() } ?: return null
    val reference = guidance.announcementDistanceMeters?.takeIf { it.isFinite() && it > 0 } ?: return null
    return (1.0 - distance / reference).coerceIn(0.0, 1.0).toFloat()
}

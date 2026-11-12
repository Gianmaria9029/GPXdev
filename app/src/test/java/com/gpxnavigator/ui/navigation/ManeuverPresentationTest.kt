package com.gpxnavigator.ui.navigation

import com.gpxnavigator.navigation.GuidanceStatus
import com.gpxnavigator.navigation.Maneuver
import com.gpxnavigator.navigation.TrackGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManeuverPresentationTest {
    private fun turn(distance: Double?, reference: Double? = 200.0) =
        TrackGuidance(GuidanceStatus.ACTIVE, Maneuver.RIGHT, distance, announcementDistanceMeters = reference)

    @Test fun approachUsesTheActualAnnouncementDistance() {
        for (reference in listOf(80.0, 167.0, 300.0, 350.0)) {
            assertEquals(0f, maneuverApproachFraction(turn(reference, reference))!!, 0f)
            assertEquals(0.5f, maneuverApproachFraction(turn(reference / 2, reference))!!, 0f)
            assertEquals(1f, maneuverApproachFraction(turn(0.0, reference))!!, 0f)
        }
    }

    @Test fun missingOrInvalidReferenceHidesTheBar() {
        for (reference in listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertNull(maneuverApproachFraction(turn(40.0, reference)))
    }

    @Test fun approachFillsAcrossTheAnnouncementDistance() {
        assertEquals(0f, maneuverApproachFraction(turn(200.0))!!, 0f)
        assertEquals(0.5f, maneuverApproachFraction(turn(100.0))!!, 0f)
        assertEquals(1f, maneuverApproachFraction(turn(0.0))!!, 0f)
    }

    @Test fun stickyAnnouncementsAndPassedTurnDistancesStayWithinTheBar() {
        assertEquals(0f, maneuverApproachFraction(turn(240.0))!!, 0f)
        assertEquals(1f, maneuverApproachFraction(turn(-5.0))!!, 0f)
    }

    @Test fun uncertainGuidanceAndStraightSectionsNeverShowAnApproachBar() {
        for (status in GuidanceStatus.entries.filter { it != GuidanceStatus.ACTIVE }) {
            assertNull(maneuverApproachFraction(turn(40.0).copy(status = status)))
        }
        assertNull(maneuverApproachFraction(turn(40.0).copy(maneuver = Maneuver.STRAIGHT)))
        assertNull(maneuverApproachFraction(turn(40.0).copy(maneuver = null)))
        for (distance in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertNull(maneuverApproachFraction(turn(distance)))
        }
    }
}

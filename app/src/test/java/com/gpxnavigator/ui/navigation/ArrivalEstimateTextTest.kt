package com.gpxnavigator.ui.navigation

import android.app.Application
import android.provider.Settings
import com.gpxnavigator.navigation.ArrivalEstimate
import com.gpxnavigator.navigation.ArrivalStatus
import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class ArrivalEstimateTextTest {
    private lateinit var originalZone: TimeZone
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }
    @After fun restoreZone() { TimeZone.setDefault(originalZone) }

    private fun estimate(seconds: Long, observed: String) = Instant.parse(observed).toEpochMilli().let {
        ArrivalEstimate(ArrivalStatus.READY, seconds, it + seconds * 1000, it, 10.0)
    }

    @Test fun compactTextShowsMinutesHoursAndArrivalClock() {
        assertEquals("24 min · Arrivo 12:24", arrivalEstimateText(context, estimate(1440, "2026-10-26T12:00:00Z")))
        assertEquals("1 h 05 min · Arrivo 13:05", arrivalEstimateText(context, estimate(3900, "2026-10-26T12:00:00Z")))
        assertEquals("<1 min · Arrivo 12:00", arrivalEstimateText(context, estimate(45, "2026-10-26T12:00:00Z")))
    }

    @Test fun midnightRolloverClearlyMarksNextDay() {
        assertEquals("20 min · Arrivo 00:10 (+1 g)",
            arrivalEstimateText(context, estimate(1200, "2026-10-26T23:50:00Z")))
    }

    @Test fun suspendedStatesDoNotDisplayOldArrivalOrDuration() {
        for (status in listOf(ArrivalStatus.PAUSED, ArrivalStatus.WAITING_FOR_GPS,
            ArrivalStatus.MATCHING_TRACK, ArrivalStatus.OFF_TRACK, ArrivalStatus.REVERSE_DIRECTION)) {
            val text = arrivalEstimateText(context, estimate(1440, "2026-10-26T12:00:00Z").copy(status = status))
            assertFalse(text.contains("12:24"))
            assertFalse(text.contains("24 min"))
        }
    }

    @Test fun clockTileShowsOnlyClockAndPreservesNextDayMarker() {
        assertEquals("12:24", arrivalClockText(context, estimate(1440, "2026-10-26T12:00:00Z")))
        assertEquals("00:10 (+1 g)", arrivalClockText(context, estimate(1200, "2026-10-26T23:50:00Z")))
    }

    @Test fun clockTileReplacesStaleClockWithTheCurrentStatus() {
        val ready = estimate(1440, "2026-10-26T12:00:00Z")
        for (status in ArrivalStatus.entries.filter { it != ArrivalStatus.READY }) {
            val text = arrivalClockText(context, ready.copy(status = status))
            assertFalse(text.contains("12:24"))
            assertFalse(text.contains("Arrivo:"))
        }
        assertEquals("Attesa GPS", arrivalClockText(context, ready.copy(status = ArrivalStatus.WAITING_FOR_GPS)))
        assertEquals("In pausa", arrivalClockText(context, ready.copy(status = ArrivalStatus.PAUSED)))
    }

    @Test fun incompleteReadyEstimateShowsCalculationInsteadOfInventingAnArrival() {
        assertEquals("Calcolo…", arrivalClockText(context, ArrivalEstimate(ArrivalStatus.READY)))
    }
}
